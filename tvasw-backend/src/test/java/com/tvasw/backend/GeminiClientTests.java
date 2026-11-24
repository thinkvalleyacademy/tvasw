package com.tvasw.backend;

import com.sun.net.httpserver.HttpServer;
import com.tvasw.backend.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiClientTests {

    @Test
    void retriesWhenBusyAndReturnsText() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ex.getRequestBody().readAllBytes();
            boolean busy = calls.incrementAndGet() == 1;
            byte[] body = (busy ? "{}" : "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\",\"thought\":true},{\"text\":\"Hello\"}]}}]}").getBytes();
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(busy ? 503 : 200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            GeminiClient client = new GeminiClient();
            ReflectionTestUtils.setField(client, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            ReflectionTestUtils.setField(client, "apiKey", "k");
            assertEquals("Hello", client.generate("hi"));
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
