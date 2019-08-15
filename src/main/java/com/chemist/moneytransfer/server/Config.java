package com.chemist.moneytransfer.server;

import java.util.concurrent.TimeUnit;

public class Config {
    /**
     * The port should be > 1024 (because we don't run server as root) and
     * port should be < 32768 (do not overlap with net.ipv4.ip_local_port_range kernel variable, which is used for outgoing connections)
     */
    public int port = 4646;
    public String host = "0.0.0.0";
    public int backlog = 10240;

    public int nioThreads = Runtime.getRuntime().availableProcessors() / 2;
    public int processingThreads = Runtime.getRuntime().availableProcessors() / 2;
    public int balanceMaxScale = 2;
    public long idempotencyKeyCacheLifetimeSec = TimeUnit.DAYS.toSeconds(1);
}
