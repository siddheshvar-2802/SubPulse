package com.subpulse.security;

import com.subpulse.entity.User;
import com.subpulse.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * Handles successful OAuth2 authentication (Google, GitHub).
 * Upserts the user in the database, generates a JWT token,
 * and redirects to the frontend dashboard with the token in the URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtils       jwtUtils;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId(); // "google" or "github"
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = extractEmail(provider, attributes);
        String fullName = extractName(provider, attributes, email);
        String providerId = extractProviderId(provider, attributes);

        log.info("Processing OAuth2 login for user: {} via provider: {}", email, provider);

        // Upsert user in database
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.setOauth2Provider(provider);
                    existingUser.setOauth2ProviderId(providerId);
                    if (existingUser.getFullName() == null || existingUser.getFullName().isBlank()) {
                        existingUser.setFullName(fullName);
                    }
                    existingUser.setEmailVerified(true);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .fullName(fullName)
                            .oauth2Provider(provider)
                            .oauth2ProviderId(providerId)
                            .emailVerified(true)
                            .isActive(true)
                            .timezone("UTC")
                            .preferredCurrency("USD")
                            .build();
                    return userRepository.save(newUser);
                });

        // Generate JWT Tokens
        String accessToken = jwtUtils.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        // Redirect back to frontend dashboard with token in URL
        String targetUrl = UriComponentsBuilder.fromUriString("/index.html")
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String extractEmail(String provider, Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        if (email != null && !email.isBlank()) {
            return email.toLowerCase().trim();
        }
        if ("github".equalsIgnoreCase(provider)) {
            String login = (String) attributes.get("login");
            if (login != null && !login.isBlank()) {
                return (login + "@users.noreply.github.com").toLowerCase();
            }
        }
        return "oauth2user_" + System.currentTimeMillis() + "@subpulse.io";
    }

    private String extractName(String provider, Map<String, Object> attributes, String fallbackEmail) {
        String name = (String) attributes.get("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        if ("github".equalsIgnoreCase(provider)) {
            String login = (String) attributes.get("login");
            if (login != null && !login.isBlank()) {
                return login;
            }
        }
        return fallbackEmail.split("@")[0];
    }

    private String extractProviderId(String provider, Map<String, Object> attributes) {
        if ("google".equalsIgnoreCase(provider)) {
            return String.valueOf(attributes.get("sub"));
        }
        if ("github".equalsIgnoreCase(provider)) {
            return String.valueOf(attributes.get("id"));
        }
        return String.valueOf(attributes.getOrDefault("id", attributes.get("sub")));
    }
}
