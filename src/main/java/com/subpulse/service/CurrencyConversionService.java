package com.subpulse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for real-time and static exchange rate currency conversion.
 * Base currency is USD (1.0).
 */
@Slf4j
@Service
public class CurrencyConversionService {

    // Exchange rates relative to 1.0 USD
    private static final Map<String, BigDecimal> RATES = new ConcurrentHashMap<>();

    static {
        RATES.put("USD", BigDecimal.valueOf(1.0));
        RATES.put("INR", BigDecimal.valueOf(86.50));  // 1 USD = 86.50 INR
        RATES.put("EUR", BigDecimal.valueOf(0.92));   // 1 USD = 0.92 EUR
        RATES.put("GBP", BigDecimal.valueOf(0.79));   // 1 USD = 0.79 GBP
        RATES.put("CAD", BigDecimal.valueOf(1.38));   // 1 USD = 1.38 CAD
        RATES.put("AUD", BigDecimal.valueOf(1.54));   // 1 USD = 1.54 AUD
        RATES.put("JPY", BigDecimal.valueOf(155.0));  // 1 USD = 155.0 JPY
        RATES.put("SGD", BigDecimal.valueOf(1.35));   // 1 USD = 1.35 SGD
    }

    /**
     * Converts an amount from one currency to another.
     *
     * @param amount       the original amount
     * @param fromCurrency source ISO currency code (e.g. "USD", "INR")
     * @param toCurrency   target ISO currency code (e.g. "INR", "USD")
     * @return converted amount scaled to 2 decimal places
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null || BigDecimal.ZERO.compareTo(amount) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String from = fromCurrency != null ? fromCurrency.trim().toUpperCase() : "USD";
        String to   = toCurrency != null ? toCurrency.trim().toUpperCase() : "USD";

        if (from.equals(to)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal fromRate = RATES.getOrDefault(from, BigDecimal.valueOf(1.0));
        BigDecimal toRate   = RATES.getOrDefault(to, BigDecimal.valueOf(1.0));

        // Step 1: Convert from source currency to USD base
        BigDecimal inUSD = amount.divide(fromRate, 6, RoundingMode.HALF_UP);

        // Step 2: Convert USD base to target currency
        BigDecimal result = inUSD.multiply(toRate).setScale(2, RoundingMode.HALF_UP);

        log.debug("Converted {} {} -> {} {} (fromRate: {}, toRate: {})",
                amount, from, result, to, fromRate, toRate);

        return result;
    }
}
