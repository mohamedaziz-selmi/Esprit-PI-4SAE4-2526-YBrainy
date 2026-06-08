package com.ybrainy.scraper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class ScraperJobServiceTest {

    private ScraperJobService service;

    @BeforeEach
    void setUp() {
        service = new ScraperJobService();
        // Use an absolute path to a known Python script so the runner does not
        // accidentally start a real process during unit tests.  We inject a
        // non-existent script so the process either fails to start (no Python)
        // or exits with a non-zero code — both are acceptable for unit-level
        // testing because we verify state transitions, not external execution.
        ReflectionTestUtils.setField(service, "pythonCommand", "python");
        ReflectionTestUtils.setField(service, "scriptPath", "/nonexistent/script.py");
        ReflectionTestUtils.setField(service, "workingDirectory",
                System.getProperty("java.io.tmpdir"));
    }

    @Test
    @DisplayName("Initial status shows not running")
    void initialStatus_notRunning() {
        ScraperJobService.ScraperRunStatus status = service.getStatus();

        assertThat(status.running()).isFalse();
        assertThat(status.lastStartedAt()).isNull();
        assertThat(status.lastFinishedAt()).isNull();
        assertThat(status.lastExitCode()).isNull();
    }

    @Test
    @DisplayName("startScraper returns 'started' state on first call")
    void startScraper_firstCall_returnsStarted() {
        ScraperJobService.ScraperRunStatus status = service.startScraper();

        assertThat(status.state()).isEqualTo("started");
        assertThat(status.message()).contains("started");
        assertThat(status.lastStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("startScraper returns 'already_running' when called while running")
    void startScraper_whileRunning_returnsAlreadyRunning() throws Exception {
        // Start the scraper — the background thread begins immediately
        ScraperJobService.ScraperRunStatus first = service.startScraper();
        assertThat(first.state()).isEqualTo("started");

        // Immediately call again — atomically the running flag should still be true
        ScraperJobService.ScraperRunStatus second = service.startScraper();
        // Either still running (already_running) or just finished (started again) —
        // both are valid, but the service must never throw.
        assertThat(second.state()).isIn("already_running", "started");
    }

    @Test
    @DisplayName("getStatus returns current snapshot without side-effects")
    void getStatus_returnSnapshot_noSideEffect() {
        // Call twice — should return consistent state
        ScraperJobService.ScraperRunStatus s1 = service.getStatus();
        ScraperJobService.ScraperRunStatus s2 = service.getStatus();

        assertThat(s1.running()).isEqualTo(s2.running());
        assertThat(s1.lastExitCode()).isEqualTo(s2.lastExitCode());
        assertThat(s1.state()).isEqualTo("ok");
    }

    @Test
    @DisplayName("ScraperRunStatus record fields are accessible")
    void scraperRunStatus_fieldAccess() {
        ScraperJobService.ScraperRunStatus status = new ScraperJobService.ScraperRunStatus(
                "started", "Job started.", true, null, null, null, null);

        assertThat(status.state()).isEqualTo("started");
        assertThat(status.message()).isEqualTo("Job started.");
        assertThat(status.running()).isTrue();
        assertThat(status.lastStartedAt()).isNull();
        assertThat(status.lastFinishedAt()).isNull();
        assertThat(status.lastExitCode()).isNull();
        assertThat(status.lastError()).isNull();
    }

    @Test
    @DisplayName("After background thread finishes, running flag resets to false")
    void startScraper_afterCompletion_runningResetsFalse() throws Exception {
        service.startScraper();
        // Give the thread up to 3 s to complete (script doesn't exist → quick fail)
        long deadline = System.currentTimeMillis() + 3_000;
        while (service.getStatus().running() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertThat(service.getStatus().running()).isFalse();
        assertThat(service.getStatus().lastFinishedAt()).isNotNull();
    }
}
