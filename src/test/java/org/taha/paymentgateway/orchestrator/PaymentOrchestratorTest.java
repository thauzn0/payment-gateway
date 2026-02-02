package org.taha.paymentgateway.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.taha.paymentgateway.api.dto.request.AuthorizePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CapturePaymentRequest;
import org.taha.paymentgateway.api.dto.request.CreatePaymentRequest;
import org.taha.paymentgateway.api.dto.request.RefundPaymentRequest;
import org.taha.paymentgateway.api.dto.response.PaymentResponse;
import org.taha.paymentgateway.core.exception.IdempotencyConflictException;
import org.taha.paymentgateway.core.exception.InvalidPaymentStateException;
import org.taha.paymentgateway.core.exception.PaymentNotFoundException;
import org.taha.paymentgateway.core.exception.ProviderException;
import org.taha.paymentgateway.core.model.*;
import org.taha.paymentgateway.idempotency.IdempotencyService;
import org.taha.paymentgateway.persistence.entity.*;
import org.taha.paymentgateway.persistence.repository.*;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter.*;
import org.taha.paymentgateway.routing.RoutingEngine;
import org.taha.paymentgateway.routing.RoutingEngine.RoutingContext;
import org.taha.paymentgateway.routing.RoutingEngine.RoutingResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentOrchestrator Tests")
class PaymentOrchestratorTest {

    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentAttemptRepository attemptRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private OutboxEventRepository outboxEventRepository;
    
    @Mock
    private IdempotencyService idempotencyService;
    
    @Mock
    private RoutingEngine routingEngine;
    
    @Mock
    private PaymentProviderAdapter providerAdapter;

    private PaymentOrchestrator orchestrator;
    private ObjectMapper objectMapper;
    private Map<String, PaymentProviderAdapter> providerAdapters;

    private static final String MERCHANT_ID = "merchant-123";
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        providerAdapters = new HashMap<>();
        providerAdapters.put("MOCK_PROVIDER", providerAdapter);
        
        orchestrator = new PaymentOrchestrator(
                paymentRepository,
                attemptRepository,
                transactionRepository,
                outboxEventRepository,
                idempotencyService,
                routingEngine,
                providerAdapters,
                objectMapper
        );
    }

    @Nested
    @DisplayName("createPayment() tests")
    class CreatePaymentTests {

        @Test
        @DisplayName("should create payment successfully")
        void shouldCreatePayment_Successfully() {
            // given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .orderId("order-123")
                    .customerEmail("test@example.com")
                    .build();

            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> {
                PaymentEntity entity = inv.getArgument(0);
                entity.setCreatedAt(OffsetDateTime.now());
                entity.setUpdatedAt(OffsetDateTime.now());
                return entity;
            });

            // when
            PaymentResponse response = orchestrator.createPayment(MERCHANT_ID, request, null);

            // then
            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.merchantId()).isEqualTo(MERCHANT_ID);
            assertThat(response.amount()).isEqualTo(new BigDecimal("100.00"));
            assertThat(response.currency()).isEqualTo("TRY");
            assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
            
            verify(paymentRepository).save(any(PaymentEntity.class));
            verify(outboxEventRepository).save(any(OutboxEventEntity.class));
        }

        @Test
        @DisplayName("should mask customer email")
        void shouldMaskCustomerEmail() {
            // given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .customerEmail("longtest@example.com")
                    .build();

            ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> {
                PaymentEntity entity = inv.getArgument(0);
                entity.setCreatedAt(OffsetDateTime.now());
                entity.setUpdatedAt(OffsetDateTime.now());
                return entity;
            });

            // when
            orchestrator.createPayment(MERCHANT_ID, request, null);

            // then
            verify(paymentRepository).save(captor.capture());
            PaymentEntity saved = captor.getValue();
            assertThat(saved.getCustomerEmail()).contains("***");
            assertThat(saved.getCustomerEmail()).doesNotStartWith("longtest");
        }

        @Test
        @DisplayName("should return cached response for duplicate idempotency key")
        void shouldReturnCachedResponse_ForDuplicateIdempotencyKey() throws Exception {
            // given
            String idempotencyKey = "idem-key-123";
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .build();

            PaymentResponse cachedResponse = PaymentResponse.builder()
                    .id(PAYMENT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(PaymentStatus.CREATED)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            String cachedJson = objectMapper.writeValueAsString(cachedResponse);
            String requestHash = "hash123";

            IdempotencyRecordEntity record = IdempotencyRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .responseBody(cachedJson)
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(idempotencyService.find(idempotencyKey)).thenReturn(Optional.of(record));
            when(idempotencyService.hash(anyString())).thenReturn(requestHash);

            // when
            PaymentResponse response = orchestrator.createPayment(MERCHANT_ID, request, idempotencyKey);

            // then
            assertThat(response.id()).isEqualTo(PAYMENT_ID);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IdempotencyConflictException for different request with same key")
        void shouldThrowIdempotencyConflict_ForDifferentRequest() {
            // given
            String idempotencyKey = "idem-key-123";
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .build();

            IdempotencyRecordEntity record = IdempotencyRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .idempotencyKey(idempotencyKey)
                    .requestHash("original-hash")
                    .responseBody("{}")
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(idempotencyService.find(idempotencyKey)).thenReturn(Optional.of(record));
            when(idempotencyService.hash(anyString())).thenReturn("different-hash");

            // when/then
            assertThatThrownBy(() -> orchestrator.createPayment(MERCHANT_ID, request, idempotencyKey))
                    .isInstanceOf(IdempotencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("authorize() tests")
    class AuthorizeTests {

        private PaymentEntity createPayment(PaymentStatus status) {
            return PaymentEntity.builder()
                    .id(PAYMENT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(status)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("should authorize payment successfully")
        void shouldAuthorizePayment_Successfully() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.CREATED);
            AuthorizePaymentRequest request = AuthorizePaymentRequest.builder()
                    .cardToken("token-123")
                    .cardBin("415679")
                    .build();

            RoutingResult routingResult = new RoutingResult(
                    providerAdapter, "MOCK_PROVIDER", new BigDecimal("1.50"), "Test routing", null
            );

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(routingEngine.selectProvider(any(RoutingContext.class))).thenReturn(routingResult);
            when(providerAdapter.authorize(any(AuthorizeContext.class)))
                    .thenReturn(ProviderResult.success("AUTH-REF-123"));
            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            PaymentResponse response = orchestrator.authorize(PAYMENT_ID, MERCHANT_ID, request, null);

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            assertThat(response.providerReference()).isEqualTo("AUTH-REF-123");
            
            verify(attemptRepository).save(any(PaymentAttemptEntity.class));
            verify(outboxEventRepository).save(any(OutboxEventEntity.class));
        }

        @Test
        @DisplayName("should throw InvalidPaymentStateException for non-CREATED payment")
        void shouldThrowInvalidPaymentState_ForNonCreatedPayment() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.AUTHORIZED);
            AuthorizePaymentRequest request = AuthorizePaymentRequest.builder()
                    .cardToken("token-123")
                    .cardBin("415679")
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

            // when/then
            assertThatThrownBy(() -> orchestrator.authorize(PAYMENT_ID, MERCHANT_ID, request, null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }

        @Test
        @DisplayName("should throw PaymentNotFoundException for wrong merchant")
        void shouldThrowPaymentNotFound_ForWrongMerchant() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.CREATED);
            AuthorizePaymentRequest request = AuthorizePaymentRequest.builder()
                    .cardToken("token-123")
                    .cardBin("415679")
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

            // when/then
            assertThatThrownBy(() -> orchestrator.authorize(PAYMENT_ID, "wrong-merchant", request, null))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ProviderException on authorization failure")
        void shouldThrowProviderException_OnAuthorizationFailure() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.CREATED);
            AuthorizePaymentRequest request = AuthorizePaymentRequest.builder()
                    .cardToken("token-123")
                    .cardBin("415679")
                    .build();

            RoutingResult routingResult = new RoutingResult(
                    providerAdapter, "MOCK_PROVIDER", new BigDecimal("1.50"), "Test routing", null
            );

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(routingEngine.selectProvider(any(RoutingContext.class))).thenReturn(routingResult);
            when(providerAdapter.authorize(any(AuthorizeContext.class)))
                    .thenReturn(ProviderResult.failure("DECLINED", "Card declined"));
            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // when/then
            assertThatThrownBy(() -> orchestrator.authorize(PAYMENT_ID, MERCHANT_ID, request, null))
                    .isInstanceOf(ProviderException.class);
        }
    }

    @Nested
    @DisplayName("capture() tests")
    class CaptureTests {

        private PaymentEntity createPayment(PaymentStatus status) {
            return PaymentEntity.builder()
                    .id(PAYMENT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(status)
                    .providerReference("AUTH-REF-123")
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("should capture payment successfully")
        void shouldCapturePayment_Successfully() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.AUTHORIZED);
            CapturePaymentRequest request = CapturePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            PaymentAttemptEntity authorizeAttempt = PaymentAttemptEntity.builder()
                    .id(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .provider("MOCK_PROVIDER")
                    .operation(OperationType.AUTHORIZE)
                    .status(AttemptStatus.SUCCESS)
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(attemptRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(List.of(authorizeAttempt));
            when(providerAdapter.capture(any(CaptureContext.class)))
                    .thenReturn(ProviderResult.success("CAP-REF-456"));
            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            PaymentResponse response = orchestrator.capture(PAYMENT_ID, MERCHANT_ID, request, null);

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.CAPTURED);
            verify(transactionRepository).save(any(TransactionEntity.class));
        }

        @Test
        @DisplayName("should throw InvalidPaymentStateException for non-AUTHORIZED payment")
        void shouldThrowInvalidPaymentState_ForNonAuthorizedPayment() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.CREATED);
            CapturePaymentRequest request = CapturePaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

            // when/then
            assertThatThrownBy(() -> orchestrator.capture(PAYMENT_ID, MERCHANT_ID, request, null))
                    .isInstanceOf(InvalidPaymentStateException.class);
        }
    }

    @Nested
    @DisplayName("refund() tests")
    class RefundTests {

        private PaymentEntity createPayment(PaymentStatus status) {
            return PaymentEntity.builder()
                    .id(PAYMENT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(status)
                    .providerReference("CAP-REF-456")
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("should refund full payment successfully")
        void shouldRefundFullPayment_Successfully() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.CAPTURED);
            RefundPaymentRequest request = RefundPaymentRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .reason("Customer request")
                    .build();

            PaymentAttemptEntity authorizeAttempt = PaymentAttemptEntity.builder()
                    .id(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .provider("MOCK_PROVIDER")
                    .operation(OperationType.AUTHORIZE)
                    .status(AttemptStatus.SUCCESS)
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(attemptRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(List.of(authorizeAttempt));
            when(transactionRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(Collections.emptyList());
            when(providerAdapter.refund(any(RefundContext.class)))
                    .thenReturn(ProviderResult.success("REF-789"));
            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            PaymentResponse response = orchestrator.refund(PAYMENT_ID, MERCHANT_ID, request, null);

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
            verify(transactionRepository).save(any(TransactionEntity.class));
        }

        @Test
        @DisplayName("should mark as partially refunded for partial refund")
        void shouldMarkAsPartiallyRefunded_ForPartialRefund() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.CAPTURED);
            RefundPaymentRequest request = RefundPaymentRequest.builder()
                    .amount(new BigDecimal("50.00"))
                    .reason("Partial refund")
                    .build();

            PaymentAttemptEntity authorizeAttempt = PaymentAttemptEntity.builder()
                    .id(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .provider("MOCK_PROVIDER")
                    .operation(OperationType.AUTHORIZE)
                    .status(AttemptStatus.SUCCESS)
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(attemptRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(List.of(authorizeAttempt));
            when(transactionRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(Collections.emptyList());
            when(providerAdapter.refund(any(RefundContext.class)))
                    .thenReturn(ProviderResult.success("REF-789"));
            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            PaymentResponse response = orchestrator.refund(PAYMENT_ID, MERCHANT_ID, request, null);

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        }

        @Test
        @DisplayName("should allow refund for partially refunded payment")
        void shouldAllowRefund_ForPartiallyRefundedPayment() {
            // given
            PaymentEntity payment = createPayment(PaymentStatus.PARTIALLY_REFUNDED);
            RefundPaymentRequest request = RefundPaymentRequest.builder()
                    .amount(new BigDecimal("50.00"))
                    .build();

            PaymentAttemptEntity authorizeAttempt = PaymentAttemptEntity.builder()
                    .id(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .provider("MOCK_PROVIDER")
                    .operation(OperationType.AUTHORIZE)
                    .status(AttemptStatus.SUCCESS)
                    .createdAt(OffsetDateTime.now())
                    .build();

            TransactionEntity previousRefund = TransactionEntity.builder()
                    .id(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .type(OperationType.REFUND)
                    .amount(new BigDecimal("50.00"))
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(attemptRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(List.of(authorizeAttempt));
            when(transactionRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(List.of(previousRefund));
            when(providerAdapter.refund(any(RefundContext.class)))
                    .thenReturn(ProviderResult.success("REF-789"));
            when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            PaymentResponse response = orchestrator.refund(PAYMENT_ID, MERCHANT_ID, request, null);

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        }
    }

    @Nested
    @DisplayName("getPayment() tests")
    class GetPaymentTests {

        @Test
        @DisplayName("should return payment for correct merchant")
        void shouldReturnPayment_ForCorrectMerchant() {
            // given
            PaymentEntity payment = PaymentEntity.builder()
                    .id(PAYMENT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(PaymentStatus.CREATED)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

            // when
            PaymentResponse response = orchestrator.getPayment(PAYMENT_ID, MERCHANT_ID);

            // then
            assertThat(response.id()).isEqualTo(PAYMENT_ID);
            assertThat(response.merchantId()).isEqualTo(MERCHANT_ID);
        }

        @Test
        @DisplayName("should throw PaymentNotFoundException for non-existent payment")
        void shouldThrowPaymentNotFound_ForNonExistentPayment() {
            // given
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> orchestrator.getPayment(PAYMENT_ID, MERCHANT_ID))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getAttempts() tests")
    class GetAttemptsTests {

        @Test
        @DisplayName("should return attempts for payment")
        void shouldReturnAttempts_ForPayment() {
            // given
            PaymentEntity payment = PaymentEntity.builder()
                    .id(PAYMENT_ID)
                    .merchantId(MERCHANT_ID)
                    .amount(new BigDecimal("100.00"))
                    .currency("TRY")
                    .status(PaymentStatus.AUTHORIZED)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                    .id(UUID.randomUUID())
                    .paymentId(PAYMENT_ID)
                    .provider("MOCK_PROVIDER")
                    .operation(OperationType.AUTHORIZE)
                    .status(AttemptStatus.SUCCESS)
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(attemptRepository.findByPaymentIdOrderByCreatedAtDesc(PAYMENT_ID))
                    .thenReturn(List.of(attempt));

            // when
            List<PaymentAttemptEntity> attempts = orchestrator.getAttempts(PAYMENT_ID, MERCHANT_ID);

            // then
            assertThat(attempts).hasSize(1);
            assertThat(attempts.get(0).getProvider()).isEqualTo("MOCK_PROVIDER");
        }
    }
}
