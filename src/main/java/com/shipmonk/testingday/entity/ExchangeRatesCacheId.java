package com.shipmonk.testingday.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class ExchangeRatesCacheId implements Serializable {

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    public ExchangeRatesCacheId() {
    }

    public ExchangeRatesCacheId(LocalDate date, String baseCurrency) {
        this.date = date;
        this.baseCurrency = baseCurrency;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeRatesCacheId that = (ExchangeRatesCacheId) o;
        return Objects.equals(date, that.date) && Objects.equals(baseCurrency, that.baseCurrency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, baseCurrency);
    }
}
