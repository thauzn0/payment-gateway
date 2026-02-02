package org.taha.paymentgateway.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * BIN (Bank Identification Number) lookup servisi.
 * 
 * Kart numarasının ilk 6 hanesine bakarak:
 * - Hangi bankaya ait
 * - Kart markası (VISA, MC, TROY)
 * - Kart tipi (CREDIT, DEBIT)
 * bilgilerini döner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BinLookupService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * BIN numarasına göre banka bilgisini getirir.
     */
    public Optional<BinInfo> lookup(String cardBin) {
        if (cardBin == null || cardBin.length() < 6) {
            log.warn("Invalid BIN: {}", cardBin);
            return Optional.empty();
        }

        String bin = cardBin.substring(0, 6);
        
        try {
            return jdbcTemplate.query(
                "SELECT bin_prefix, bank_name, card_brand, card_type, country_code FROM bin_database WHERE bin_prefix = ?",
                (rs, rowNum) -> new BinInfo(
                    rs.getString("bin_prefix"),
                    rs.getString("bank_name"),
                    rs.getString("card_brand"),
                    rs.getString("card_type"),
                    rs.getString("country_code")
                ),
                bin
            ).stream().findFirst();
        } catch (Exception e) {
            log.error("BIN lookup failed for: {}", bin, e);
            return Optional.empty();
        }
    }

    /**
     * BIN bilgisi record.
     */
    public record BinInfo(
        String binPrefix,
        String bankName,
        String cardBrand,    // VISA, MASTERCARD, TROY
        String cardType,     // CREDIT, DEBIT, PREPAID
        String countryCode   // TR, US, etc.
    ) {
        public boolean isTurkishCard() {
            return "TR".equals(countryCode);
        }
        
        public boolean isGarantiCard() {
            return bankName != null && bankName.contains("Garanti");
        }
        
        public boolean isYapiKrediCard() {
            return bankName != null && bankName.contains("Yapı Kredi");
        }
        
        public boolean isIsBankCard() {
            return bankName != null && bankName.contains("İş Bankası");
        }
    }
}
