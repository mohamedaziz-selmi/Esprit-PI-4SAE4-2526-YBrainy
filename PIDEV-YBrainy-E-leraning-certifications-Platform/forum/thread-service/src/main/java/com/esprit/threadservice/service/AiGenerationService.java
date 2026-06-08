package com.esprit.threadservice.service;

import com.esprit.threadservice.dto.AiChatRequest;
import com.esprit.threadservice.dto.AiChatResponse;
import com.esprit.threadservice.dto.ThreadQualityScore;
import com.esprit.threadservice.model.ForumThread;
import com.esprit.threadservice.repository.ThreadRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiGenerationService {

    private static final String THREAD_QUESTION_PROMPT =
            "You are an expert forum question writer. Given a thread title, write a clear, " +
            "detailed, and well-structured forum question. Your question should: provide context " +
            "about the problem or topic, explain what the person has already tried or what they know, " +
            "and be specific about what kind of help or information they need. " +
            "Use a natural, conversational tone. " +
            "Structure your response in 2-3 paragraphs. Do not include a subject line — " +
            "write only the question body. Do not use any markdown formatting like ** or ##.";

    private static final String POST_ANSWER_PROMPT =
            "You are an expert forum responder. Given a forum thread title and its question body, " +
            "write a helpful, detailed, and accurate answer. Your response should directly address " +
            "the question, provide clear explanations and practical examples where relevant, " +
            "and be structured logically. " +
            "Structure your response in 2-4 paragraphs. " +
            "Do not use any markdown formatting like ** or ##.";

    private static final String FORUM_ASSISTANT_BASE_PROMPT =
            "You are yForumy, a smart and precise AI assistant for the YBrainy tech forum.\n" +
            "You always respond in the same language as the user (French or English).\n\n" +

            "YOUR JOB:\n" +
            "1. Show the user any related threads found in the database (already provided as context below)\n" +
            "2. ALWAYS offer to help create a new forum post — even when similar threads exist\n" +
            "3. Before generating a draft, gather specific details by asking targeted questions\n\n" +

            "CONVERSATION FLOW (follow these steps in order):\n" +
            "STEP 1 — If related threads exist, show them. Then say something like: 'I found similar discussions above. " +
            "Would you like me to help you create a precise post about your specific problem?'\n" +
            "STEP 2 — Ask 2-3 SPECIFIC technical questions to understand the exact problem. Examples:\n" +
            "  - 'What is the EXACT error message or stack trace you are getting?'\n" +
            "  - 'What version of [technology] are you using? What OS?'\n" +
            "  - 'What have you already tried?'\n" +
            "  - 'What is your exact configuration (paste relevant code/config if possible)?'\n" +
            "  Adapt the questions to the user's specific topic — do NOT ask generic questions.\n" +
            "STEP 3 — Ask: 'Do you have any screenshots, error logs, or files to attach? " +
            "If yes, please use the attachment button below to add them before I generate the post.'\n" +
            "STEP 4 — Once you have answers (and user confirms files or says no files), generate the draft with " +
            "EXACTLY this format at the very END of your response:\n" +
            "    DRAFT_TITLE: <specific title including the exact error/technology/version>\n" +
            "    DRAFT_BODY: <complete, detailed, specific post body — NOT generic. Include: " +
            "exact error messages, environment details, what was tried, code snippets if relevant, " +
            "what specific help is needed. Structure: context paragraph, problem details, what was tried, question.>\n\n" +

            "IMPORTANT RULES:\n" +
            "- The draft must reflect the USER'S SPECIFIC PROBLEM, not a generic template\n" +
            "- If user says 'generate now', 'no files', 'just create it' or similar, go directly to generation\n" +
            "- If user explicitly provides all details upfront, skip steps 2-3 and generate immediately\n" +
            "- Never use markdown ** bold or _ italic\n" +
            "- Keep messages concise — do not repeat yourself\n\n";

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the","and","for","are","but","not","you","all","can","her","was","one","our","out",
            "day","get","has","him","his","how","its","now","old","see","two","way","who","did",
            "had","let","put","say","she","too","use","with","that","this","have","from","they",
            "will","been","into","than","then","when","what","some","like","just","also","more",
            "very","your","which","about","there","would","could","their","where","after","other",
            "dans","les","des","une","qui","que","est","par","sur","avec","pour","pas","mais","est",
            "son","tout","cette","plus","comme","bien","peut","faire","nous","vous","leur","moi","toi",
            "ils","elle","elles","dont","quand","donc","sinon","parce","comment","pourquoi","quoi"
    ));

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model:llama-3.1-8b-instant}")
    private String model;

    @Value("${ai.api-base-url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiBaseUrl;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ThreadRepository threadRepository;

    public AiGenerationService() {
        this.restClient = RestClient.builder().build();
    }

    private static final String THREAD_SUMMARIZE_PROMPT =
            "You are an expert forum discussion analyst. Given a forum thread title, its original question, " +
            "and all the answers/posts, write a concise summary (4-6 sentences) that covers:\n" +
            "1. What the original problem or question was\n" +
            "2. The main solutions or answers proposed\n" +
            "3. The conclusion or best approach recommended\n\n" +
            "Rules:\n" +
            "- Write in the same language as the thread (French or English)\n" +
            "- Be direct and actionable — someone reading this should understand what to do without reading all posts\n" +
            "- Highlight if there is a best answer marked\n" +
            "- Do not use markdown formatting\n" +
            "- Do not start with 'Summary:' or any label\n" +
            "- Return only the summary paragraph, nothing else";

    private static final String THREAD_SCORE_PROMPT =
            "You are a forum content quality evaluator. Analyze the given forum thread and return a JSON object (no markdown, no code block, raw JSON only) with this exact structure:\n" +
            "{\n" +
            "  \"clarity\": <int 1-10>,\n" +
            "  \"detail\": <int 1-10>,\n" +
            "  \"usefulness\": <int 1-10>,\n" +
            "  \"overall\": <int 1-10>,\n" +
            "  \"label\": <\"Excellent\"|\"Bon\"|\"Moyen\"|\"Faible\">,\n" +
            "  \"labelColor\": <\"#22c55e\" for Excellent, \"#3b82f6\" for Bon, \"#f59e0b\" for Moyen, \"#ef4444\" for Faible>,\n" +
            "  \"summary\": <one sentence summary of the thread in French>,\n" +
            "  \"strengths\": [<up to 3 short strengths in French>],\n" +
            "  \"improvements\": [<up to 3 short improvement suggestions in French>],\n" +
            "  \"improvedVersion\": <rewritten thread body, improved, in the same language as the original>\n" +
            "}\n" +
            "Rules: overall = average of clarity+detail+usefulness. " +
            "Excellent = overall>=8, Bon = overall>=6, Moyen = overall>=4, Faible = overall<4. " +
            "Return ONLY raw JSON, no extra text.";

    public ThreadQualityScore scoreThread(String title, String body) {
        String plainBody = body != null ? body.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim() : "";
        String userMessage = "Thread title: \"" + title + "\"\n\nThread body:\n" + plainBody;
        // Use 1400 tokens — enough for full JSON including improvedVersion
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", THREAD_SCORE_PROMPT),
                Map.of("role", "user", "content", userMessage)
        );
        String raw = callGroqMessages(messages, 1400);
        // Strip markdown code fences if model wraps response
        String json = raw.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        try {
            return objectMapper.readValue(json, ThreadQualityScore.class);
        } catch (Exception e) {
            log.warn("Failed to parse AI score JSON: {}. Raw: {}", e.getMessage(), json);
            // Return a fallback so async task doesn't crash
            return ThreadQualityScore.builder()
                    .clarity(5).detail(5).usefulness(5).overall(5)
                    .label("Moyen").labelColor("#f59e0b")
                    .summary("Analyse non disponible.")
                    .strengths(java.util.Collections.emptyList())
                    .improvements(java.util.Collections.emptyList())
                    .improvedVersion("")
                    .build();
        }
    }

    public String generateThreadBody(String title) {
        return callGroq(
                THREAD_QUESTION_PROMPT,
                "Write a forum question body for this thread title: \"" + title + "\""
        );
    }

    public String generatePostBody(String threadTitle, String threadBody) {
        String userMessage = "Thread title: \"" + threadTitle + "\"\n\n" +
                "Thread question:\n" + threadBody + "\n\n" +
                "Write a helpful answer to this forum question.";
        return callGroq(POST_ANSWER_PROMPT, userMessage);
    }

    public AiChatResponse chat(AiChatRequest request) {
        String message = request.getMessage();
        List<Map<String, String>> history = request.getHistory();

        List<String> keywords = extractKeywords(message);

        List<ForumThread> found = new ArrayList<>();
        for (String kw : keywords) {
            if (found.size() >= 5) break;
            List<ForumThread> results = threadRepository.searchByKeyword(kw, PageRequest.of(0, 3));
            for (ForumThread t : results) {
                if (found.stream().noneMatch(x -> x.getId().equals(t.getId()))) {
                    found.add(t);
                    if (found.size() >= 5) break;
                }
            }
        }

        StringBuilder ctx = new StringBuilder();
        if (!found.isEmpty()) {
            ctx.append("RELATED FORUM THREADS FROM DATABASE:\n");
            for (ForumThread t : found) {
                String body = t.getBody() != null
                        ? t.getBody().replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim()
                        : "";
                if (body.length() > 200) body = body.substring(0, 200) + "...";
                ctx.append("- [Thread #").append(t.getId()).append("] ").append(t.getTitle()).append("\n");
                if (!body.isBlank()) ctx.append("  ").append(body).append("\n");
            }
        } else {
            ctx.append("No related threads found in the database for this query.\n");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", FORUM_ASSISTANT_BASE_PROMPT + ctx));

        if (history != null) {
            for (Map<String, String> h : history) {
                String role = h.getOrDefault("role", "user");
                String content = h.getOrDefault("content", "");
                messages.add(Map.of("role", role, "content", content));
            }
        }
        messages.add(Map.of("role", "user", "content", message));

        String rawReply = callGroqMessages(messages, 900);

        String reply = rawReply;
        String draftTitle = null;
        String draftBody = null;
        boolean hasDraft = false;

        if (rawReply.contains("DRAFT_TITLE:") && rawReply.contains("DRAFT_BODY:")) {
            int titleMarker = rawReply.indexOf("DRAFT_TITLE:");
            int bodyMarker  = rawReply.indexOf("DRAFT_BODY:");
            draftTitle = rawReply.substring(titleMarker + "DRAFT_TITLE:".length(), bodyMarker).trim();
            draftBody  = rawReply.substring(bodyMarker + "DRAFT_BODY:".length()).trim();
            reply = rawReply.substring(0, titleMarker).trim();
            if (reply.isBlank()) reply = "Here is the draft post I generated for you:";
            hasDraft = true;
        }

        List<AiChatResponse.ThreadSummary> summaries = found.stream()
                .map(t -> new AiChatResponse.ThreadSummary(t.getId(), t.getTitle()))
                .collect(Collectors.toList());

        return AiChatResponse.builder()
                .reply(reply)
                .relatedThreads(summaries)
                .hasDraft(hasDraft)
                .draftTitle(draftTitle)
                .draftBody(draftBody)
                .build();
    }

    public String summarizeThread(String threadTitle, String threadBody,
                                  List<com.esprit.threadservice.feign.PostFeignClient.PostSummary> posts) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append("Thread title: \"").append(threadTitle).append("\"\n\n");

        String plainBody = threadBody != null
                ? threadBody.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim()
                : "";
        if (!plainBody.isBlank()) {
            if (plainBody.length() > 500) plainBody = plainBody.substring(0, 500) + "...";
            userMessage.append("Original question:\n").append(plainBody).append("\n\n");
        }

        userMessage.append("Answers (").append(posts.size()).append(" total):\n");
        for (int i = 0; i < posts.size(); i++) {
            var post = posts.get(i);
            String author = post.getAuthor() != null ? post.getAuthor().getUsername() : "User";
            String body = post.getBody() != null
                    ? post.getBody().replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim()
                    : "";
            if (body.length() > 400) body = body.substring(0, 400) + "...";
            userMessage.append("\nAnswer ").append(i + 1)
                    .append(post.isBestAnswer() ? " [BEST ANSWER]" : "")
                    .append(" by ").append(author).append(":\n")
                    .append(body).append("\n");
        }

        return callGroq(THREAD_SUMMARIZE_PROMPT, userMessage.toString());
    }

    private List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        return Arrays.stream(text.toLowerCase().split("[\\s\\p{Punct}]+"))
                .filter(w -> w.length() > 3)
                .filter(w -> !STOP_WORDS.contains(w))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private String callGroq(String systemPrompt, String userMessage) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        );
        return callGroqMessages(messages, 600);
    }

    private String callGroqMessages(List<Map<String, Object>> messages, int maxTokens) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", 0.7);

        ChatResponse response = restClient.post()
                .uri(apiBaseUrl)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new RuntimeException("No response from Groq API");
        }

        String text = response.getChoices().get(0).getMessage().getContent();
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Groq returned empty content");
        }
        return text.trim();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        private List<Choice> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;
    }
}
