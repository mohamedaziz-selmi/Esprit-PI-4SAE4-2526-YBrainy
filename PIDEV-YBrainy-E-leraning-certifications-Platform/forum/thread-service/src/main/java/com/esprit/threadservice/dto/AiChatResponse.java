package com.esprit.threadservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String reply;
    private List<ThreadSummary> relatedThreads;
    private boolean hasDraft;
    private String draftTitle;
    private String draftBody;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ThreadSummary {
        private Long id;
        private String title;
    }
}
