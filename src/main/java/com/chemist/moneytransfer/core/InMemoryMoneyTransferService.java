package com.chemist.moneytransfer.core;

import com.chemist.moneytransfer.server.Config;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class InMemoryMoneyTransferService implements MoneyTransferService {
    private static final Logger log = LoggerFactory.getLogger(InMemoryMoneyTransferService.class);

    private final Config config;

    //maps account ID to balance reference
    //we don't use ConcurrentHashMap because we aren't going to update map concurrently
    private final Map<String, AtomicReference<BigDecimal>> accounts;

    private final ExecutorService processingExecutor;

    //idempotency key to transaction result cache
    private final Cache<String, CompletableFuture<Void>> resultCache;

    public InMemoryMoneyTransferService(Config config, Map<String, BigDecimal> initialAccounts) {
        this.config = config;
        //wrap balances with AtomicReference and create simple HashMap
        //this map will not be modified later
        accounts = initialAccounts.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), new AtomicReference<>(e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        processingExecutor = Executors.newFixedThreadPool(config.processingThreads, threadFactoryWithPrefix("processing-"));

        resultCache = CacheBuilder.newBuilder()
                .expireAfterAccess(config.idempotencyKeyCacheLifetimeSec, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public CompletableFuture<Void> transfer(String sender,
                                            String recipient,
                                            BigDecimal amount,
                                            String idempotencyKey) {
        //validate parameters
        var senderBalanceRef = accounts.get(sender);
        if (senderBalanceRef == null) {
            return CompletableFuture.failedFuture(accountNotFoundException(sender));
        }

        var recipientBalanceRef = accounts.get(recipient);
        if (recipientBalanceRef == null) {
            return CompletableFuture.failedFuture(accountNotFoundException(recipient));
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > config.balanceMaxScale) {
            return CompletableFuture.failedFuture(new MoneyTransferException(400, "'amount' param has invalid value."));
        }

        //get cached future or do actual money transfer
        try {
            return resultCache.get(idempotencyKey, () -> {
                return CompletableFuture.runAsync(() -> transfer(senderBalanceRef, recipientBalanceRef, amount), processingExecutor);
            });
        } catch (ExecutionException e) {
            //it's impossible situation, but we should pass exception to the future
            return CompletableFuture.failedFuture(e);
        }
    }

    private void transfer(AtomicReference<BigDecimal> senderBalanceRef,
                          AtomicReference<BigDecimal> recipientBalanceRef,
                          BigDecimal amount) {
        //actual money transfer is done using CAS loop
        for (; ; ) {
            BigDecimal senderBalance = senderBalanceRef.get();
            if (senderBalance.compareTo(amount) < 0)
                throw new MoneyTransferException(500, "Sender doesn't have enough money.");
            BigDecimal recipientBalance = recipientBalanceRef.get();

            var newSenderBalance = senderBalance.subtract(amount);
            var newRecipientBalance = recipientBalance.add(amount);

            if (senderBalanceRef.compareAndSet(senderBalance, newSenderBalance)) {
                if (recipientBalanceRef.compareAndSet(recipientBalance, newRecipientBalance)) {
                    //successful transfer
                    break;
                } else {
                    //rollback senderBalance and try again
                    returnTakenAmountBackToBalance(senderBalanceRef, amount);
                }
            }
        }
    }

    /**
     * Returns taken amount back to account balance.<br/>
     * Ignores transaction timeout.
     *
     * @param accountBalanceRef target account balance
     * @param amount            returning amount
     */
    private void returnTakenAmountBackToBalance(AtomicReference<BigDecimal> accountBalanceRef, BigDecimal amount) {
        for (; ; ) {
            BigDecimal balance = accountBalanceRef.get();
            BigDecimal newBalance = balance.add(amount);
            if (accountBalanceRef.compareAndSet(balance, newBalance)) break;
        }
    }

    //for testing only
    public BigDecimal balance(String accountId) {
        return accounts.get(accountId).get();
    }

    @Override
    public void shutdown() throws InterruptedException {
        processingExecutor.shutdown();
        while (!processingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
            log.info("Waiting for transfers completion");
        }
    }

    private static MoneyTransferException accountNotFoundException(String accountId) {
        return new MoneyTransferException(400, "Account '" + accountId + "' not found.");
    }

    private static ThreadFactory threadFactoryWithPrefix(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger num = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(prefix + num.getAndIncrement());
                return thread;
            }
        };
    }
}
