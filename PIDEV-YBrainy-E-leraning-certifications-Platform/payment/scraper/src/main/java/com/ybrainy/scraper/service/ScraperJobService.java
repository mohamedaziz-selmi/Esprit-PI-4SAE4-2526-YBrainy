package com.ybrainy.scraper.service;

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
public class ScraperJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScraperJobService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${scraper.python-command:python}")
    private String pythonCommand;

    @Value("${scraper.script-path:elearning_scraper.py}")
    private String scriptPath;

    @Value("${scraper.working-directory:${user.dir}}")
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

        Thread jobThread = new Thread(this::executeScraperProcess, "scraper-job-runner");
        jobThread.setDaemon(true);
        jobThread.start();

        return snapshot("started", "Scraper job started.");
    }

    public ScraperRunStatus getStatus() {
        return snapshot("ok", "Status fetched.");
    }

    private void executeScraperProcess() {
        try {
            Path resolvedScriptPath = resolveScriptPath();
            Path processDirectory = resolvedScriptPath.getParent() != null
                    ? resolvedScriptPath.getParent()
                    : Paths.get(workingDirectory).toAbsolutePath().normalize();

            ProcessBuilder processBuilder = new ProcessBuilder(pythonCommand, resolvedScriptPath.toString());
            processBuilder.directory(processDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            LOGGER.info("Starting scraper process: '{}' '{}' in '{}'",
                    pythonCommand, resolvedScriptPath, processDirectory);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[scraper] {}", line);
                }
            }

            int exitCode = process.waitFor();
            lastExitCode = exitCode;
            LOGGER.info("Scraper process finished with exit code {}", exitCode);
        } catch (Exception ex) {
            lastExitCode = -1;
            lastError = ex.getMessage();
            LOGGER.error("Scraper process failed", ex);
        } finally {
            lastFinishedAt = Instant.now();
            running.set(false);
        }
    }

    private Path resolveScriptPath() {
        Path configuredScript = Paths.get(scriptPath);
        if (configuredScript.isAbsolute()) {
            return configuredScript;
        }

        Path workingDirCandidate = Paths.get(workingDirectory)
                .toAbsolutePath()
                .normalize()
                .resolve(configuredScript)
                .normalize();
        if (Files.exists(workingDirCandidate)) {
            return workingDirCandidate;
        }

        Path projectRootCandidate = Paths.get(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize()
                .resolve("scraper")
                .resolve(configuredScript)
                .normalize();
        if (Files.exists(projectRootCandidate)) {
            return projectRootCandidate;
        }

        return workingDirCandidate;
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
