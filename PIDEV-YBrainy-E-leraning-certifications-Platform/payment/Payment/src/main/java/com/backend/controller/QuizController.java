package com.backend.controller;

import com.backend.entity.Question;
import com.backend.entity.Quiz;
import com.backend.repository.CertificationRepository;
import com.backend.repository.QuestionRepository;
import com.backend.repository.QuizRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/quizzes")
@CrossOrigin("*")
public class QuizController {

    private final QuizRepository quizRepo;
    private final CertificationRepository certRepo;
    private final QuestionRepository questionRepo;

    public QuizController(QuizRepository quizRepo, CertificationRepository certRepo, QuestionRepository questionRepo) {
        this.quizRepo = quizRepo;
        this.certRepo = certRepo;
        this.questionRepo = questionRepo;
    }

    /* ─── LIST all quizzes ─── */
    @GetMapping
    public List<Quiz> getAll() {
        return quizRepo.findAllByOrderByCreatedAtDesc();
    }

    /* ─── LIST by certification ─── */
    @GetMapping("/certification/{certId}")
    public List<Quiz> getByCertification(@PathVariable Long certId) {
        return quizRepo.findByCertification_IdOrderByCreatedAtDesc(certId);
    }

    /* ─── LIST by status ─── */
    @GetMapping("/status/{status}")
    public List<Quiz> getByStatus(@PathVariable String status) {
        return quizRepo.findByStatusOrderByCreatedAtDesc(status);
    }

    /* ─── STATS ─── */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", quizRepo.count());
        stats.put("published", quizRepo.countByStatus("published"));
        stats.put("draft", quizRepo.countByStatus("draft"));
        stats.put("archived", quizRepo.countByStatus("archived"));
        return stats;
    }

    /* ─── GET single quiz (with questions) ─── */
    @GetMapping("/{id}")
    public ResponseEntity<Quiz> getById(@PathVariable Long id) {
        return quizRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ─── CREATE quiz ─── */
    @PostMapping
    public ResponseEntity<Quiz> create(@RequestBody Map<String, Object> body) {
        Quiz quiz = Quiz.builder()
                .title((String) body.get("title"))
                .description((String) body.get("description"))
                .difficulty((String) body.getOrDefault("difficulty", "medium"))
                .status((String) body.getOrDefault("status", "draft"))
                .build();

        if (body.get("timeLimit") != null)
            quiz.setTimeLimit(Integer.valueOf(body.get("timeLimit").toString()));
        if (body.get("passingScore") != null)
            quiz.setPassingScore(Integer.valueOf(body.get("passingScore").toString()));
        if (body.get("maxAttempts") != null)
            quiz.setMaxAttempts(Integer.valueOf(body.get("maxAttempts").toString()));

        if (body.get("certificationId") != null) {
            Long certId = Long.valueOf(body.get("certificationId").toString());
            certRepo.findById(certId).ifPresent(quiz::setCertification);
        }

        return ResponseEntity.ok(quizRepo.save(quiz));
    }

    /* ─── UPDATE quiz ─── */
    @PutMapping("/{id}")
    public ResponseEntity<Quiz> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return quizRepo.findById(id).map(quiz -> {
            if (body.containsKey("title")) quiz.setTitle((String) body.get("title"));
            if (body.containsKey("description")) quiz.setDescription((String) body.get("description"));
            if (body.containsKey("difficulty")) quiz.setDifficulty((String) body.get("difficulty"));
            if (body.containsKey("status")) quiz.setStatus((String) body.get("status"));
            if (body.containsKey("timeLimit"))
                quiz.setTimeLimit(Integer.valueOf(body.get("timeLimit").toString()));
            if (body.containsKey("passingScore"))
                quiz.setPassingScore(Integer.valueOf(body.get("passingScore").toString()));
            if (body.containsKey("maxAttempts"))
                quiz.setMaxAttempts(Integer.valueOf(body.get("maxAttempts").toString()));
            if (body.containsKey("certificationId")) {
                if (body.get("certificationId") != null) {
                    Long certId = Long.valueOf(body.get("certificationId").toString());
                    certRepo.findById(certId).ifPresent(quiz::setCertification);
                } else {
                    quiz.setCertification(null);
                }
            }
            return ResponseEntity.ok(quizRepo.save(quiz));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── DELETE quiz ─── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!quizRepo.existsById(id)) return ResponseEntity.notFound().build();
        quizRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ═══════════════════════════════════════════════
     *   QUESTIONS endpoints (nested under /api/quizzes)
     * ═══════════════════════════════════════════════ */

    /* ─── LIST questions for a quiz ─── */
    @GetMapping("/{quizId}/questions")
    public ResponseEntity<List<Question>> getQuestions(@PathVariable Long quizId) {
        if (!quizRepo.existsById(quizId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(questionRepo.findByQuiz_IdOrderByOrderIndexAsc(quizId));
    }

    /* ─── ADD question to quiz ─── */
    @PostMapping("/{quizId}/questions")
    public ResponseEntity<Question> addQuestion(@PathVariable Long quizId, @RequestBody Map<String, Object> body) {
        return quizRepo.findById(quizId).map(quiz -> {
            int nextOrder = questionRepo.countByQuiz_Id(quizId);
            Question q = Question.builder()
                    .questionText((String) body.get("questionText"))
                    .optionA((String) body.get("optionA"))
                    .optionB((String) body.get("optionB"))
                    .optionC((String) body.get("optionC"))
                    .optionD((String) body.get("optionD"))
                    .correctAnswer((String) body.get("correctAnswer"))
                    .questionType((String) body.getOrDefault("questionType", "multiple_choice"))
                    .orderIndex(nextOrder)
                    .quiz(quiz)
                    .build();
            if (body.get("points") != null)
                q.setPoints(Integer.valueOf(body.get("points").toString()));
            return ResponseEntity.ok(questionRepo.save(q));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── UPDATE question ─── */
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<Question> updateQuestion(@PathVariable Long questionId, @RequestBody Map<String, Object> body) {
        return questionRepo.findById(questionId).map(q -> {
            if (body.containsKey("questionText")) q.setQuestionText((String) body.get("questionText"));
            if (body.containsKey("optionA")) q.setOptionA((String) body.get("optionA"));
            if (body.containsKey("optionB")) q.setOptionB((String) body.get("optionB"));
            if (body.containsKey("optionC")) q.setOptionC((String) body.get("optionC"));
            if (body.containsKey("optionD")) q.setOptionD((String) body.get("optionD"));
            if (body.containsKey("correctAnswer")) q.setCorrectAnswer((String) body.get("correctAnswer"));
            if (body.containsKey("questionType")) q.setQuestionType((String) body.get("questionType"));
            if (body.containsKey("points"))
                q.setPoints(Integer.valueOf(body.get("points").toString()));
            return ResponseEntity.ok(questionRepo.save(q));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ─── DELETE question ─── */
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        if (!questionRepo.existsById(questionId)) return ResponseEntity.notFound().build();
        questionRepo.deleteById(questionId);
        return ResponseEntity.noContent().build();
    }
}

