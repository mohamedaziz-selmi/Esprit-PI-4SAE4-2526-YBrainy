package com.backend.controller;

import com.backend.entity.Meet;
import com.backend.repository.CourseRepository;
import com.backend.repository.MeetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/meets")
@CrossOrigin("*")
public class MeetController {

    private final MeetRepository meetRepo;
    private final CourseRepository courseRepo;

    public MeetController(MeetRepository meetRepo, CourseRepository courseRepo) {
        this.meetRepo = meetRepo;
        this.courseRepo = courseRepo;
    }

    /* ─── LIST all ─── */
    @GetMapping
    public List<Meet> getAll() {
        return meetRepo.findAllByOrderByStartTimeAsc();
    }

    /* ─── GET single ─── */
    @GetMapping("/{id}")
    public ResponseEntity<Meet> getById(@PathVariable Long id) {
        return meetRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ─── LIST by course ─── */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<Meet>> getByCourse(@PathVariable Long courseId) {
        if (!courseRepo.existsById(courseId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(meetRepo.findByCourse_Id(courseId));
    }

    /* ─── CREATE ─── */
    @PostMapping
    public ResponseEntity<Meet> create(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String description = (String) body.getOrDefault("description", "");
        String meetLink = (String) body.get("meetLink");
        String startTimeStr = (String) body.get("startTime");
        String endTimeStr = (String) body.getOrDefault("endTime", null);
        String color = (String) body.getOrDefault("color", "bg-primary");
        Object courseIdObj = body.getOrDefault("courseId", null);

        if (title == null || meetLink == null || startTimeStr == null) {
            return ResponseEntity.badRequest().build();
        }

        Meet meet = Meet.builder()
                .title(title)
                .description(description)
                .meetLink(meetLink)
                .startTime(LocalDateTime.parse(startTimeStr))
                .endTime(endTimeStr != null ? LocalDateTime.parse(endTimeStr) : null)
                .color(color)
                .build();

        if (courseIdObj != null) {
            Long courseId = Long.valueOf(courseIdObj.toString());
            courseRepo.findById(courseId).ifPresent(meet::setCourse);
        }

        return ResponseEntity.ok(meetRepo.save(meet));
    }

    /* ─── UPDATE ─── */
    @PutMapping("/{id}")
    public ResponseEntity<Meet> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return meetRepo.findById(id).map(meet -> {
            if (body.containsKey("title")) meet.setTitle((String) body.get("title"));
            if (body.containsKey("description")) meet.setDescription((String) body.get("description"));
            if (body.containsKey("meetLink")) meet.setMeetLink((String) body.get("meetLink"));
            if (body.containsKey("startTime")) meet.setStartTime(LocalDateTime.parse((String) body.get("startTime")));
            if (body.containsKey("endTime")) {
                String endStr = (String) body.get("endTime");
                meet.setEndTime(endStr != null ? LocalDateTime.parse(endStr) : null);
            }
            if (body.containsKey("color")) meet.setColor((String) body.get("color"));
            if (body.containsKey("courseId")) {
                Object courseIdObj = body.get("courseId");
                if (courseIdObj != null) {
                    Long courseId = Long.valueOf(courseIdObj.toString());
                    courseRepo.findById(courseId).ifPresent(meet::setCourse);
                } else {
                    meet.setCourse(null);
                }
            }
            return ResponseEntity.ok(meetRepo.save(meet));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── DELETE ─── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!meetRepo.existsById(id)) return ResponseEntity.notFound().build();
        meetRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

