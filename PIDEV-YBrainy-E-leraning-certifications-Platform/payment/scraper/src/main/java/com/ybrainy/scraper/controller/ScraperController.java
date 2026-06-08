package com.ybrainy.scraper.controller;

import com.ybrainy.scraper.service.ScraperJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/scraper")
public class ScraperController {

    private final ScraperJobService scraperJobService;

    public ScraperController(ScraperJobService scraperJobService) {
        this.scraperJobService = scraperJobService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        ScraperJobService.ScraperRunStatus status = scraperJobService.getStatus();
        return Map.of(
                "service", "scraper-service",
                "status", "UP",
                "running", status.running()
        );
    }

    @GetMapping("/status")
    public ScraperJobService.ScraperRunStatus status() {
        return scraperJobService.getStatus();
    }

    @PostMapping("/run")
    public ResponseEntity<ScraperJobService.ScraperRunStatus> run() {
        ScraperJobService.ScraperRunStatus status = scraperJobService.startScraper();
        if ("already_running".equals(status.state())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(status);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }
}
