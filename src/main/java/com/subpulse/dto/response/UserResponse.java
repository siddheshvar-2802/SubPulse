package com.subpulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String timezone;
    private String preferredCurrency;
    private Boolean isActive;
    private Boolean emailVerified;
    private String oauth2Provider;
    private LocalDateTime createdAt;
}
