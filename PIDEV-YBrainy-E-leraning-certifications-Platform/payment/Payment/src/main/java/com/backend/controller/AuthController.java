package com.backend.controller;

import com.backend.dto.auth.LoginRequest;
import com.backend.dto.auth.LoginResponse;
import com.backend.dto.auth.UserProfileResponse;
import com.backend.service.StaticAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StaticAuthService staticAuthService;

    public AuthController(StaticAuthService staticAuthService) {
        this.staticAuthService = staticAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(staticAuthService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return ResponseEntity.ok(staticAuthService.extractProfile(authorization));
    }
}
