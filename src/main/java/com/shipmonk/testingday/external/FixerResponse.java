package com.shipmonk.testingday.external;


import java.util.Map;

/**
    DTO representing the response from the Fixer API
    <pre>
    {
      "success": true,
      "historical": true,
      "date": "2013-12-24",
      "timestamp": 1387929599,
      "base": "GBP",
      "rates": {
        "USD": 1.636492,
        "EUR": 1.196476,
        "CAD": 1.739516
     }
    }
    </pre>
 */
public class FixerResponse {

    private boolean success;
    private boolean historical;
    private String date;
    private Long timestamp;
    private String base;
    private Map<String, Double> rates;

    public FixerResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isHistorical() {
        return historical;
    }

    public void setHistorical(boolean historical) {
        this.historical = historical;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public Map<String, Double> getRates() {
        return rates;
    }

    public void setRates(Map<String, Double> rates) {
        this.rates = rates;
    }

    @Override
    public String toString() {
        return "FixerResponse{" +
            "success=" + success +
            ", historical=" + historical +
            ", date='" + date + '\'' +
            ", timestamp=" + timestamp +
            ", base='" + base + '\'' +
            ", rates=" + rates +
            '}';
    }
}
