package com.vulntrend.batch.infrastructure.summary;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** OpenAI Responses API 흉내. 마지막 요청 본문·경로·보낸 열쇠를 기억하고, 정해 둔 상태 코드와 본문으로 답한다 */
public class OpenAiStubServer implements AutoCloseable {

    private final HttpServer server;
    private int status = 200;
    private String body = "{}";
    private String lastRequestBody;
    private String lastCredential;
    private String lastPath;

    public OpenAiStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /** SDK 의 baseUrl 은 /v1 까지 포함한다(기본 https://api.openai.com/v1) */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public void answer(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public String lastRequestBody() {
        return lastRequestBody;
    }

    /** Authorization: Bearer 뒤에 실려 간 열쇠 값 */
    public String lastCredential() {
        return lastCredential;
    }

    public String lastPath() {
        return lastPath;
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastPath = exchange.getRequestURI().getPath();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        lastCredential = Objects.isNull(authorization) ? null : authorization.replaceFirst("^Bearer ", "");
        lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
