package tn.esprit.tpfoyer.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tn.esprit.tpfoyer.Dto.LearningPathCourseDTO;
import tn.esprit.tpfoyer.Dto.LearningPathDTO;
import tn.esprit.tpfoyer.Dto.SaveLearningPathRequestDTO;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.LearningPath;
import tn.esprit.tpfoyer.Repositories.CourseRepository;
import tn.esprit.tpfoyer.Repositories.LearningPathRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LearningPathServiceImpl implements ILearningPathService {

    private final CourseRepository courseRepository;
    private final LearningPathRepository learningPathRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${openrouter.api.key}")
    private String openRouterKey;

    @Override
    public LearningPathDTO generate(Long studentId, String goal) {
        // STEP A — Load all courses
        List<Course> allCourses = courseRepository.findAll();

        // STEP B — Build course catalog string
        StringBuilder catalog = new StringBuilder();
        for (Course c : allCourses) {
            catalog.append("ID:").append(c.getId())
                   .append("|TITLE:").append(c.getTitle())
                   .append("|CATEGORY:").append(c.getCategory())
                   .append("|LEVEL:").append(c.getLevel())
                   .append("|DURATION:").append(c.getApproximateDurationMinutes())
                   .append("|PRICE:").append(c.getPrice())
                   .append("|DESC:").append(
                       c.getDescription() != null
                           ? c.getDescription().substring(0, Math.min(100, c.getDescription().length()))
                           : "")
                   .append("\n");
        }
        System.out.println("LEARNING PATH: Catalog built with " + allCourses.size() + " courses");

        // STEP C — Build AI prompt
        String prompt = """
                You are a learning advisor for an e-learning platform.
                A student has this learning goal: "%s"

                Here is the complete course catalog:
                %s

                Select the most relevant courses to help the student achieve their goal.
                Choose between 2 and 5 courses maximum.
                Order them logically from foundational to advanced.

                Respond ONLY with a valid JSON object, no markdown, no explanation:
                {
                  "title": "A compelling name for this learning path",
                  "description": "2-3 sentences describing what the student will achieve",
                  "selectedCourses": [
                    {
                      "courseId": 123,
                      "orderIndex": 1,
                      "whyIncluded": "One sentence explaining why this course is in the path"
                    }
                  ]
                }
                """.formatted(goal, catalog.toString());

        // STEP D — Call OpenRouter
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openRouterKey);
            headers.set("HTTP-Referer", "http://localhost:4301");
            headers.set("X-Title", "YBrainy");

            Map<String, Object> body = Map.of(
                "model", "google/gemma-3-4b-it:free",
                "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://openrouter.ai/api/v1/chat/completions",
                request,
                String.class
            );

            String responseBody = response.getBody();
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            System.out.println("LEARNING PATH: AI raw response: " + content);

            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();
            }

            // STEP E — Parse AI response and build DTO
            JsonNode aiResult = objectMapper.readTree(content);
            String title = aiResult.path("title").asText("Learning Path");
            String description = aiResult.path("description").asText("");
            JsonNode selectedCourses = aiResult.path("selectedCourses");

            Map<Long, Course> courseMap = allCourses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

            List<LearningPathCourseDTO> courseDTOs = new ArrayList<>();
            BigDecimal totalPrice = BigDecimal.ZERO;
            int totalDuration = 0;

            for (JsonNode sc : selectedCourses) {
                Long courseId = sc.path("courseId").asLong();
                int orderIndex = sc.path("orderIndex").asInt(courseDTOs.size() + 1);
                String whyIncluded = sc.path("whyIncluded").asText("");

                Course course = courseMap.get(courseId);
                if (course == null) continue;

                courseDTOs.add(LearningPathCourseDTO.builder()
                    .id(course.getId())
                    .title(course.getTitle())
                    .description(course.getDescription())
                    .thumbnailUrl(course.getThumbnailUrl())
                    .price(course.getPrice())
                    .level(course.getLevel() != null ? course.getLevel().name() : null)
                    .category(course.getCategory() != null ? course.getCategory().name() : null)
                    .approximateDurationMinutes(course.getApproximateDurationMinutes())
                    .orderIndex(orderIndex)
                    .whyIncluded(whyIncluded)
                    .build());

                if (course.getPrice() != null) {
                    totalPrice = totalPrice.add(course.getPrice());
                }
                if (course.getApproximateDurationMinutes() != null) {
                    totalDuration += course.getApproximateDurationMinutes();
                }
            }

            // Build whyIncluded JSON map
            Map<String, String> whyMap = new HashMap<>();
            for (LearningPathCourseDTO dto : courseDTOs) {
                whyMap.put(String.valueOf(dto.getId()), dto.getWhyIncluded());
            }
            String whyIncludedJson;
            try {
                whyIncludedJson = objectMapper.writeValueAsString(whyMap);
            } catch (Exception ex) {
                whyIncludedJson = "{}";
            }

            String courseIdsStr = courseDTOs.stream()
                .map(dto -> String.valueOf(dto.getId()))
                .collect(Collectors.joining(","));

            LearningPath entity = LearningPath.builder()
                .studentId(studentId)
                .goal(goal)
                .generatedTitle(title)
                .generatedDescription(description)
                .courseIds(courseIdsStr)
                .whyIncluded(whyIncludedJson)
                .totalPrice(totalPrice)
                .totalDurationMinutes(totalDuration)
                .saved(true)
                .build();

            LearningPath saved = learningPathRepository.save(entity);

            return LearningPathDTO.builder()
                .id(saved.getId())
                .studentId(studentId)
                .goal(goal)
                .generatedTitle(title)
                .generatedDescription(description)
                .courses(courseDTOs)
                .totalPrice(totalPrice)
                .totalDurationMinutes(totalDuration)
                .saved(true)
                .createdAt(saved.getCreatedAt())
                .build();

        } catch (Exception e) {
            System.out.println("LEARNING PATH ERROR: " + e.getMessage());
            e.printStackTrace();
            log.error("Learning path generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate learning path: " + e.getMessage());
        }
    }

    @Override
    public LearningPathDTO save(SaveLearningPathRequestDTO request) {
        String courseIdsStr = request.getCourseIds().stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));

        LearningPath entity = LearningPath.builder()
            .studentId(request.getStudentId())
            .goal(request.getGoal())
            .generatedTitle(request.getGeneratedTitle())
            .generatedDescription(request.getGeneratedDescription())
            .courseIds(courseIdsStr)
            .whyIncluded(request.getWhyIncludedJson())
            .totalPrice(request.getTotalPrice())
            .totalDurationMinutes(request.getTotalDurationMinutes())
            .saved(true)
            .build();

        LearningPath saved = learningPathRepository.save(entity);
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningPathDTO> getSavedPaths(Long studentId) {
        return learningPathRepository.findByStudentIdAndSavedTrue(studentId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Override
    public void deletePath(Long pathId) {
        learningPathRepository.deleteById(pathId);
    }

    private String parseWhyIncluded(String json, Long courseId) {
        if (json == null || json.isBlank()) return "";
        try {
            Map<String, String> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, String>>() {});
            return map.getOrDefault(String.valueOf(courseId), "");
        } catch (Exception e) {
            return "";
        }
    }

    private LearningPathDTO toDTO(LearningPath path) {
        List<LearningPathCourseDTO> courseDTOs = new ArrayList<>();

        if (path.getCourseIds() != null && !path.getCourseIds().isBlank()) {
            List<Long> ids = Arrays.stream(path.getCourseIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

            List<Course> courses = courseRepository.findAllById(ids);
            Map<Long, Course> courseMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

            for (int i = 0; i < ids.size(); i++) {
                Course course = courseMap.get(ids.get(i));
                if (course == null) continue;
                courseDTOs.add(LearningPathCourseDTO.builder()
                    .id(course.getId())
                    .title(course.getTitle())
                    .description(course.getDescription())
                    .thumbnailUrl(course.getThumbnailUrl())
                    .price(course.getPrice())
                    .level(course.getLevel() != null ? course.getLevel().name() : null)
                    .category(course.getCategory() != null ? course.getCategory().name() : null)
                    .approximateDurationMinutes(course.getApproximateDurationMinutes())
                    .orderIndex(i + 1)
                    .whyIncluded(parseWhyIncluded(path.getWhyIncluded(), course.getId()))
                    .build());
            }
        }

        return LearningPathDTO.builder()
            .id(path.getId())
            .studentId(path.getStudentId())
            .goal(path.getGoal())
            .generatedTitle(path.getGeneratedTitle())
            .generatedDescription(path.getGeneratedDescription())
            .courses(courseDTOs)
            .totalPrice(path.getTotalPrice())
            .totalDurationMinutes(path.getTotalDurationMinutes())
            .saved(path.getSaved())
            .createdAt(path.getCreatedAt())
            .build();
    }
}
