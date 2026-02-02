package org.taha.paymentgateway.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.taha.paymentgateway.routing.BinLookupService.BinInfo;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BinLookupService Tests")
class BinLookupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BinLookupService binLookupService;

    @Nested
    @DisplayName("lookup() method tests")
    class LookupTests {

        @Test
        @DisplayName("should return BinInfo when valid BIN exists in database")
        void shouldReturnBinInfo_WhenValidBinExists() {
            // given
            String cardBin = "415679123456";
            BinInfo expectedBinInfo = new BinInfo(
                    "415679",
                    "Garanti BBVA",
                    "VISA",
                    "CREDIT",
                    "TR"
            );
            
            when(jdbcTemplate.query(
                    any(String.class),
                    any(RowMapper.class),
                    eq("415679")
            )).thenReturn(List.of(expectedBinInfo));

            // when
            Optional<BinInfo> result = binLookupService.lookup(cardBin);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().binPrefix()).isEqualTo("415679");
            assertThat(result.get().bankName()).isEqualTo("Garanti BBVA");
            assertThat(result.get().cardBrand()).isEqualTo("VISA");
            assertThat(result.get().cardType()).isEqualTo("CREDIT");
            assertThat(result.get().countryCode()).isEqualTo("TR");
        }

        @Test
        @DisplayName("should return empty when BIN not found in database")
        void shouldReturnEmpty_WhenBinNotFound() {
            // given
            String cardBin = "999999123456";
            
            when(jdbcTemplate.query(
                    any(String.class),
                    any(RowMapper.class),
                    eq("999999")
            )).thenReturn(Collections.emptyList());

            // when
            Optional<BinInfo> result = binLookupService.lookup(cardBin);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when cardBin is null")
        void shouldReturnEmpty_WhenCardBinIsNull() {
            // when
            Optional<BinInfo> result = binLookupService.lookup(null);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("should return empty when cardBin is too short")
        void shouldReturnEmpty_WhenCardBinTooShort() {
            // when
            Optional<BinInfo> result = binLookupService.lookup("12345");

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("should use first 6 digits of cardBin for lookup")
        void shouldUseFirst6Digits_ForLookup() {
            // given
            String cardBin = "415679999999999";
            
            when(jdbcTemplate.query(
                    any(String.class),
                    any(RowMapper.class),
                    eq("415679")
            )).thenReturn(Collections.emptyList());

            // when
            binLookupService.lookup(cardBin);

            // then
            verify(jdbcTemplate).query(
                    any(String.class),
                    any(RowMapper.class),
                    eq("415679")
            );
        }

        @Test
        @DisplayName("should return empty when database exception occurs")
        void shouldReturnEmpty_WhenDatabaseExceptionOccurs() {
            // given
            String cardBin = "415679123456";
            
            when(jdbcTemplate.query(
                    any(String.class),
                    any(RowMapper.class),
                    any(String.class)
            )).thenThrow(new RuntimeException("Database error"));

            // when
            Optional<BinInfo> result = binLookupService.lookup(cardBin);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("BinInfo record method tests")
    class BinInfoTests {

        @Test
        @DisplayName("isTurkishCard() should return true for TR country code")
        void isTurkishCard_ShouldReturnTrue_ForTRCountryCode() {
            // given
            BinInfo binInfo = new BinInfo("415679", "Garanti", "VISA", "CREDIT", "TR");

            // then
            assertThat(binInfo.isTurkishCard()).isTrue();
        }

        @Test
        @DisplayName("isTurkishCard() should return false for non-TR country code")
        void isTurkishCard_ShouldReturnFalse_ForNonTRCountryCode() {
            // given
            BinInfo binInfo = new BinInfo("400001", "Chase", "VISA", "CREDIT", "US");

            // then
            assertThat(binInfo.isTurkishCard()).isFalse();
        }

        @Test
        @DisplayName("isGarantiCard() should return true when bank name contains Garanti")
        void isGarantiCard_ShouldReturnTrue_WhenBankNameContainsGaranti() {
            // given
            BinInfo binInfo = new BinInfo("415679", "Garanti BBVA", "VISA", "CREDIT", "TR");

            // then
            assertThat(binInfo.isGarantiCard()).isTrue();
        }

        @Test
        @DisplayName("isGarantiCard() should return false for other banks")
        void isGarantiCard_ShouldReturnFalse_ForOtherBanks() {
            // given
            BinInfo binInfo = new BinInfo("415679", "Akbank", "VISA", "CREDIT", "TR");

            // then
            assertThat(binInfo.isGarantiCard()).isFalse();
        }

        @Test
        @DisplayName("isYapiKrediCard() should return true when bank name contains Yapı Kredi")
        void isYapiKrediCard_ShouldReturnTrue_WhenBankNameContainsYapiKredi() {
            // given
            BinInfo binInfo = new BinInfo("453214", "Yapı Kredi Bankası", "MASTERCARD", "CREDIT", "TR");

            // then
            assertThat(binInfo.isYapiKrediCard()).isTrue();
        }

        @Test
        @DisplayName("isIsBankCard() should return true when bank name contains İş Bankası")
        void isIsBankCard_ShouldReturnTrue_WhenBankNameContainsIsBankasi() {
            // given
            BinInfo binInfo = new BinInfo("489456", "Türkiye İş Bankası", "VISA", "DEBIT", "TR");

            // then
            assertThat(binInfo.isIsBankCard()).isTrue();
        }

        @Test
        @DisplayName("bank identifier methods should return false for null bankName")
        void bankIdentifierMethods_ShouldReturnFalse_ForNullBankName() {
            // given
            BinInfo binInfo = new BinInfo("415679", null, "VISA", "CREDIT", "TR");

            // then
            assertThat(binInfo.isGarantiCard()).isFalse();
            assertThat(binInfo.isYapiKrediCard()).isFalse();
            assertThat(binInfo.isIsBankCard()).isFalse();
        }
    }
}
