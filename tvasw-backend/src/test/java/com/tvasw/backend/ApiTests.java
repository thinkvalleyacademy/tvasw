package com.tvasw.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tvasw.backend.service.GeminiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:apitests", "tvasw.limits.register-per-hour=5"})
@AutoConfigureMockMvc
class ApiTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean GeminiClient gemini;

    private MockHttpServletResponse send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode tree(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private String register(String email) throws Exception {
        MockHttpServletResponse r = send(post("/api/auth/register").header("X-Forwarded-For", UUID.randomUUID().toString()),
            null, Map.of("name", "T", "email", email, "password", "password1"));
        assertEquals(201, r.getStatus());
        return tree(r).get("token").asText();
    }

    private long project(String token) throws Exception {
        MockHttpServletResponse r = send(post("/api/ai/projects"), token, Map.of("name", "P", "description", "fee tracker"));
        assertEquals(201, r.getStatus());
        return tree(r).get("id").asLong();
    }

    @Test
    void aiApiNeedsLogin() throws Exception {
        assertEquals(401, send(post("/api/ai/projects"), null, Map.of("name", "P", "description", "d")).getStatus());
        assertEquals(401, send(get("/api/ai/draft/1"), "bad.token.here", null).getStatus());
    }

    @Test
    void loginChecksPassword() throws Exception {
        register("login@example.com");
        assertEquals(200, send(post("/api/auth/login"), null, Map.of("email", "LOGIN@example.com", "password", "password1")).getStatus());
        assertEquals(401, send(post("/api/auth/login"), null, Map.of("email", "login@example.com", "password", "wrong")).getStatus());
        for (int i = 0; i < 8; i++) send(post("/api/auth/login"), null, Map.of("email", "login@example.com", "password", "wrong"));
        assertEquals(429, send(post("/api/auth/login"), null, Map.of("email", "login@example.com", "password", "password1")).getStatus());
    }

    @Test
    void registerValidatesAndLimits() throws Exception {
        assertEquals(400, send(post("/api/auth/register"), null, Map.of("name", "T", "email", "x", "password", "password1")).getStatus());
        register("dup@example.com");
        int status = 0;
        for (int i = 0; i < 6; i++) {
            status = send(post("/api/auth/register").header("X-Forwarded-For", "203.0.113.9"), null,
                Map.of("name", "T", "email", "dup@example.com", "password", "password1")).getStatus();
            if (i == 0) assertEquals(409, status);
        }
        assertEquals(429, status);
    }

    @Test
    void draftUsesChatAndProjectsArePrivate() throws Exception {
        when(gemini.generate(anyString())).thenReturn("How many students?");
        String owner = register("owner@example.com");
        String other = register("other@example.com");
        long id = project(owner);
        String session = tree(send(post("/api/ai/start"), owner, Map.of("projectId", id, "ideaText", "idea"))).get("sessionId").asText();
        Map<String, Object> message = Map.of("projectId", id, "sessionId", session, "message", "We have 217 students");
        assertEquals(200, send(post("/api/ai/message"), owner, message).getStatus());

        assertEquals(404, send(post("/api/ai/message"), other, message).getStatus());
        assertEquals(404, send(post("/api/ai/start"), other, Map.of("projectId", id, "ideaText", "x")).getStatus());
        assertEquals(404, send(get("/api/ai/draft/" + id), other, null).getStatus());

        clearInvocations(gemini);
        assertEquals(200, send(get("/api/ai/draft/" + id), owner, null).getStatus());
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(gemini, times(2)).generate(prompts.capture());
        prompts.getAllValues().forEach(p -> assertTrue(p.contains("217 students") && p.contains("fee tracker")));
    }

    @Test
    void aiCallsAreLimitedPerMinute() throws Exception {
        when(gemini.generate(anyString())).thenReturn("ok");
        String token = register("limit@example.com");
        long id = project(token);
        int status = 0;
        for (int i = 0; i < 11; i++) {
            status = send(post("/api/ai/start"), token, Map.of("projectId", id, "ideaText", "x")).getStatus();
            if (i == 9) assertEquals(200, status);
        }
        assertEquals(429, status);
    }
}
