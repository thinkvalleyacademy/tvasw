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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:apitests", "tvasw.limits.register-per-hour=5", "tvasw.app.admin-token=test-token"})
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
        assertEquals(401, send(get("/api/ai/projects"), "bad.token.here", null).getStatus());
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
    void chatDraftAndSubmit() throws Exception {
        when(gemini.generateJson(anyString(), anyMap())).thenReturn(
            "{\"message\":\"How many students?\",\"options\":[\"Under 100\",\"100-500\"],\"progress\":40}");
        when(gemini.generate(anyString())).thenReturn("draft text");
        String owner = register("owner@example.com");
        String other = register("other@example.com");
        long id = project(owner);
        String url = "/api/ai/projects/" + id;

        JsonNode started = tree(send(post(url + "/start"), owner, null));
        assertEquals("100-500", started.at("/messages/0/options/1").asText());
        assertEquals(40, started.at("/project/progress").asInt());
        assertEquals(1, tree(send(post(url + "/start"), owner, null)).get("messages").size());

        Map<String, Object> message = Map.of("message", "We have 217 students");
        assertEquals(409, send(post(url + "/submit"), owner, null).getStatus());
        assertEquals(3, tree(send(post(url + "/messages"), owner, message)).get("messages").size());

        assertEquals(404, send(get(url), other, null).getStatus());
        for (String action : new String[]{"/start", "/draft", "/submit"}) {
            assertEquals(404, send(post(url + action), other, null).getStatus());
        }
        assertEquals(404, send(post(url + "/messages"), other, message).getStatus());
        assertEquals(0, tree(send(get("/api/ai/projects"), other, null)).size());

        assertEquals("draft text", tree(send(post(url + "/draft"), owner, null)).get("prd").asText());
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(gemini, times(2)).generate(prompts.capture());
        prompts.getAllValues().forEach(p -> assertTrue(p.contains("217 students") && p.contains("fee tracker")));

        // A newer message makes the draft outdated, so it cannot be submitted
        assertTrue(tree(send(post(url + "/messages"), owner, message)).get("draftOutdated").asBoolean());
        assertEquals(409, send(post(url + "/submit"), owner, null).getStatus());
        send(post(url + "/draft"), owner, null);

        assertEquals(404, send(get("/api/internal/projects/" + id).header("X-Admin-Token", "test-token"), null, null).getStatus());
        assertEquals("SUBMITTED", tree(send(post(url + "/submit"), owner, null)).at("/project/status").asText());
        assertEquals(409, send(post(url + "/messages"), owner, message).getStatus());

        // Admin API
        String admin = "/api/internal/projects";
        assertEquals(401, send(get(admin), null, null).getStatus());
        assertEquals(403, send(get(admin), owner, null).getStatus());
        assertEquals(401, send(get(admin).header("X-Admin-Token", "wrong"), null, null).getStatus());
        JsonNode listed = tree(send(get(admin).header("X-Admin-Token", "test-token"), null, null));
        assertEquals("owner@example.com", listed.at("/0/clientEmail").asText());
        assertEquals(400, send(post(admin + "/" + id + "/status").header("X-Admin-Token", "test-token"), null, Map.of("status", "X")).getStatus());
        send(post(admin + "/" + id + "/status").header("X-Admin-Token", "test-token"), null, Map.of("status", "QUOTED"));
        assertEquals("QUOTED", tree(send(get(url), owner, null)).at("/project/status").asText());
    }

    @Test
    void interviewFinishes() throws Exception {
        when(gemini.generateJson(anyString(), anyMap())).thenReturn("{\"message\":\"More?\",\"options\":[\"A\"],\"progress\":95}");
        when(gemini.generate(anyString())).thenReturn("doc");
        String token = register("finish@example.com");
        String url = "/api/ai/projects/" + project(token);
        assertEquals("GATHERING", tree(send(get(url), token, null)).at("/project/stage").asText());
        assertEquals("READY", tree(send(post(url + "/start"), token, null)).at("/project/stage").asText());

        JsonNode r = tree(send(post(url + "/messages"), token, Map.of("message", "x")));
        assertEquals(100, r.at("/project/progress").asInt());
        assertEquals(0, r.at("/messages/2/options").size());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini, atLeastOnce()).generateJson(prompt.capture(), anyMap());
        assertTrue(prompt.getValue().contains("FINISHED"));

        assertEquals("DOCUMENT_READY", tree(send(post(url + "/draft"), token, null)).at("/project/stage").asText());
        assertEquals("DOCUMENT_OUTDATED", tree(send(post(url + "/messages"), token, Map.of("message", "y"))).at("/project/stage").asText());
    }

    @Test
    void plainTextReplyStillWorks() throws Exception {
        when(gemini.generateJson(anyString(), anyMap())).thenReturn("not json");
        String token = register("plain@example.com");
        JsonNode r = tree(send(post("/api/ai/projects/" + project(token) + "/start"), token, null));
        assertEquals("not json", r.at("/messages/0/content").asText());
    }

    @Test
    void aiCallsAreLimitedPerMinute() throws Exception {
        when(gemini.generateJson(anyString(), anyMap())).thenReturn("ok");
        String token = register("limit@example.com");
        long id = project(token);
        int status = 0;
        for (int i = 0; i < 11; i++) {
            status = send(post("/api/ai/projects/" + id + "/messages"), token, Map.of("message", "x")).getStatus();
            if (i == 9) assertEquals(200, status);
        }
        assertEquals(429, status);
    }
}
