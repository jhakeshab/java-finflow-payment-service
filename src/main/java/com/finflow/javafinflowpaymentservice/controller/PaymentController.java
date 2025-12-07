package com.finflow.javafinflowpaymentservice.controller;

import com.finflow.javafinflowpaymentservice.service.PaymentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestHeader("Authorization") String authHeader,
                                       @RequestBody Map<String, Object> payload) {
        String token = authHeader.replace("Bearer ", "");
        var payment = paymentService.processPayment(token, payload);
        return Map.of(
            "payment_id", payment.getPaymentId(),
            "status", payment.getStatus(),
            "amount", payment.getAmount(),
            "from_wallet_id", payment.getFromWalletId(),
            "to_wallet_id", payment.getToWalletId()
        );
    }

    @GetMapping("/{idempotencyKey}/status")
    public Map<String, Object> getStatus(@PathVariable String idempotencyKey) {
        var payment = paymentService.getPayment(idempotencyKey);
        return Map.of(
            "payment_id", payment.getPaymentId(),
            "status", payment.getStatus(),
            "created_at", payment.getCreatedAt()
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }
}

