package com.chemist.moneytransfer.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerTest extends AbstractServerTest {
    @Test
    void notFound() {
        var mono = client
                .post()
                .uri("/transfer2?sender=a&recipient=b&amount=11")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.NOT_FOUND, resp.status());
                    return buf.asString();
                });
        assertEquals("Page not found.", mono.block());
    }

    @Test
    void noRequiredHeader() {
        var mono = client
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=11")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.BAD_REQUEST, resp.status());
                    return buf.asString();
                });
        assertEquals("'Idempotency-Key' header is required.", mono.block());
    }

    @Test
    void requiredParamOmitted() {
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=&amount=11")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.BAD_REQUEST, resp.status());
                    return buf.asString();
                });
        assertEquals("'recipient' param is required.", mono.block());
    }

    @Test
    void noMoreMoney() {
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=11")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, resp.status());
                    return buf.asString();
                });
        assertEquals("Sender doesn't have enough money.", mono.block());
    }

    @Test
    void invalidAmountScale() {
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=1.002")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.BAD_REQUEST, resp.status());
                    return buf.asString();
                });
        assertEquals("'amount' param has invalid value.", mono.block());
    }

    @Test
    void negativeAmount() {
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=-1")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.BAD_REQUEST, resp.status());
                    return buf.asString();
                });
        assertEquals("'amount' param has invalid value.", mono.block());
    }

    @Test
    void zeroAmount() {
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=0")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.BAD_REQUEST, resp.status());
                    return buf.asString();
                });
        assertEquals("'amount' param has invalid value.", mono.block());
    }

    @Test
    void badMethod() {
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .get()
                .uri("/transfer?sender=a&recipient=b&amount=1")
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, resp.status());
                    return buf.asString();
                });
        assertEquals("Method not allowed.", mono.block());
    }

    @Test
    void successfulForwardAndBackwardTransfer() {
        //from a to b
        client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=10")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.OK, resp.status());
                    return buf.asString();
                })
                .block();

        //from b to a
        client
                .headers(headers -> headers.set("Idempotency-Key", "456"))
                .post()
                .uri("/transfer?sender=b&recipient=a&amount=10")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.OK, resp.status());
                    return buf.asString();
                })
                .block();
    }

    @Test
    void doubleSpendWithSameIdempotencyKey() {
        //from a to b
        client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=10")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.OK, resp.status());
                    return buf.asString();
                })
                .block();

        //from a to b
        client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=10")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.OK, resp.status());
                    return buf.asString();
                })
                .block();
    }

    @Test
    void doubleSpendWithAnotherIdempotencyKey() {
        //from a to b
        client
                .headers(headers -> headers.set("Idempotency-Key", "123"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=10")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.OK, resp.status());
                    return buf.asString();
                })
                .block();

        //from a to b
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "456"))
                .post()
                .uri("/transfer?sender=a&recipient=b&amount=10")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, resp.status());
                    return buf.asString();
                });
        assertEquals("Sender doesn't have enough money.", mono.block());
    }

    @Test
    void unknownAccount() {
        //from a to c
        var mono = client
                .headers(headers -> headers.set("Idempotency-Key", "456"))
                .post()
                .uri("/transfer?sender=a&recipient=c&amount=10")
                .send(Mono.empty())
                .responseSingle((resp, buf) -> {
                    assertEquals(HttpResponseStatus.BAD_REQUEST, resp.status());
                    return buf.asString();
                });
        assertEquals("Account 'c' not found.", mono.block());
    }
}