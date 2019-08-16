package com.chemist.moneytransfer.processing;

import com.chemist.moneytransfer.server.Config;
import com.chemist.moneytransfer.server.ServerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * There are some very simple tests.<br/>
 * The complete integrations tests are located in {@link ServerTest}.
 */
class InMemoryMoneyTransferServiceTest {
    private MoneyTransferService service;

    @BeforeEach
    void init() {
        Config config = new Config();
        service = new InMemoryMoneyTransferService(config,
                Map.of("a", BigDecimal.valueOf(10), "b", BigDecimal.valueOf(0)));
    }

    @AfterEach
    void shutdown() throws InterruptedException {
        service.shutdown();
    }

    @Test
    void successfulTransfer() {
        service.transfer("a", "b", BigDecimal.valueOf(10), "key1").join();
    }

    @Test
    void noMoreMoney() {
        var exception = assertThrows(CompletionException.class, () -> {
            service.transfer("a", "b", BigDecimal.valueOf(11), "key1").join();
        });
        var moneyTransferException = (MoneyTransferException) exception.getCause();
        assertEquals(500, moneyTransferException.getHttpCode());
    }

    @Test
    void zeroAmount() {
        var exception = assertThrows(CompletionException.class, () -> {
            service.transfer("a", "b", BigDecimal.valueOf(0), "key1").join();
        });
        var moneyTransferException = (MoneyTransferException) exception.getCause();
        assertEquals(400, moneyTransferException.getHttpCode());
    }

    @Test
    void negativeAmount() {
        var exception = assertThrows(CompletionException.class, () -> {
            service.transfer("a", "b", BigDecimal.valueOf(-1), "key1").join();
        });
        var moneyTransferException = (MoneyTransferException) exception.getCause();
        assertEquals(400, moneyTransferException.getHttpCode());
    }
}