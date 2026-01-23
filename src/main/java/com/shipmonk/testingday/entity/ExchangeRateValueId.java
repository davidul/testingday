package com.shipmonk.testingday.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class ExchangeRateValueId implements Serializable {

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    public ExchangeRateValueId() {
    }

    public ExchangeRateValueId(LocalDate date, String baseCurrency, String targetCurrency) {
        this.date = date;
        this.baseCurrency = baseCurrency;
        this.targetCurrency = targetCurrency;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public void setTargetCurrency(String targetCurrency) {
        this.targetCurrency = targetCurrency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeRateValueId that = (ExchangeRateValueId) o;
        return Objects.equals(date, that.date) &&
                Objects.equals(baseCurrency, that.baseCurrency) &&
                Objects.equals(targetCurrency, that.targetCurrency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, baseCurrency, targetCurrency);
    }
}
