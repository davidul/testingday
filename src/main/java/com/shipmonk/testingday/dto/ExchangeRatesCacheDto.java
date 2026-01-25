package com.shipmonk.testingday.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing cached exchange rates for a specific date and base currency
 * Contains the date, base currency, execution timestamp, and a list of exchange rate values
 */
public class ExchangeRatesCacheDto {

    private LocalDate date;
    private String baseCurrency;
    private LocalDateTime execDate;
    private List<ExchangeRateValueDto> rates;

    public ExchangeRatesCacheDto() {
        this.rates = new ArrayList<>();
    }

    public ExchangeRatesCacheDto(LocalDate date, String baseCurrency) {
        this.date = date;
        this.baseCurrency = baseCurrency;
        this.rates = new ArrayList<>();
    }

    public ExchangeRatesCacheDto(LocalDate date, String baseCurrency, List<ExchangeRateValueDto> rates) {
        this.date = date;
        this.baseCurrency = baseCurrency;
        this.rates = rates != null ? rates : new ArrayList<>();
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

    public LocalDateTime getExecDate() {
        return execDate;
    }

    public void setExecDate(LocalDateTime execDate) {
        this.execDate = execDate;
    }

    public List<ExchangeRateValueDto> getRates() {
        return rates;
    }

    public void setRates(List<ExchangeRateValueDto> rates) {
        this.rates = rates;
    }

    public void addRate(ExchangeRateValueDto rate) {
        if (this.rates == null) {
            this.rates = new ArrayList<>();
        }
        this.rates.add(rate);
    }

    public void addRate(String targetCurrency, BigDecimal rate) {
        addRate(new ExchangeRateValueDto(targetCurrency, rate));
    }
}
