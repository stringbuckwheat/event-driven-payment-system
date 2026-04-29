package com.example.edps.infra.notification;

import com.example.edps.domain.payment.event.PaymentSuccessEvent;
import com.example.edps.infra.fcm.UserDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class FcmEventListener {

    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final FcmNotifier fcmNotifier;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        userDeviceTokenRepository.findByUserId(event.userId()).ifPresentOrElse(
                token -> fcmNotifier.sendPaymentSuccess(token.getFcmToken(), event.orderId(), event.amount()),
                () -> log.debug("[FCM] 등록된 토큰 없음 userId={}", event.userId())
        );
    }
}
