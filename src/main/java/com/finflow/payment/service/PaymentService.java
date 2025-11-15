package com.finflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.payment.dto.PaymentRequest;
import com.finflow.payment.entity.Payment;
import com.finflow.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String AUTH_URL = "http://localhost:9001/api/auth";
    private final String WALLET_URL = "http://localhost:9002/api/wallet";

    public Payment processTransfer(PaymentRequest req) {
        // Idempotency check
        if (redisTemplate.opsForValue().get("idemp:" + req.getIdempotencyKey()) != null) {
            return paymentRepository.findByIdempotencyKey(req.getIdempotencyKey()).orElseThrow();
        }

        // KYC check
        Map fromUser = restTemplate.getForObject(AUTH_URL + "/user/" + req.getFromUserId(), Map.class);
        if (!"verified".equals(fromUser.get("kycStatus"))) {
            throw new RuntimeException("KYC not verified");
        }

        // Balance check
        BigDecimal balance = restTemplate.getForObject(WALLET_URL + "/" + req.getFromWalletId() + "/balance", BigDecimal.class);
        if (balance.compareTo(req.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // Update wallets (debit from, credit to)
        Map<String, Object> debitReq = Map.of("amount", req.getAmount().negate(), "type", "debit", "referenceId", "pay_" + req.getIdempotencyKey(), "description", "Payment transfer");
        restTemplate.postForObject(WALLET_URL + "/" + req.getFromWalletId() + "/update-balance", debitReq, String.class);

        Map<String, Object> creditReq = Map.of("amount", req.getAmount(), "type", "credit", "referenceId", "pay_" + req.getIdempotencyKey(), "description", "Payment received");
        restTemplate.postForObject(WALLET_URL + "/" + req.getToWalletId() + "/update-balance", creditReq, String.class);

        // Save payment
        Payment payment = new Payment();
        payment.setFromUserId(req.getFromUserId());
        payment.setToUserId(req.getToUserId());
        payment.setFromWalletId(req.getFromWalletId());
        payment.setToWalletId(req.getToWalletId());
        payment.setAmount(req.getAmount());
        payment.setCurrency(req.getCurrency());
        payment.setIdempotencyKey(req.getIdempotencyKey());
        payment.setStatus("completed");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        // Cache idempotency
        redisTemplate.opsForValue().set("idemp:" + req.getIdempotencyKey(), saved.getId(), 86400);

        // Publish event
        try {
            String json = objectMapper.writeValueAsString(saved);
            kafkaTemplate.send("payment.completed", json);
        } catch (JsonProcessingException e) {
            // Log and rethrow or handle
            throw new RuntimeException("Failed to serialize payment to JSON", e);
        }

        return saved;
    }

    public Payment getPaymentByIdempotency(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
    }

    @KafkaListener(topics = "wallet.status_changed")
    public void handleWalletStatusChange(String message) {
        // If frozen, update pending payments to failed
        // Parse message, query payments, update status, publish payment.failed
    }

    // Add refund method similar to processTransfer, with negative amount and status "refunded"
}