package org.taha.paymentgateway.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.taha.paymentgateway.persistence.entity.IdempotencyRecordEntity;
import org.taha.paymentgateway.persistence.repository.IdempotencyRecordRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Tests")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Nested
    @DisplayName("find() method tests")
    class FindTests {

        @Test
        @DisplayName("should return existing record when key exists")
        void shouldReturnExistingRecord_WhenKeyExists() {
            // given
            String idempotencyKey = "test-key-123";
            IdempotencyRecordEntity expectedRecord = IdempotencyRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .idempotencyKey(idempotencyKey)
                    .requestHash("hash123")
                    .responseBody("{\"status\":\"success\"}")
                    .createdAt(OffsetDateTime.now())
                    .build();
            
            when(repository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.of(expectedRecord));

            // when
            Optional<IdempotencyRecordEntity> result = idempotencyService.find(idempotencyKey);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(result.get().getRequestHash()).isEqualTo("hash123");
            verify(repository).findByIdempotencyKey(idempotencyKey);
        }

        @Test
        @DisplayName("should return empty when key does not exist")
        void shouldReturnEmpty_WhenKeyDoesNotExist() {
            // given
            String idempotencyKey = "non-existent-key";
            when(repository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.empty());

            // when
            Optional<IdempotencyRecordEntity> result = idempotencyService.find(idempotencyKey);

            // then
            assertThat(result).isEmpty();
            verify(repository).findByIdempotencyKey(idempotencyKey);
        }
    }

    @Nested
    @DisplayName("hash() method tests")
    class HashTests {

        @Test
        @DisplayName("should generate consistent SHA-256 hash for same input")
        void shouldGenerateConsistentHash_ForSameInput() {
            // given
            String input = "test-input-string";

            // when
            String hash1 = idempotencyService.hash(input);
            String hash2 = idempotencyService.hash(input);

            // then
            assertThat(hash1).isEqualTo(hash2);
            assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex characters
        }

        @Test
        @DisplayName("should generate different hashes for different inputs")
        void shouldGenerateDifferentHashes_ForDifferentInputs() {
            // given
            String input1 = "input-one";
            String input2 = "input-two";

            // when
            String hash1 = idempotencyService.hash(input1);
            String hash2 = idempotencyService.hash(input2);

            // then
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should generate valid hex string")
        void shouldGenerateValidHexString() {
            // given
            String input = "test-input";

            // when
            String hash = idempotencyService.hash(input);

            // then
            assertThat(hash).matches("^[0-9a-f]+$");
        }
    }

    @Nested
    @DisplayName("save() method tests")
    class SaveTests {

        @Test
        @DisplayName("should save new idempotency record with correct data")
        void shouldSaveNewRecord_WithCorrectData() {
            // given
            String key = "payment-key-123";
            String requestHash = "abc123hash";
            String responseBody = "{\"id\":\"uuid\",\"status\":\"success\"}";
            
            ArgumentCaptor<IdempotencyRecordEntity> captor = 
                    ArgumentCaptor.forClass(IdempotencyRecordEntity.class);

            // when
            idempotencyService.save(key, requestHash, responseBody);

            // then
            verify(repository).save(captor.capture());
            
            IdempotencyRecordEntity savedEntity = captor.getValue();
            assertThat(savedEntity.getId()).isNotNull();
            assertThat(savedEntity.getIdempotencyKey()).isEqualTo(key);
            assertThat(savedEntity.getRequestHash()).isEqualTo(requestHash);
            assertThat(savedEntity.getResponseBody()).isEqualTo(responseBody);
            assertThat(savedEntity.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should generate unique UUID for each save")
        void shouldGenerateUniqueUUID_ForEachSave() {
            // given
            ArgumentCaptor<IdempotencyRecordEntity> captor = 
                    ArgumentCaptor.forClass(IdempotencyRecordEntity.class);

            // when
            idempotencyService.save("key1", "hash1", "response1");
            idempotencyService.save("key2", "hash2", "response2");

            // then
            verify(repository, times(2)).save(captor.capture());
            
            var savedEntities = captor.getAllValues();
            assertThat(savedEntities.get(0).getId())
                    .isNotEqualTo(savedEntities.get(1).getId());
        }
    }
}
