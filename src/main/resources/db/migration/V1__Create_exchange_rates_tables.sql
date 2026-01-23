-- Create exchange_rates_cache table
CREATE TABLE exchange_rates_cache (
    date DATE NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (date, base_currency)
);

-- Create exchange_rate_values table
CREATE TABLE exchange_rate_values (
    date DATE NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC NOT NULL,
    PRIMARY KEY (date, base_currency, target_currency),
    FOREIGN KEY (date, base_currency) REFERENCES exchange_rates_cache(date, base_currency) ON DELETE CASCADE
);
