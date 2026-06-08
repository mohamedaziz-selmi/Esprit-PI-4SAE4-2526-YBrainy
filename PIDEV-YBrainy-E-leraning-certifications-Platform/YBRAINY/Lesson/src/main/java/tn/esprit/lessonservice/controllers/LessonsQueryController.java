package tn.esprit.lessonservice.controllers;

import tn.esprit.lessonservice.dto.LessonContentResponseDTO;
import tn.esprit.lessonservice.dto.LessonResponseDTO;
import tn.esprit.lessonservice.entities.Lesson;
import tn.esprit.lessonservice.entities.LessonContent;
import tn.esprit.lessonservice.services.ILessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/courses/lessons")
@RequiredArgsConstructor
public class LessonsQueryController {

    private final ILessonService lessonService;

    @GetMapping
    public ResponseEntity<Page<LessonResponseDTO>> getAllLessons(
            @RequestParam(required = false) Long courseId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        if (courseId == null) {
            return ResponseEntity.ok(Page.empty(pageable));
        }

        List<LessonResponseDTO> all = lessonService.getLessonsByCourse(courseId)
            .stream().map(this::toResponseDTO).toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<LessonResponseDTO> content = start >= all.size()
            ? Collections.emptyList() : all.subList(start, end);

        return ResponseEntity.ok(new PageImpl<>(content, pageable, all.size()));
    }

    private LessonResponseDTO toResponseDTO(Lesson l) {
        List<LessonContentResponseDTO> contents =
            Optional.ofNullable(l.getContents()).orElse(Collections.emptyList())
                .stream()
                .map(c -> LessonContentResponseDTO.builder()
                    .id(c.getId())
                    .type(c.getType())
                    .contentUrl(c.getContentUrl())
                    .orderIndex(c.getOrderIndex())
                    .createdAt(c.getCreatedAt())
                    .build())
                .toList();
        return LessonResponseDTO.builder()
            .id(l.getId())
            .title(l.getTitle())
            .description(l.getDescription())
            .type(l.getType())
            .contentUrl(l.getContentUrl())
            .contents(contents)
            .orderIndex(l.getOrderIndex())
            .durationMinutes(l.getDurationMinutes())
            .courseId(l.getCourseId())
            .createdAt(l.getCreatedAt())
            .updatedAt(l.getUpdatedAt())
            .build();
    }
}
