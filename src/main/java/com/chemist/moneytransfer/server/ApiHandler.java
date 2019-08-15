package com.chemist.moneytransfer.server;

import com.chemist.moneytransfer.core.MoneyTransferException;
import com.chemist.moneytransfer.core.MoneyTransferService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.util.annotation.Nullable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

class ApiHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);

    private final MoneyTransferService service;

    ApiHandler(MoneyTransferService service) {
        this.service = service;
    }

    Mono<Void> handle(HttpServerRequest req, HttpServerResponse resp) {
        if (req.path().equals("transfer")) {
            if (req.method() == HttpMethod.POST) {
                return transfer(req, resp);
            } else {
                return fail(resp, HttpResponseStatus.METHOD_NOT_ALLOWED.code(), "Method not allowed.");
            }
        } else {
            return fail(resp, HttpResponseStatus.NOT_FOUND.code(), "Page not found.");
        }
    }

    private Mono<Void> transfer(HttpServerRequest req, HttpServerResponse resp) {
        var idempotencyKey = req.requestHeaders().get("Idempotency-Key");
        if (isEmpty(idempotencyKey)) {
            return fail(resp, HttpResponseStatus.BAD_REQUEST.code(), "'Idempotency-Key' header is required.");
        }

        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        var sender = queryStringParam(decoder, "sender");
        if (sender.isEmpty() || isEmpty(sender.get())) {
            return fail(resp, HttpResponseStatus.BAD_REQUEST.code(), "'sender' param is required.");
        }

        var recipient = queryStringParam(decoder, "recipient");
        if (recipient.isEmpty() || isEmpty(recipient.get())) {
            return fail(resp, HttpResponseStatus.BAD_REQUEST.code(), "'recipient' param is required.");
        }

        var amount = queryStringParam(decoder, "amount");
        if (amount.isEmpty() || isEmpty(amount.get())) {
            return fail(resp, HttpResponseStatus.BAD_REQUEST.code(), "'amount' param is required.");
        }

        BigDecimal parsedAmount;
        try {
            parsedAmount = new BigDecimal(amount.get());
        } catch (Exception e) {
            return fail(resp, HttpResponseStatus.BAD_REQUEST.code(), "'amount' param has invalid value.");
        }

        return Mono.fromFuture(service.transfer(sender.get(), recipient.get(), parsedAmount, idempotencyKey))
                .then(Mono.defer(resp::send))
                .onErrorResume(e -> {
                    if (e instanceof MoneyTransferException) {
                        MoneyTransferException moneyTransferException = (MoneyTransferException) e;
                        return fail(resp, moneyTransferException.getHttpCode(), moneyTransferException.getMessage());
                    } else if (e.getCause() instanceof MoneyTransferException) {
                        MoneyTransferException moneyTransferException = (MoneyTransferException) e.getCause();
                        return fail(resp, moneyTransferException.getHttpCode(), moneyTransferException.getMessage());
                    } else {
                        log.error("Unexpected server error", e);
                        return fail(resp, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), e.getMessage());
                    }
                });
    }

    private Mono<Void> fail(HttpServerResponse resp, int httpCode, String message) {
        ByteBuf buf = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
        return resp
                .status(httpCode)
                .header(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(buf.readableBytes()))
                .send(Mono.just(buf))
                .then();
    }

    private static Optional<String> queryStringParam(QueryStringDecoder decoder, String name) {
        return Optional.ofNullable(decoder.parameters().get(name))
                .map(list -> list.get(0));
    }

    private static boolean isEmpty(@Nullable String value) {
        return value == null || value.isEmpty();
    }
}