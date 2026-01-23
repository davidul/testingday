package com.shipmonk.testingday.external;

/**
 * DTO representing an error response from the Fixer API
 * <pre>
 * {
 *   "success": false,
 *   "error": {
 *     "code": 105,
 *     "type": "base_currency_access_restricted",
 *     "info": "Additional error information"
 *   }
 * }
 * </pre>
 */
public class FixerErrorResponse {

    private boolean success;
    private ErrorDetail error;

    public FixerErrorResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    /**
     * Nested class representing error details
     */
    public static class ErrorDetail {
        private int code;
        private String type;
        private String info;

        public ErrorDetail() {
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getInfo() {
            return info;
        }

        public void setInfo(String info) {
            this.info = info;
        }

        @Override
        public String toString() {
            return "ErrorDetail{" +
                    "code=" + code +
                    ", type='" + type + '\'' +
                    ", info='" + info + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "FixerErrorResponse{" +
                "success=" + success +
                ", error=" + error +
                '}';
    }
}
