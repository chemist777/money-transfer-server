package com.chemist.moneytransfer.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentStressServerTest extends AbstractServerTest {
    /**
     * Starts two threads.<br/>
     * First transfers random amount in range [0.01, 10] from 'a' to 'b', and second transfers from 'b' to 'a'.<br/>
     * Then checks the sum of remaining balances. It must be 10 (as initial).
     */
    @Test
    void randomTransfers() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(2);
        int transfersCount = 3000;
        CountDownLatch latch = new CountDownLatch(2);
        service.execute(() -> {
            Random random = new Random(46);
            for (int i = 0; i < transfersCount; i++) {
                transfer("a", "b", BigDecimal.valueOf((double) (1 + random.nextInt(1000)) / 100));
            }
            latch.countDown();
        });
        service.execute(() -> {
            Random random = new Random(64);
            for (int i = 0; i < transfersCount; i++) {
                transfer("b", "a", BigDecimal.valueOf((double) (1 + random.nextInt(1000)) / 100));
            }
            latch.countDown();
        });

        latch.await();

        BigDecimal aBalance = server.service.balance("a");
        BigDecimal bBalance = server.service.balance("b");

        assertEquals("10", aBalance.add(bBalance).stripTrailingZeros().toPlainString());

        service.shutdown();
    }

    void transfer(String sender, String recipient, BigDecimal amount) {
        client
                .headers(headers -> headers.set("Idempotency-Key", UUID.randomUUID().toString()))
                .post()
                .uri("/transfer?sender=" + sender + "&recipient=" + recipient + "&amount=" + amount.toPlainString())
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertTrue(resp.status() == HttpResponseStatus.OK || resp.status() == HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    return buf.asString();
                })
                .doOnNext(body -> {
                    assertTrue("Sender doesn't have enough money.".equals(body) || body.isEmpty());
                })
                .block();
    }

    /**
     * Starts two threads.<br/>
     * They transfer random amount in range [0.01, 10] from 'a' to 'b'.<br/>
     * Then checks the remaining balances. 'a' must be 0, 'b' must be 10.
     */
    @Test
    void doubleSpend() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(2);
        int transfersCount = 3000;
        CountDownLatch latch = new CountDownLatch(2);
        service.execute(() -> {
            Random random = new Random(46);
            for (int i = 0; i < transfersCount; i++) {
                transfer("a", "b", BigDecimal.valueOf((double) (1 + random.nextInt(1000)) / 100));
            }
            latch.countDown();
        });
        service.execute(() -> {
            Random random = new Random(64);
            for (int i = 0; i < transfersCount; i++) {
                transfer("a", "b", BigDecimal.valueOf((double) (1 + random.nextInt(1000)) / 100));
            }
            latch.countDown();
        });

        latch.await();

        BigDecimal aBalance = server.service.balance("a");
        BigDecimal bBalance = server.service.balance("b");

        assertEquals("0", aBalance.stripTrailingZeros().toPlainString());
        assertEquals("10", bBalance.stripTrailingZeros().toPlainString());

        service.shutdown();
    }
}