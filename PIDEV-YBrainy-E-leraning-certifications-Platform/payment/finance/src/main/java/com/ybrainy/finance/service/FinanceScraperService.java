package com.ybrainy.finance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FinanceScraperService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinanceScraperService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${finance.scraper.python-command:python}")
    private String pythonCommand;

    @Value("${finance.scraper.script-name:elearning_scraper.py}")
    private String scriptName;

    @Value("${finance.scraper.working-directory:D:/4eme/project pi/spring/PIDEV-YBrainy-E-leraning-certifications-Platform/scraper}")
    private String workingDirectory;

    private volatile Instant lastStartedAt;
    private volatile Instant lastFinishedAt;
    private volatile Integer lastExitCode;
    private volatile String lastError;

    public ScraperRunStatus startScraper() {
        if (!running.compareAndSet(false, true)) {
            return snapshot("already_running", "Scraper is already running.");
        }

        lastStartedAt = Instant.now();
        lastFinishedAt = null;
        lastExitCode = null;
        lastError = null;

        Thread worker = new Thread(this::executeScraperProcess, "finance-scraper-runner");
        worker.setDaemon(true);
        worker.start();

        return snapshot("started", "Scraper job started.");
    }

    public ScraperRunStatus getStatus() {
        return snapshot("ok", "Status fetched.");
    }

    private void executeScraperProcess() {
        try {
            Path processDirectory = Paths.get(workingDirectory).toAbsolutePath().normalize();
            if (!Files.isDirectory(processDirectory)) {
                throw new IllegalStateException("Scraper working directory not found: " + processDirectory);
            }

            Path scriptPath = processDirectory.resolve(scriptName).normalize();
            if (!Files.exists(scriptPath)) {
                throw new IllegalStateException("Scraper script not found: " + scriptPath);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(pythonCommand, scriptPath.toString());
            processBuilder.directory(processDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            LOGGER.info("Starting finance scraper process: '{}' '{}' in '{}'",
                    pythonCommand, scriptPath, processDirectory);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[finance-scraper] {}", line);
                }
            }

            int exitCode = process.waitFor();
            lastExitCode = exitCode;
            if (exitCode != 0) {
                lastError = "Scraper exited with code " + exitCode;
            }
            LOGGER.info("Finance scraper process finished with exit code {}", exitCode);
        } catch (Exception ex) {
            lastExitCode = -1;
            lastError = ex.getMessage();
            LOGGER.error("Finance scraper process failed", ex);
        } finally {
            lastFinishedAt = Instant.now();
            running.set(false);
        }
    }

    private ScraperRunStatus snapshot(String state, String message) {
        return new ScraperRunStatus(
                state,
                message,
                running.get(),
                lastStartedAt,
                lastFinishedAt,
                lastExitCode,
                lastError
        );
    }

    public record ScraperRunStatus(
            String state,
            String message,
            boolean running,
            Instant lastStartedAt,
            Instant lastFinishedAt,
            Integer lastExitCode,
            String lastError
    ) {
    }
}