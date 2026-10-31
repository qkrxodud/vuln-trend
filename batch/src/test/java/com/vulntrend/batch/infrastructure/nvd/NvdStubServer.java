package com.vulntrend.batch.infrastructure.nvd;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NVD 흉내 HTTP 서버(외부 시스템이라 테스트 안에서 띄운다). 미리 넣어 둔 답을 순서대로 돌려주고 받은 요청을 기억한다 */
class NvdStubServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<Answer> answers = new ArrayDeque<>();
    private final List<Received> received = new ArrayList<>();

    NvdStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cves", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/cves";
    }

    NvdStubServer answer(int status, String body) {
        answers.add(new Answer(status, body));
        return this;
    }

    /** startIndex 부터 count 건이 든 한 쪽. 전체는 total 건 */
    NvdStubServer page(int startIndex, int count, int total) {
        return answer(200, pageJson(startIndex, count, total));
    }

    List<Received> received() {
        return List.copyOf(received);
    }

    static String pageJson(int startIndex, int count, int total) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"cve\":{\"id\":\"CVE-2026-").append(10000 + startIndex + i)
                    .append("\",\"vulnStatus\":\"Analyzed\",\"published\":\"2026-10-01T10:15:09.143\"}}");
        }
        return "{\"resultsPerPage\":" + count + ",\"startIndex\":" + startIndex + ",\"totalResults\":" + total
                + ",\"format\":\"NVD_CVE\",\"version\":\"2.0\",\"timestamp\":\"2026-10-30T12:44:45.494\""
                + ",\"vulnerabilities\":[" + items + "]}";
    }

    private void handle(HttpExchange exchange) throws IOException {
        received.add(new Received(query(exchange.getRequestURI().getRawQuery()),
                exchange.getRequestHeaders().getFirst("apiKey")));
        Answer answer = answers.isEmpty() ? new Answer(500, "no answer prepared") : answers.poll();
        byte[] body = answer.body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static class Answer {
        private final int status;
        private final String body;

        Answer(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    static class Received {
        private final Map<String, String> query;
        private final String apiKey;

        Received(Map<String, String> query, String apiKey) {
            this.query = query;
            this.apiKey = apiKey;
        }

        String param(String name) {
            return query.get(name);
        }

        String apiKey() {
            return apiKey;
        }
    }
}
