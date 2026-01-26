package com.shipmonk.testingday.dto;

import java.math.BigDecimal;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeRateValueDto that = (ExchangeRateValueDto) o;
        return Objects.equals(targetCurrency, that.targetCurrency) && Objects.equals(rate, that.rate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetCurrency, rate);
    }
}
