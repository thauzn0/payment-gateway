package org.taha.paymentgateway.threeds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.taha.paymentgateway.core.exception.PaymentException;
import org.taha.paymentgateway.core.exception.PaymentNotFoundException;
import org.taha.paymentgateway.core.model.PaymentStatus;
import org.taha.paymentgateway.persistence.entity.PaymentEntity;
import org.taha.paymentgateway.persistence.entity.ThreeDsSessionEntity;
import org.taha.paymentgateway.persistence.repository.PaymentRepository;
import org.taha.paymentgateway.persistence.repository.ThreeDsSessionRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeDsService {

    private static final String VALID_OTP = "111111";
    private static final int MAX_ATTEMPTS = 3;

    private final ThreeDsSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 3DS oturumu oluşturur.
     */
    @Transactional
    public ThreeDsSessionEntity createSession(UUID paymentId) {
        log.info("Creating 3DS session for payment: {}", paymentId);

        // Payment'ı bul
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // Mevcut session varsa onu döndür
        return sessionRepository.findByPaymentId(paymentId)
                .orElseGet(() -> {
                    ThreeDsSessionEntity session = ThreeDsSessionEntity.builder()
                            .id(UUID.randomUUID())
                            .paymentId(paymentId)
                            .otpCode(VALID_OTP)
                            .status("PENDING")
                            .attempts(0)
                            .expiresAt(OffsetDateTime.now().plusMinutes(5))
                            .build();

                    session = sessionRepository.save(session);

                    // Payment'ı güncelle
                    payment.setRequires3ds(true);
                    payment.setThreeDsSessionId(session.getId());
                    paymentRepository.save(payment);

                    log.info("3DS session created: {}", session.getId());
                    return session;
                });
    }

    /**
     * OTP kodunu doğrular.
     */
    @Transactional
    public ThreeDsVerifyResult verify(UUID paymentId, String otpCode) {
        log.info("Verifying 3DS OTP for payment: {}", paymentId);

        ThreeDsSessionEntity session = sessionRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentException("3DS_SESSION_NOT_FOUND", "3DS session not found"));

        // Oturum süresi kontrolü
        if (session.isExpired()) {
            session.setStatus("EXPIRED");
            sessionRepository.save(session);
            log.warn("3DS session expired for payment: {}", paymentId);
            return new ThreeDsVerifyResult(false, "OTP süresi doldu. Lütfen tekrar deneyin.", "EXPIRED");
        }

        // Zaten doğrulanmış mı?
        if (session.isVerified()) {
            return new ThreeDsVerifyResult(true, "Zaten doğrulanmış", "ALREADY_VERIFIED");
        }

        // Deneme sayısı kontrolü
        if (session.getAttempts() >= MAX_ATTEMPTS) {
            session.setStatus("BLOCKED");
            sessionRepository.save(session);
            log.warn("3DS max attempts reached for payment: {}", paymentId);
            return new ThreeDsVerifyResult(false, "Çok fazla hatalı deneme. İşlem iptal edildi.", "MAX_ATTEMPTS");
        }

        // OTP kontrolü
        session.setAttempts(session.getAttempts() + 1);

        if (VALID_OTP.equals(otpCode)) {
            session.setStatus("VERIFIED");
            session.setVerifiedAt(OffsetDateTime.now());
            sessionRepository.save(session);
            log.info("3DS OTP verified for payment: {}", paymentId);
            return new ThreeDsVerifyResult(true, "Doğrulama başarılı", "SUCCESS");
        } else {
            sessionRepository.save(session);
            int remaining = MAX_ATTEMPTS - session.getAttempts();
            log.warn("3DS OTP failed for payment: {}. Remaining attempts: {}", paymentId, remaining);
            return new ThreeDsVerifyResult(false, 
                    String.format("Hatalı kod. Kalan deneme: %d", remaining), 
                    "INVALID_OTP");
        }
    }

    /**
     * 3DS durumunu kontrol eder.
     */
    public boolean isVerified(UUID paymentId) {
        return sessionRepository.findByPaymentId(paymentId)
                .map(ThreeDsSessionEntity::isVerified)
                .orElse(false);
    }

    public record ThreeDsVerifyResult(
        boolean success,
        String message,
        String code
    ) {}
}
