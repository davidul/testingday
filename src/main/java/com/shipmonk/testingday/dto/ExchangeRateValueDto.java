package com.shipmonk.testingday.dto;

import java.math.BigDecimal;

/**
 * DTO representing a single exchange rate value
 * Contains the target currency and its rate
 */
public class ExchangeRateValueDto {

    private String targetCurrency;
    private BigDecimal rate;

    public ExchangeRateValueDto() {
    }

    public ExchangeRateValueDto(String targetCurrency, BigDecimal rate) {
        this.targetCurrency = targetCurrency;
        this.rate = rate;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public void setTargetCurrency(String targetCurrency) {
        this.targetCurrency = targetCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }
}
