-- Add exec_date column to exchange_rates_cache table
-- This column records the timestamp when the API call was executed
ALTER TABLE exchange_rates_cache
ADD COLUMN exec_date TIMESTAMP;

-- Add comment for documentation
COMMENT ON COLUMN exchange_rates_cache.exec_date IS 'Timestamp when the API call was executed to fetch the exchange rates';
