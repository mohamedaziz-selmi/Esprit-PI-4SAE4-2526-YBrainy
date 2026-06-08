package tn.esprit.quizservice.controllers;

import tn.esprit.quizservice.dto.LeaderboardEntryDTO;
import tn.esprit.quizservice.dto.QuestionDTO;
import tn.esprit.quizservice.dto.QuestionRequestDTO;
import tn.esprit.quizservice.dto.QuizDTO;
import tn.esprit.quizservice.dto.QuizRequestDTO;
import tn.esprit.quizservice.dto.QuizResultDTO;
import tn.esprit.quizservice.dto.QuizSubmissionDTO;
import tn.esprit.quizservice.services.IQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private static final String QUIZ_NOT_FOUND = "Quiz not found";

    private final IQuizService quizService;

    @GetMapping
    public ResponseEntity<List<QuizDTO>> getQuizzes(@RequestParam Long courseId) {
        return ResponseEntity.ok(quizService.getQuizzesByCourse(courseId));
    }

    @PostMapping
    public ResponseEntity<QuizDTO> createQuiz(
            @RequestParam Long courseId,
            @RequestBody @Valid QuizRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quizService.createQuiz(courseId, request));
    }

    @GetMapping("/{quizId}")
    public ResponseEntity<Object> getQuizById(@PathVariable Long quizId) {
        try {
            return ResponseEntity.ok(quizService.getQuizById(null, quizId));
        } catch (RuntimeException e) {
            if (QUIZ_NOT_FOUND.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/{quizId}")
    public ResponseEntity<Void> deleteQuiz(@PathVariable Long quizId) {
        quizService.deleteQuiz(null, quizId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{quizId}/questions")
    public ResponseEntity<List<QuestionDTO>> getQuestions(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.getQuestionsByQuiz(quizId));
    }

    @PostMapping("/{quizId}/questions")
    public ResponseEntity<QuestionDTO> addQuestion(
            @PathVariable Long quizId,
            @RequestBody @Valid QuestionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quizService.addQuestion(quizId, request));
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long quizId,
            @PathVariable Long questionId) {
        quizService.deleteQuestion(quizId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{quizId}")
    public ResponseEntity<Object> updateQuiz(
            @PathVariable Long quizId,
            @RequestBody @Valid QuizRequestDTO dto) {
        try {
            return ResponseEntity.ok(quizService.updateQuiz(quizId, dto));
        } catch (RuntimeException e) {
            if (QUIZ_NOT_FOUND.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/{quizId}/questions/{questionId}")
    public ResponseEntity<Object> updateQuestion(
            @PathVariable Long quizId,
            @PathVariable Long questionId,
            @RequestBody @Valid QuestionRequestDTO dto) {
        try {
            return ResponseEntity.ok(quizService.updateQuestion(questionId, dto));
        } catch (RuntimeException e) {
            if ("Question not found".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{quizId}/submit")
    public ResponseEntity<Object> submitQuiz(
            @PathVariable Long quizId,
            @RequestParam Long studentId,
            @RequestBody QuizSubmissionDTO submission) {
        try {
            submission.setStudentId(studentId);
            QuizResultDTO result = quizService.submitQuiz(quizId, studentId, submission);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            if ("Max attempts reached".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
            }
            if (QUIZ_NOT_FOUND.equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/{quizId}/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.getLeaderboard(quizId));
    }

    @GetMapping("/{quizId}/attempts")
    public ResponseEntity<Map<String, Integer>> getAttemptInfo(
            @PathVariable Long quizId,
            @RequestParam Long studentId) {
        QuizDTO quiz = quizService.getQuizById(null, quizId);
        int attemptsUsed = quizService.countAttempts(quizId, studentId);
        int maxAttempts = quiz.getMaxAttempts();
        return ResponseEntity.ok(Map.of(
                "attemptsUsed", attemptsUsed,
                "attemptsRemaining", Math.max(0, maxAttempts - attemptsUsed),
                "maxAttempts", maxAttempts
        ));
    }

    @GetMapping("/best-score")
    public ResponseEntity<Object> getBestScore(
            @RequestParam Long studentId,
            @RequestParam Long courseId) {
        try {
            Double best = quizService.getBestScoreForStudentAndCourse(studentId, courseId);
            return ResponseEntity.ok(Map.of(
                    "studentId", studentId,
                    "courseId", courseId,
                    "bestScore", best != null ? best : 0.0
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("bestScore", 0.0));
        }
    }

    @GetMapping("/student/{studentId}/avg-score")
    public ResponseEntity<Map<String, Double>> getStudentAvgScore(@PathVariable Long studentId) {
        return ResponseEntity.ok(quizService.getStudentAvgScore(studentId));
    }
}
