package com.chemist.moneytransfer.server;


import com.chemist.moneytransfer.processing.InMemoryMoneyTransferService;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpServer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Warning! SSL termination must be done at frontend level (nginx, haproxy).
 */
public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    //package private for testing purpose
    final InMemoryMoneyTransferService service;
    private final HttpServer httpServer;
    private volatile DisposableServer disposableServer;

    public Server(Config config, Map<String, BigDecimal> accounts) {
        service = new InMemoryMoneyTransferService(config, accounts);
        var apiHandler = new ApiHandler(service);

        LoopResources loopResources = LoopResources.create("nio", config.nioThreads, 1, true);
        TcpServer tcpServer = TcpServer.create()
                .runOn(loopResources, true)
                .selectorOption(ChannelOption.SO_BACKLOG, config.backlog)
                .selectorOption(ChannelOption.SO_REUSEADDR, true)
                .host(config.host)
                .port(config.port);

        httpServer = HttpServer.from(tcpServer).handle(apiHandler::handle);
    }

    public void start() {
        if (disposableServer != null) throw new IllegalStateException("Server is already started");
        disposableServer = httpServer.bindNow();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        log.info("Server started on {}:{}", disposableServer.host(), disposableServer.port());
    }

    public int port() {
        checkServerStarted();
        return disposableServer.port();
    }

    public void blockUntilShutdown() {
        checkServerStarted();
        disposableServer.onDispose().block();
    }

    public void stop() {
        checkServerStarted();
        disposableServer.disposeNow();
        try {
            service.shutdown();
        } catch (InterruptedException e) {
            log.error("Service shutdown is interrupted", e);
        }
        disposableServer = null;
        log.info("Server stopped");
    }

    private void checkServerStarted() {
        if (disposableServer == null) throw new IllegalStateException("Server isn't started");
    }

    public static void main(String[] args) {
        Config config = new Config();
        var initialAccounts = Map.of("a", BigDecimal.valueOf(10), "b", BigDecimal.valueOf(0));
        //todo load config from a local file
        Server server = new Server(config, initialAccounts);
        server.start();
        server.blockUntilShutdown();
    }
}
