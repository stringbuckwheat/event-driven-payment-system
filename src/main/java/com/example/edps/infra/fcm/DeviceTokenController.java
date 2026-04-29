package com.example.edps.infra.fcm;

import com.example.edps.global.common.AppHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/devices")
public class DeviceTokenController {

    private final UserDeviceTokenRepository userDeviceTokenRepository;

    public record RegisterTokenRequest(@NotBlank String fcmToken) {}

    @PostMapping("/token")
    public ResponseEntity<Void> register(
            @RequestHeader(AppHeaders.USER_ID) String userId,
            @Valid @RequestBody RegisterTokenRequest request) {

        userDeviceTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        token -> token.updateToken(request.fcmToken()),
                        () -> userDeviceTokenRepository.save(UserDeviceToken.create(userId, request.fcmToken()))
                );
        return ResponseEntity.ok().build();
    }
}
