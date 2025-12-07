package com.finflow.javafinflowpaymentservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WalletClient {
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public WalletClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://java-finflow-wallet-service:9002").build();
    }

    @CircuitBreaker(name = "walletService", fallbackMethod = "fallbackGetWallet")
    public JsonNode getWallet(Long walletId, String token) {
        String response = webClient.get()
            .uri("/api/wallet/" + walletId)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse wallet response");
        }
    }

    public JsonNode fallbackGetWallet(Long walletId, String token, Exception ex) {
        throw new RuntimeException("Wallet service down", ex);
    }
}

