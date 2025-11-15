package com.finflow.payment.controller;

import com.finflow.payment.dto.PaymentRequest;
import com.finflow.payment.entity.Payment;
import com.finflow.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/transfer")
    public ResponseEntity<Payment> transfer(@RequestBody PaymentRequest req) {
        return ResponseEntity.ok(paymentService.processTransfer(req));
    }

    @GetMapping("/idempotency/{key}")
    public ResponseEntity<Payment> getByIdempotency(@PathVariable String key) {
        return ResponseEntity.ok(paymentService.getPaymentByIdempotency(key));
    }

    // Add /refund endpoint
}