package org.taha.paymentgateway.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.taha.paymentgateway.persistence.entity.IdempotencyRecordEntity;
import org.taha.paymentgateway.persistence.repository.IdempotencyRecordRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    public Optional<IdempotencyRecordEntity> find(String key) {
        return repository.findByIdempotencyKey(key);
    }

    public String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void save(String key, String requestHash, String responseBody) {
        repository.save(
                IdempotencyRecordEntity.builder()
                        .id(UUID.randomUUID())
                        .idempotencyKey(key)
                        .requestHash(requestHash)
                        .responseBody(responseBody)
                        .createdAt(OffsetDateTime.now())
                        .build()
        );
    }
}