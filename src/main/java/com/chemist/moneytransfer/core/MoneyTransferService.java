package com.chemist.moneytransfer.core;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation must be thread safe.
 */
public interface MoneyTransferService {
    /**
     * Transfers money from sender to recipient account.<br/>
     * All parameters are expected to be non null.<br/>
     * The method must be idempotent and non-blocking.
     *
     * @param sender         sender account ID
     * @param recipient      recipient account ID
     * @param amount         amount to transfer, greater then zero
     * @param idempotencyKey idempotency key
     * @return CompletableFuture which fails with {@link MoneyTransferException} if transfer fails
     */
    CompletableFuture<Void> transfer(String sender,
                                     String recipient,
                                     BigDecimal amount,
                                     String idempotencyKey);

    void shutdown() throws InterruptedException;
}
