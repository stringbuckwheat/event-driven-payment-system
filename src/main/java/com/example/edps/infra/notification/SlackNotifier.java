package com.example.edps.infra.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

@Component
@Slf4j
public class SlackNotifier {
    private final WebClient webClient;

    public SlackNotifier(@Value("${slack.webhook.url}") String webhookUrl) {
        this.webClient = WebClient.builder().baseUrl(webhookUrl).build();
    }

    public void sendDlqAlert(String originalTopic, String eventId, Long orderId, Long paymentId, String cause, String message) {
        String body = """
            {
                "text": ":rotating_light: *DLQ Alert*\\n• originalTopic: `%s`\\n• eventId: `%s`\\n• orderId: `%s`\\n• paymentId: `%s`\\n• cause: `%s`\\n• message: `%s`\\n• time: `%s`"
            }
            """.formatted(originalTopic, eventId, orderId, paymentId, cause, message, LocalDateTime.now());

        try {
            webClient.post()
                    .bodyValue(body)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("Slack 알람 전송 실패", e);
        }
    }
}
