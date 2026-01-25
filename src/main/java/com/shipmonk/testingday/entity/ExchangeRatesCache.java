package com.shipmonk.testingday.entity;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "exchange_rates_cache")
public class ExchangeRatesCache {

    @EmbeddedId
    private ExchangeRatesCacheId id;

    @Column(name = "exec_date")
    private LocalDateTime execDate;

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

    public LocalDateTime getExecDate() {
        return execDate;
    }

    public void setExecDate(LocalDateTime execDate) {
        this.execDate = execDate;
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
