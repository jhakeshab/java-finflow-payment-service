package com.finflow.javafinflowpaymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.javafinflowpaymentservice.client.AuthClient;
import com.finflow.javafinflowpaymentservice.client.WalletClient;
import com.finflow.javafinflowpaymentservice.model.Payment;
import com.finflow.javafinflowpaymentservice.repository.PaymentRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AuthClient authClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentService(PaymentRepository paymentRepository, AuthClient authClient, KafkaTemplate<String, String> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.authClient = authClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public Payment processPayment(String token, Map<String, Object> payload) {
        JsonNode user = authClient.verifyToken(token);
        if (!user.get("valid").asBoolean()) {
            throw new RuntimeException("Invalid token");
        }
        if (!"verified".equals(user.get("kyc_status").asText())) {
            throw new RuntimeException("KYC not verified - payment denied");
        }

        String idempotencyKey = (String) payload.get("idempotency_key");
        if (idempotencyKey == null) {
            throw new RuntimeException("idempotency_key is required");
        }

        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Long fromWalletId = ((Number) payload.get("from_wallet_id")).longValue();
        Long toWalletId = ((Number) payload.get("to_wallet_id")).longValue();
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String currency = (String) payload.get("currency");
        String type = (String) payload.get("type");
        String description = (String) payload.get("description");
        Payment payment = new Payment();
        payment.setFromWalletId(fromWalletId);
        payment.setToWalletId(toWalletId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setType(type);
        payment.setDescription(description);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus("completed");
        payment.setCompletedAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);

        String event = String.format("{\"payment_id\":\"%s\",\"from_wallet_id\":%d,\"to_wallet_id\":%d,\"amount\":%s}",
            saved.getPaymentId(), fromWalletId, toWalletId, amount);
        kafkaTemplate.send("payment.completed", event);

        return saved;
    }

    public Payment getPayment(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new RuntimeException("Payment not found"));
    }
}

