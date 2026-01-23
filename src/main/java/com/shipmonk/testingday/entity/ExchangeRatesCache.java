package com.shipmonk.testingday.entity;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "exchange_rates_cache")
public class ExchangeRatesCache {

    @EmbeddedId
    private ExchangeRatesCacheId id;

    @OneToMany(mappedBy = "exchangeRatesCache", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ExchangeRateValue> exchangeRateValues = new HashSet<>();

    public ExchangeRatesCache() {
    }

    public ExchangeRatesCache(ExchangeRatesCacheId id) {
        this.id = id;
    }

    public ExchangeRatesCacheId getId() {
        return id;
    }

    public void setId(ExchangeRatesCacheId id) {
        this.id = id;
    }

    public Set<ExchangeRateValue> getExchangeRateValues() {
        return exchangeRateValues;
    }

    public void setExchangeRateValues(Set<ExchangeRateValue> exchangeRateValues) {
        this.exchangeRateValues = exchangeRateValues;
    }

    public void addExchangeRateValue(ExchangeRateValue exchangeRateValue) {
        exchangeRateValues.add(exchangeRateValue);
        exchangeRateValue.setExchangeRatesCache(this);
    }

    public void removeExchangeRateValue(ExchangeRateValue exchangeRateValue) {
        exchangeRateValues.remove(exchangeRateValue);
        exchangeRateValue.setExchangeRatesCache(null);
    }
}
