package org.taha.paymentgateway.provider.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.taha.paymentgateway.core.model.AttemptStatus;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter;
import org.taha.paymentgateway.provider.adapter.PaymentProviderAdapter.*;
import org.taha.paymentgateway.provider.mock.MockPaymentProviderAdapter.MockMode;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockPaymentProviderAdapter Tests")
class MockPaymentProviderAdapterTest {

    private MockPaymentProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MockPaymentProviderAdapter();
    }

    @Nested
    @DisplayName("providerName() tests")
    class ProviderNameTests {

        @Test
        @DisplayName("should return MOCK_PROVIDER as provider name")
        void shouldReturnMockProviderAsName() {
            // when
            String name = adapter.providerName();

            // then
            assertThat(name).isEqualTo("MOCK_PROVIDER");
        }
    }

    @Nested
    @DisplayName("setMode() and getMode() tests")
    class ModeTests {

        @Test
        @DisplayName("should start with SUCCESS mode by default")
        void shouldStartWithSuccessMode() {
            // then
            assertThat(adapter.getMode()).isEqualTo(MockMode.SUCCESS);
        }

        @Test
        @DisplayName("should change mode when setMode is called")
        void shouldChangeModeWhenSetModeCalled() {
            // when
            adapter.setMode(MockMode.FAIL);

            // then
            assertThat(adapter.getMode()).isEqualTo(MockMode.FAIL);
        }

        @Test
        @DisplayName("should support all mock modes")
        void shouldSupportAllMockModes() {
            for (MockMode mode : MockMode.values()) {
                // when
                adapter.setMode(mode);

                // then
                assertThat(adapter.getMode()).isEqualTo(mode);
            }
        }
    }

    @Nested
    @DisplayName("authorize() tests")
    class AuthorizeTests {

        private AuthorizeContext createContext() {
            return new AuthorizeContext(
                    UUID.randomUUID(),
                    new BigDecimal("100.00"),
                    "TRY",
                    "token123",
                    "415679",
                    "merchant-1"
            );
        }

        @Test
        @DisplayName("should return success result in SUCCESS mode")
        void shouldReturnSuccess_InSuccessMode() {
            // given
            adapter.setMode(MockMode.SUCCESS);
            AuthorizeContext context = createContext();

            // when
            ProviderResult result = adapter.authorize(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.SUCCESS);
            assertThat(result.providerReference()).startsWith("MOCK-AUTH-");
            assertThat(result.errorCode()).isNull();
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("should return failure result in FAIL mode")
        void shouldReturnFailure_InFailMode() {
            // given
            adapter.setMode(MockMode.FAIL);
            AuthorizeContext context = createContext();

            // when
            ProviderResult result = adapter.authorize(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.FAILURE);
            assertThat(result.providerReference()).isNull();
            assertThat(result.errorCode()).isEqualTo("MOCK_DECLINED");
            assertThat(result.errorMessage()).isEqualTo("Card declined by issuer");
        }

        @Test
        @DisplayName("should return timeout result in TIMEOUT mode")
        void shouldReturnTimeout_InTimeoutMode() {
            // given
            adapter.setMode(MockMode.TIMEOUT);
            AuthorizeContext context = createContext();

            // when
            ProviderResult result = adapter.authorize(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.TIMEOUT);
            assertThat(result.errorCode()).isEqualTo("TIMEOUT");
        }

        @Test
        @DisplayName("should return 3DS required result in REQUIRES_3DS mode")
        void shouldReturn3DSRequired_InRequires3DSMode() {
            // given
            adapter.setMode(MockMode.REQUIRES_3DS);
            AuthorizeContext context = createContext();

            // when
            ProviderResult result = adapter.authorize(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.REQUIRES_3DS);
            assertThat(result.requires3DS()).isTrue();
            assertThat(result.threeDSUrl()).startsWith("https://mock-3ds.example.com/verify");
        }
    }

    @Nested
    @DisplayName("capture() tests")
    class CaptureTests {

        private CaptureContext createContext() {
            return new CaptureContext(
                    UUID.randomUUID(),
                    "MOCK-AUTH-12345678",
                    new BigDecimal("100.00"),
                    "TRY"
            );
        }

        @Test
        @DisplayName("should return success result in SUCCESS mode")
        void shouldReturnSuccess_InSuccessMode() {
            // given
            adapter.setMode(MockMode.SUCCESS);
            CaptureContext context = createContext();

            // when
            ProviderResult result = adapter.capture(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.SUCCESS);
            assertThat(result.providerReference()).startsWith("MOCK-CAP-");
        }

        @Test
        @DisplayName("should return failure result in FAIL mode")
        void shouldReturnFailure_InFailMode() {
            // given
            adapter.setMode(MockMode.FAIL);
            CaptureContext context = createContext();

            // when
            ProviderResult result = adapter.capture(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.FAILURE);
            assertThat(result.errorCode()).isEqualTo("MOCK_CAPTURE_FAILED");
        }

        @Test
        @DisplayName("should return timeout result in TIMEOUT mode")
        void shouldReturnTimeout_InTimeoutMode() {
            // given
            adapter.setMode(MockMode.TIMEOUT);
            CaptureContext context = createContext();

            // when
            ProviderResult result = adapter.capture(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.TIMEOUT);
        }
    }

    @Nested
    @DisplayName("refund() tests")
    class RefundTests {

        private RefundContext createContext() {
            return new RefundContext(
                    UUID.randomUUID(),
                    "MOCK-CAP-12345678",
                    new BigDecimal("50.00"),
                    "TRY",
                    "Customer request"
            );
        }

        @Test
        @DisplayName("should return success result in SUCCESS mode")
        void shouldReturnSuccess_InSuccessMode() {
            // given
            adapter.setMode(MockMode.SUCCESS);
            RefundContext context = createContext();

            // when
            ProviderResult result = adapter.refund(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.SUCCESS);
            assertThat(result.providerReference()).startsWith("MOCK-REF-");
        }

        @Test
        @DisplayName("should return failure result in FAIL mode")
        void shouldReturnFailure_InFailMode() {
            // given
            adapter.setMode(MockMode.FAIL);
            RefundContext context = createContext();

            // when
            ProviderResult result = adapter.refund(context);

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.FAILURE);
            assertThat(result.errorCode()).isEqualTo("MOCK_REFUND_FAILED");
        }
    }

    @Nested
    @DisplayName("healthCheck() tests")
    class HealthCheckTests {

        @Test
        @DisplayName("should return HEALTHY in SUCCESS mode")
        void shouldReturnHealthy_InSuccessMode() {
            // given
            adapter.setMode(MockMode.SUCCESS);

            // when
            HealthStatus status = adapter.healthCheck();

            // then
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }

        @Test
        @DisplayName("should return DEGRADED in FAIL mode")
        void shouldReturnDegraded_InFailMode() {
            // given
            adapter.setMode(MockMode.FAIL);

            // when
            HealthStatus status = adapter.healthCheck();

            // then
            assertThat(status).isEqualTo(HealthStatus.DEGRADED);
        }

        @Test
        @DisplayName("should return UNHEALTHY in TIMEOUT mode")
        void shouldReturnUnhealthy_InTimeoutMode() {
            // given
            adapter.setMode(MockMode.TIMEOUT);

            // when
            HealthStatus status = adapter.healthCheck();

            // then
            assertThat(status).isEqualTo(HealthStatus.UNHEALTHY);
        }

        @Test
        @DisplayName("should return HEALTHY in RANDOM mode")
        void shouldReturnHealthy_InRandomMode() {
            // given
            adapter.setMode(MockMode.RANDOM);

            // when
            HealthStatus status = adapter.healthCheck();

            // then
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }

        @Test
        @DisplayName("should return HEALTHY in REQUIRES_3DS mode")
        void shouldReturnHealthy_InRequires3DSMode() {
            // given
            adapter.setMode(MockMode.REQUIRES_3DS);

            // when
            HealthStatus status = adapter.healthCheck();

            // then
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }
    }
}
