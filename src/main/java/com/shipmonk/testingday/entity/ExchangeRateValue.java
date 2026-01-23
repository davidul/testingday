package com.shipmonk.testingday.entity;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "exchange_rate_values")
public class ExchangeRateValue {

    @EmbeddedId
    private ExchangeRateValueId id;

    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "date", referencedColumnName = "date", insertable = false, updatable = false),
        @JoinColumn(name = "base_currency", referencedColumnName = "base_currency", insertable = false, updatable = false)
    })
    private ExchangeRatesCache exchangeRatesCache;

    public ExchangeRateValue() {
    }

    public ExchangeRateValue(ExchangeRateValueId id, BigDecimal rate) {
        this.id = id;
        this.rate = rate;
    }

    public ExchangeRateValueId getId() {
        return id;
    }

    public void setId(ExchangeRateValueId id) {
        this.id = id;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public ExchangeRatesCache getExchangeRatesCache() {
        return exchangeRatesCache;
    }

    public void setExchangeRatesCache(ExchangeRatesCache exchangeRatesCache) {
        this.exchangeRatesCache = exchangeRatesCache;
    }
}
