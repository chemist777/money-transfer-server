package com.chemist.moneytransfer.core;

public class MoneyTransferException extends RuntimeException {
    private final int httpCode;

    public MoneyTransferException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    /**
     * We use this exception to control flow.
     * So we should prevent stack to be filled for performance reason.
     *
     * @return null
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return null;
    }

    public int getHttpCode() {
        return httpCode;
    }
}
