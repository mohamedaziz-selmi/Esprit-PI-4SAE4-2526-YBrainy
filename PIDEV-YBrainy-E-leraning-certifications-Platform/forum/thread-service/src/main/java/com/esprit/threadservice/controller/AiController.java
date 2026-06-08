package com.esprit.threadservice.controller;

import com.esprit.threadservice.dto.AiAnalysisResponse;
import com.esprit.threadservice.dto.AiChatRequest;
import com.esprit.threadservice.dto.AiChatResponse;
import com.esprit.threadservice.dto.AiGeneratePostRequest;
import com.esprit.threadservice.dto.AiGenerateRequest;
import com.esprit.threadservice.dto.AiGenerateResponse;
import com.esprit.threadservice.dto.MlPredictResponse;
import com.esprit.threadservice.exception.ResourceNotFoundException;
import com.esprit.threadservice.feign.PostFeignClient;
import com.esprit.threadservice.model.ForumThread;
import com.esprit.threadservice.repository.ThreadRepository;
import com.esprit.threadservice.service.AiGenerationService;
import com.esprit.threadservice.service.MlPredictService;
import com.esprit.threadservice.service.ThreadAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiGenerationService aiGenerationService;
    private final ThreadAiService threadAiService;
    private final MlPredictService mlPredictService;
    private final PostFeignClient postFeignClient;
    private final ThreadRepository threadRepository;

    @PostMapping("/generate-thread-body")
    public ResponseEntity<AiGenerateResponse> generateThreadBody(
            @Valid @RequestBody AiGenerateRequest request) {
        log.info("AI question generation for title: {}", request.getTitle());
        try {
            String generated = aiGenerationService.generateThreadBody(request.getTitle());
            return ResponseEntity.ok(AiGenerateResponse.builder().body(generated).build());
        } catch (Exception e) {
            log.error("AI thread generation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(AiGenerateResponse.builder().body("AI generation failed: " + e.getMessage()).build());
        }
    }

    @PostMapping("/generate-post-body")
    public ResponseEntity<AiGenerateResponse> generatePostBody(
            @Valid @RequestBody AiGeneratePostRequest request) {
        log.info("AI answer generation for thread: {}", request.getThreadTitle());
        try {
            String generated = aiGenerationService.generatePostBody(
                    request.getThreadTitle(),
                    request.getThreadBody() != null ? request.getThreadBody() : "");
            return ResponseEntity.ok(AiGenerateResponse.builder().body(generated).build());
        } catch (Exception e) {
            log.error("AI post generation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(AiGenerateResponse.builder().body("AI generation failed: " + e.getMessage()).build());
        }
    }

    /** Pre-submit analysis: analyzes title+body and returns full ThreadQualityScore */
    @PostMapping("/analyze")
    public ResponseEntity<AiAnalysisResponse> analyze(@RequestBody java.util.Map<String, String> body) {
        String title = body.getOrDefault("title", "");
        String threadBody = body.getOrDefault("threadBody", "");
        log.info("AI analyze request for title: {}", title);
        try {
            return ResponseEntity.ok(threadAiService.analyzeThread(title, threadBody));
        } catch (Exception e) {
            log.error("AI analyze failed: {}", e.getMessage());
            return ResponseEntity.ok(AiAnalysisResponse.builder().analysisAvailable(false).build());
        }
    }

    /** Fetch persisted AI score for a thread (after async scoring completes) */
    @GetMapping("/score/thread/{threadId}")
    public ResponseEntity<AiAnalysisResponse> getScore(@PathVariable Long threadId) {
        try {
            return ResponseEntity.ok(threadAiService.getScoreForThread(threadId));
        } catch (Exception e) {
            log.error("Get AI score failed for thread {}: {}", threadId, e.getMessage());
            return ResponseEntity.ok(AiAnalysisResponse.builder().analysisAvailable(false).build());
        }
    }

    /** ML prediction: calls Flask service to classify thread as HQ / LQ_CLOSE / LQ_EDIT */
    @PostMapping("/ml/predict")
    public ResponseEntity<MlPredictResponse> mlPredict(@RequestBody java.util.Map<String, String> body) {
        String title = body.getOrDefault("title", "");
        String threadBody = body.getOrDefault("body", "");
        String tags = body.getOrDefault("tags", "");
        log.info("ML predict request for title: {}", title);
        return ResponseEntity.ok(mlPredictService.predict(title, threadBody, tags));
    }

    /** Summarize all posts of a thread using Groq AI. Requires at least 2 posts. */
    @GetMapping("/summarize/{threadId}")
    public ResponseEntity<Map<String, String>> summarize(@PathVariable Long threadId) {
        log.info("AI summarize request for thread #{}", threadId);
        try {
            ForumThread thread = threadRepository.findById(threadId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));

            List<PostFeignClient.PostSummary> posts = postFeignClient.getPostsByThread(threadId);

            if (posts == null || posts.size() < 2) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "At least 2 posts are required to generate a summary."));
            }

            String summary = aiGenerationService.summarizeThread(thread.getTitle(), thread.getBody(), posts);
            return ResponseEntity.ok(Map.of("summary", summary, "postCount", String.valueOf(posts.size())));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("AI summarize failed for thread #{}: {}", threadId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Summary generation failed. Please try again."));
        }
    }

    /** ML quality stats: run predict on all threads of a user and return HQ/LQ breakdown. */
    @GetMapping("/ml-quality/user/{userId}")
    public ResponseEntity<Map<String, Object>> mlQualityForUser(@PathVariable Long userId) {
        log.info("ML quality stats requested for user #{}", userId);
        try {
            List<com.esprit.threadservice.model.ForumThread> threads =
                    threadRepository.findByAuthorIdOrderByCreatedAtDesc(userId);

            int hq = 0, lqEdit = 0, lqClose = 0, unknown = 0;
            for (com.esprit.threadservice.model.ForumThread t : threads) {
                MlPredictResponse r = mlPredictService.predict(
                        t.getTitle(),
                        t.getBody() != null ? t.getBody() : "",
                        "");
                if (!r.isAvailable()) { unknown++; continue; }
                switch (r.getLabel()) {
                    case "HQ"       -> hq++;
                    case "LQ_EDIT"  -> lqEdit++;
                    case "LQ_CLOSE" -> lqClose++;
                    default         -> unknown++;
                }
            }
            int total = hq + lqEdit + lqClose + unknown;
            int analyzed = hq + lqEdit + lqClose;
            double hqRate = analyzed == 0 ? 0.0 : Math.round((double) hq / analyzed * 1000.0) / 10.0;

            return ResponseEntity.ok(Map.of(
                    "hqCount",      hq,
                    "lqEditCount",  lqEdit,
                    "lqCloseCount", lqClose,
                    "totalThreads", total,
                    "totalAnalyzed", analyzed,
                    "hqRate",       hqRate,
                    "available",    true
            ));
        } catch (Exception e) {
            log.error("ML quality stats failed for user #{}: {}", userId, e.getMessage());
            return ResponseEntity.ok(Map.of("available", false, "hqRate", 0.0,
                    "hqCount", 0, "lqEditCount", 0, "lqCloseCount", 0,
                    "totalAnalyzed", 0, "totalThreads", 0));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        log.info("yForumy chat: {}", request.getMessage());
        try {
            return ResponseEntity.ok(aiGenerationService.chat(request));
        } catch (Exception e) {
            log.error("yForumy chat failed: {}", e.getMessage());
            return ResponseEntity.ok(AiChatResponse.builder()
                    .reply("Sorry, I encountered an error. Please try again.")
                    .relatedThreads(java.util.Collections.emptyList())
                    .build());
        }
    }
}
