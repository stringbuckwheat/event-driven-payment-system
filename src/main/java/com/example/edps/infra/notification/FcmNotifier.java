package com.example.edps.infra.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FcmNotifier {

    public void sendPaymentSuccess(String fcmToken, Long orderId, int amount) {
        log.info("FCM TOKEN: {}", fcmToken);

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putData("orderId", String.valueOf(orderId))
                    .putData("type", "PAYMENT_SUCCESS")
                    .putData("title", "결제 완료")
                    .putData("body", "주문 #" + orderId + " 결제 완료. ₩" + amount)
                    .build();

            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] 전송 성공 orderId={}, messageId={}", orderId, messageId);
        } catch (Exception e) {
            log.error("[FCM] 전송 실패 orderId={}", orderId, e);
        }
    }
}
