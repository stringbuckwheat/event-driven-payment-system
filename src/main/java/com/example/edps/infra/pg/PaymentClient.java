package com.example.edps.infra.pg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentClient {
    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:9099")
            .build();

    public void requestPayment(Long paymentId, int amount, String scenario) {
        webClient.post()
                .uri("/pg/payments")
                .headers(h -> {
                    if (StringUtils.hasText(scenario)) {
                        h.add("X-MOCK-SCENARIO", scenario);
                    }
                })
                .bodyValue(new PgPaymentRequest(paymentId, amount))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(2)) // 타임아웃 2초
                .doOnNext(body -> log.info("PG 응답: {}", body))
                .block();
    }
}
