package com.esprit.threadservice.service;

import com.esprit.threadservice.dto.AiAnalysisResponse;
import com.esprit.threadservice.dto.ThreadQualityScore;
import com.esprit.threadservice.exception.ResourceNotFoundException;
import com.esprit.threadservice.model.ForumThread;
import com.esprit.threadservice.repository.ThreadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThreadAiService {

    private final ThreadRepository threadRepository;
    private final AiGenerationService aiGenerationService;

    @Async
    @Transactional
    public void scoreAndSaveThread(Long threadId) {
        try {
            ForumThread thread = threadRepository.findById(threadId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));

            log.info("AI scoring thread #{}: {}", threadId, thread.getTitle());
            ThreadQualityScore score = aiGenerationService.scoreThread(thread.getTitle(), thread.getBody());

            thread.setAiOverallScore(score.getOverall());
            thread.setAiLabel(score.getLabel());
            thread.setAiLabelColor(score.getLabelColor());
            thread.setAiSummary(score.getSummary());
            thread.setAiAnalyzed(true);
            threadRepository.save(thread);
            log.info("AI score saved for thread #{}: {} ({})", threadId, score.getOverall(), score.getLabel());
        } catch (Exception e) {
            log.error("Async AI scoring failed for thread #{}: {}", threadId, e.getMessage());
        }
    }

    public AiAnalysisResponse analyzeThread(String title, String body) {
        try {
            ThreadQualityScore score = aiGenerationService.scoreThread(title, body);
            return AiAnalysisResponse.builder()
                    .score(score)
                    .analysisAvailable(true)
                    .build();
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage());
            return AiAnalysisResponse.builder()
                    .analysisAvailable(false)
                    .build();
        }
    }

    public AiAnalysisResponse getScoreForThread(Long threadId) {
        ForumThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));

        if (!thread.isAiAnalyzed()) {
            return AiAnalysisResponse.builder().analysisAvailable(false).build();
        }

        ThreadQualityScore score = ThreadQualityScore.builder()
                .overall(thread.getAiOverallScore() != null ? thread.getAiOverallScore() : 0)
                .label(thread.getAiLabel())
                .labelColor(thread.getAiLabelColor())
                .summary(thread.getAiSummary())
                .build();

        return AiAnalysisResponse.builder()
                .score(score)
                .analysisAvailable(true)
                .build();
    }
}
