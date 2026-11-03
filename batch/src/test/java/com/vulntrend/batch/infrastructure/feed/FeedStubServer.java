package com.vulntrend.batch.infrastructure.feed;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 파일 하나를 내려주는 외부 소스(CISA KEV, FIRST EPSS) 흉내 HTTP 서버. 외부 시스템이라 테스트 안에서 띄운다.
 * 미리 넣어 둔 답을 순서대로 돌려주고 받은 요청 수를 센다. 답이 떨어지면 500 을 준다.
 */
public class FeedStubServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<Integer> statuses = new ArrayDeque<>();
    private final Deque<byte[]> bodies = new ArrayDeque<>();
    private final List<String> paths = new ArrayList<>();
    private int requests;

    public FeedStubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        server.createContext("/feed", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/feed";
    }

    public FeedStubServer answer(int status, String body) {
        return answer(status, body.getBytes(StandardCharsets.UTF_8));
    }

    public FeedStubServer answer(int status, byte[] body) {
        statuses.add(status);
        bodies.add(body);
        return this;
    }

    public int requests() {
        return requests;
    }

    /** 받은 요청 경로(CVE.org 처럼 주소 끝에 번호를 붙여 부르는 소스) */
    public List<String> paths() {
        return List.copyOf(paths);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests++;
        paths.add(exchange.getRequestURI().getPath());
        int status = statuses.isEmpty() ? 500 : statuses.poll();
        byte[] body = bodies.isEmpty() ? "no answer prepared".getBytes(StandardCharsets.UTF_8) : bodies.poll();
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
