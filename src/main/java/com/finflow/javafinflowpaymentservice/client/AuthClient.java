package com.finflow.javafinflowpaymentservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AuthClient {
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://java-finflow-auth-service:9001").build();
    }

    @CircuitBreaker(name = "authService", fallbackMethod = "fallbackVerifyToken")
    public JsonNode verifyToken(String token) {
        String response = webClient.get()
            .uri("/api/auth/verify-token")
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse auth response");
        }
    }

    public JsonNode fallbackVerifyToken(String token, Exception ex) {
        throw new RuntimeException("Auth service down", ex);
    }
}

