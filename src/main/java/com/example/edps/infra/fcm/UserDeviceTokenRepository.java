package com.example.edps.infra.fcm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {
    Optional<UserDeviceToken> findByUserId(String userId);
}
