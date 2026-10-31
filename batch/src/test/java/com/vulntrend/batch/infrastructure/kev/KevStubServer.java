package com.vulntrend.batch.infrastructure.kev;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/** CISA KEV 흉내 HTTP 서버(외부 시스템이라 테스트 안에서 띄운다). 미리 넣어 둔 답을 순서대로 돌려주고 받은 요청 수를 센다 */
class KevStubServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<Integer> statuses = new ArrayDeque<>();
    private final Deque<String> bodies = new ArrayDeque<>();
    private int requests;

    KevStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/kev.json", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/kev.json";
    }

    KevStubServer answer(int status, String body) {
        statuses.add(status);
        bodies.add(body);
        return this;
    }

    int requests() {
        return requests;
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests++;
        int status = statuses.isEmpty() ? 500 : statuses.poll();
        String text = bodies.isEmpty() ? "no answer prepared" : bodies.poll();
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
