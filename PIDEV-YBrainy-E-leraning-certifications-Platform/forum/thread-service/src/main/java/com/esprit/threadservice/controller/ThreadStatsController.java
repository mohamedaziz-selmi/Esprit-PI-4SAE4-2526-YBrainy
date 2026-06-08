package com.esprit.threadservice.controller;

import com.esprit.threadservice.dto.ThreadUserStatsDto;
import com.esprit.threadservice.service.ThreadStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/threads/stats")
@RequiredArgsConstructor
public class ThreadStatsController {

    private final ThreadStatsService statsService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<ThreadUserStatsDto> getUserStats(@PathVariable Long userId) {
        return ResponseEntity.ok(statsService.getStatsForUser(userId));
    }
}
