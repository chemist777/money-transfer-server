package com.chemist.moneytransfer.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.util.Map;

abstract class AbstractServerTest {
    Server server;
    HttpClient client;

    @BeforeEach
    void init() {
        Config config = new Config();

        //we should make server port zero to obtain unused port from the kernel,
        //otherwise we can get exceptions like "bind failed" in CI (teamcity for example)
        config.port = 0;

        var accounts = Map.of("a", BigDecimal.valueOf(10), "b", BigDecimal.valueOf(0));

        server = new Server(config, accounts);
        server.start();

        client = HttpClient.create().port(server.port());
    }

    @AfterEach
    void shutdown() {
        server.stop();
    }
}
