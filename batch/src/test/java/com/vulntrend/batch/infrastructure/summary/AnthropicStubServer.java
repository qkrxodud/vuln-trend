package com.vulntrend.batch.infrastructure.summary;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Claude Messages API 흉내. 마지막 요청 본문·보낸 열쇠를 기억하고, 정해 둔 상태 코드와 본문으로 답한다 */
public class AnthropicStubServer implements AutoCloseable {

    private final HttpServer server;
    private int status = 200;
    private String body = "{}";
    private String lastRequestBody;
    private String lastCredential;
    private String lastPath;
    private String lastBetaHeader;

    public AnthropicStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void answer(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public String lastRequestBody() {
        return lastRequestBody;
    }

    /** SDK 3.x 는 열쇠를 x-api-key 또는 Authorization: Bearer 로 보낸다. 어느 쪽이든 실려 간 열쇠 값 */
    public String lastCredential() {
        return lastCredential;
    }

    public String lastPath() {
        return lastPath;
    }

    public String lastBetaHeader() {
        return lastBetaHeader;
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastPath = exchange.getRequestURI().getPath();
        lastCredential = credentialOf(exchange);
        lastBetaHeader = exchange.getRequestHeaders().getFirst("anthropic-beta");
        lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String credentialOf(HttpExchange exchange) {
        String apiKey = exchange.getRequestHeaders().getFirst("x-api-key");
        if (Objects.nonNull(apiKey)) {
            return apiKey;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        return Objects.isNull(authorization) ? null : authorization.replaceFirst("^Bearer ", "");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
