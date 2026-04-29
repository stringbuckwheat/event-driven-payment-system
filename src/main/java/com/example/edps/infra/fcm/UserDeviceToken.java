package com.example.edps.infra.fcm;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_device_token",
        indexes = @Index(name = "uk_user_device_token_user_id", columnList = "userId", unique = true))
public class UserDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String userId;

    @Column(nullable = false, length = 512)
    private String fcmToken;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public static UserDeviceToken create(String userId, String fcmToken) {
        UserDeviceToken token = new UserDeviceToken();
        token.userId = userId;
        token.fcmToken = fcmToken;
        return token;
    }

    public void updateToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
