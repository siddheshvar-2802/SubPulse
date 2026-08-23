package com.subpulse.controller;

import com.subpulse.security.CustomUserDetails;
import com.subpulse.security.JwtUtils;
import com.subpulse.service.CalendarFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@Tag(name = "Calendar Sync", description = "Subscribe to renewal feeds in Google Calendar, Apple Calendar, and Outlook")
public class CalendarController {

    private final CalendarFeedService calendarFeedService;
    private final JwtUtils            jwtUtils;

    @GetMapping(value = "/feed.ics", produces = "text/calendar; charset=utf-8")
    @Operation(summary = "Live RFC 5545 iCalendar feed for external calendar apps")
    public ResponseEntity<String> getIcsFeed(@RequestParam("token") String token) {
        String icsContent = calendarFeedService.generateIcsFeed(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar; charset=utf-8"));
        headers.setContentDispositionFormData("attachment", "subpulse-renewals.ics");

        return ResponseEntity.ok()
                .headers(headers)
                .body(icsContent);
    }

    @GetMapping("/sync-info")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get calendar sync links and subscription feed URLs")
    public ResponseEntity<Map<String, String>> getSyncInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request) {

        String token = jwtUtils.generateAccessToken(userDetails.getUsername());
        
        // Build base URL
        String scheme = request.getScheme();
        String host = request.getHeader("Host") != null ? request.getHeader("Host") : "localhost:8080";
        String httpFeedUrl = String.format("%s://%s/api/v1/calendar/feed.ics?token=%s", scheme, host, token);
        String webcalFeedUrl = String.format("webcal://%s/api/v1/calendar/feed.ics?token=%s", host, token);
        
        // Google Calendar Add by URL link
        String encodedFeedUrl = URLEncoder.encode(httpFeedUrl, StandardCharsets.UTF_8);
        String googleCalendarUrl = "https://calendar.google.com/calendar/render?cid=" + encodedFeedUrl;

        Map<String, String> response = new HashMap<>();
        response.put("httpFeedUrl", httpFeedUrl);
        response.put("webcalFeedUrl", webcalFeedUrl);
        response.put("googleCalendarUrl", googleCalendarUrl);
        response.put("token", token);

        return ResponseEntity.ok(response);
    }
}
