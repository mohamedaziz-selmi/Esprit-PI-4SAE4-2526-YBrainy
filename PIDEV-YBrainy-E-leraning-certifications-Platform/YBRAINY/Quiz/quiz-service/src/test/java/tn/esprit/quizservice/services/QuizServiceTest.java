package tn.esprit.quizservice.services;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.quizservice.entities.*;
import tn.esprit.quizservice.repositories.*;
import tn.esprit.quizservice.clients.*;
import tn.esprit.quizservice.dto.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock QuizRepository quizRepository;
    @Mock QuestionRepository questionRepository;
    @Mock QuestionOptionRepository optionRepository;
    @Mock QuizAttemptRepository attemptRepository;
    @Mock AttemptAnswerRepository answerRepository;
    @Mock CourseClient courseClient;
    @Mock EnrollmentClient enrollmentClient;
    @Mock UserClient userClient;
    @InjectMocks QuizServiceImpl service;

    private Quiz sampleQuiz() {
        Quiz q = new Quiz();
        q.setId(1L);
        q.setCourseId(100L);
        q.setTitle("Test Quiz");
        q.setPassingScore(70);
        q.setMaxAttempts(3);
        q.setTimeLimitMinutes(30);
        q.setIsActive(true);
        return q;
    }

    private Question sampleQuestion(Long id, Long quizId) {
        return Question.builder()
            .id(id).quizId(quizId)
            .questionText("What is a JVM?").orderIndex(0).build();
    }

    private QuestionOption correctOption(Long id, Long questionId) {
        return QuestionOption.builder()
            .id(id).questionId(questionId)
            .optionText("Java Virtual Machine").isCorrect(true).orderIndex(0).build();
    }

    private QuestionOption wrongOption(Long id, Long questionId) {
        return QuestionOption.builder()
            .id(id).questionId(questionId)
            .optionText("Wrong Answer").isCorrect(false).orderIndex(1).build();
    }

    // ── getQuizzesByCourse ────────────────────────────────────────────

    @Test
    @DisplayName("getQuizzesByCourse returns quizzes for the course")
    void getQuizzesByCourse_returnsList() {
        when(quizRepository.findByCourseIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(sampleQuiz()));
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

        var result = service.getQuizzesByCourse(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCourseId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getQuizzesByCourse returns empty list when no quizzes")
    void getQuizzesByCourse_noQuizzes_returnsEmpty() {
        when(quizRepository.findByCourseIdOrderByCreatedAtAsc(100L)).thenReturn(Collections.emptyList());

        assertThat(service.getQuizzesByCourse(100L)).isEmpty();
    }

    @Test
    @DisplayName("getQuizzesByCourse includes questionCount in DTO")
    void getQuizzesByCourse_includesQuestionCount() {
        when(quizRepository.findByCourseIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(sampleQuiz()));
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L))
            .thenReturn(List.of(sampleQuestion(1L, 1L), sampleQuestion(2L, 1L)));

        var result = service.getQuizzesByCourse(100L);

        assertThat(result.get(0).getQuestionCount()).isEqualTo(2);
    }

    // ── getQuizById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getQuizById returns DTO when quiz found")
    void getQuizById_found_returnsDTO() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(sampleQuiz()));
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(Collections.emptyList());

        QuizDTO result = service.getQuizById(100L, 1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Test Quiz");
    }

    @Test
    @DisplayName("getQuizById throws when quiz not found")
    void getQuizById_notFound_throws() {
        when(quizRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuizById(100L, 99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }

    // ── createQuiz ────────────────────────────────────────────────────

    @Test
    @DisplayName("createQuiz validates course exists via Feign")
    void createQuiz_invalidCourse_throws() {
        when(courseClient.courseExists(999L)).thenReturn(false);

        var request = new QuizRequestDTO();
        request.setTitle("Bad Quiz");

        assertThatThrownBy(() -> service.createQuiz(999L, request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("createQuiz uses default passing score of 70 when not specified")
    void createQuiz_defaultPassingScore_whenNull() {
        when(courseClient.courseExists(100L)).thenReturn(true);
        when(quizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new QuizRequestDTO();
        request.setTitle("Default Quiz");

        QuizDTO result = service.createQuiz(100L, request);

        assertThat(result.getPassingScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("createQuiz uses default maxAttempts of 3 when not specified")
    void createQuiz_defaultMaxAttempts_whenNull() {
        when(courseClient.courseExists(100L)).thenReturn(true);
        when(quizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new QuizRequestDTO();
        request.setTitle("Default Quiz");

        QuizDTO result = service.createQuiz(100L, request);

        assertThat(result.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("createQuiz saves quiz with provided settings")
    void createQuiz_customSettings_savedCorrectly() {
        when(courseClient.courseExists(100L)).thenReturn(true);
        when(quizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new QuizRequestDTO();
        request.setTitle("Advanced Quiz");
        request.setPassingScore(85);
        request.setMaxAttempts(2);
        request.setTimeLimitMinutes(45);

        QuizDTO result = service.createQuiz(100L, request);

        assertThat(result.getTitle()).isEqualTo("Advanced Quiz");
        assertThat(result.getPassingScore()).isEqualTo(85);
        assertThat(result.getMaxAttempts()).isEqualTo(2);
    }

    // ── deleteQuiz ────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteQuiz deletes options, questions, then quiz in order")
    void deleteQuiz_deletesAll() {
        Question q = sampleQuestion(5L, 1L);
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(q));

        service.deleteQuiz(100L, 1L);

        InOrder order = inOrder(optionRepository, questionRepository, quizRepository);
        order.verify(optionRepository).deleteByQuestionId(5L);
        order.verify(questionRepository).deleteByQuizId(1L);
        order.verify(quizRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteQuiz with no questions still deletes quiz")
    void deleteQuiz_noQuestions_deletesQuiz() {
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(Collections.emptyList());

        service.deleteQuiz(100L, 1L);

        verify(quizRepository).deleteById(1L);
        verify(optionRepository, never()).deleteByQuestionId(any());
    }

    // ── getQuestionsByQuiz ────────────────────────────────────────────

    @Test
    @DisplayName("getQuestionsByQuiz returns questions with their options")
    void getQuestionsByQuiz_returnsMappedDTOs() {
        Question q = sampleQuestion(1L, 1L);
        QuestionOption opt = correctOption(1L, 1L);
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(q));
        when(optionRepository.findByQuestionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(opt));

        List<QuestionDTO> result = service.getQuestionsByQuiz(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOptions()).hasSize(1);
        assertThat(result.get(0).getOptions().get(0).getIsCorrect()).isTrue();
    }

    // ── addQuestion ───────────────────────────────────────────────────

    @Test
    @DisplayName("addQuestion creates question with correct options and order index")
    void addQuestion_createsQuestionAndOptions() {
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(Collections.emptyList());
        when(questionRepository.save(any())).thenAnswer(i -> {
            Question q = i.getArgument(0);
            q.setId(10L);
            return q;
        });

        var opt1 = new QuestionRequestDTO.OptionRequest();
        opt1.setOptionText("JVM");
        opt1.setIsCorrect(true);
        var opt2 = new QuestionRequestDTO.OptionRequest();
        opt2.setOptionText("Wrong");
        opt2.setIsCorrect(false);

        var req = new QuestionRequestDTO();
        req.setQuestionText("What is JVM?");
        req.setOptions(List.of(opt1, opt2));

        when(optionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        QuestionDTO result = service.addQuestion(1L, req);

        assertThat(result.getQuestionText()).isEqualTo("What is JVM?");
        assertThat(result.getOrderIndex()).isZero();
        verify(optionRepository).saveAll(argThat(opts -> ((List<?>) opts).size() == 2));
    }

    @Test
    @DisplayName("addQuestion assigns next orderIndex based on existing questions")
    void addQuestion_orderIndex_incrementsCorrectly() {
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L))
            .thenReturn(List.of(sampleQuestion(1L, 1L), sampleQuestion(2L, 1L)));
        when(questionRepository.save(any())).thenAnswer(i -> {
            Question q = i.getArgument(0);
            q.setId(3L);
            return q;
        });
        when(optionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        var req = new QuestionRequestDTO();
        req.setQuestionText("Third question");
        req.setOptions(Collections.emptyList());

        QuestionDTO result = service.addQuestion(1L, req);

        assertThat(result.getOrderIndex()).isEqualTo(2);
    }

    // ── deleteQuestion ────────────────────────────────────────────────

    @Test
    @DisplayName("deleteQuestion deletes options first then the question")
    void deleteQuestion_deletesOptionsAndQuestion() {
        service.deleteQuestion(1L, 5L);

        InOrder order = inOrder(optionRepository, questionRepository);
        order.verify(optionRepository).deleteByQuestionId(5L);
        order.verify(questionRepository).deleteById(5L);
    }

    // ── submitQuiz ────────────────────────────────────────────────────

    @Test
    @DisplayName("Score is 100% when all answers are correct")
    void submitQuiz_allCorrect_score100() {
        Quiz quiz = sampleQuiz();
        Question q1 = sampleQuestion(1L, 1L);
        QuestionOption correct = correctOption(1L, 1L);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(1L); attempt.setStudentId(10L); attempt.setQuiz(quiz);
        attempt.setScore(100.0); attempt.setPassed(true);
        attempt.setCorrectAnswers(1); attempt.setTotalQuestions(1);

        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(enrollmentClient.isEnrolled(10L, 100L)).thenReturn(true);
        when(attemptRepository.countByStudentIdAndQuizId(10L, 1L)).thenReturn(0);
        when(attemptRepository.save(any())).thenReturn(attempt);
        when(answerRepository.saveAll(any())).thenReturn(List.of());
        when(optionRepository.findById(1L)).thenReturn(Optional.of(correct));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q1));
        when(optionRepository.findByQuestionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(correct));

        var submission = new QuizSubmissionDTO();
        var answer = new QuizAnswerDTO();
        answer.setQuestionId(1L);
        answer.setSelectedOptionId(1L);
        submission.setAnswers(List.of(answer));

        var result = service.submitQuiz(1L, 10L, submission);

        assertThat(result.getScore()).isEqualTo(100.0);
        assertThat(result.getPassed()).isTrue();
        assertThat(result.getCorrectAnswers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Score is 0% when all answers are wrong")
    void submitQuiz_allWrong_score0() {
        Quiz quiz = sampleQuiz();
        Question q1 = sampleQuestion(1L, 1L);
        QuestionOption wrong = wrongOption(2L, 1L);
        QuestionOption correct = correctOption(1L, 1L);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(1L); attempt.setStudentId(10L); attempt.setQuiz(quiz);
        attempt.setScore(0.0); attempt.setPassed(false);
        attempt.setCorrectAnswers(0); attempt.setTotalQuestions(1);

        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(enrollmentClient.isEnrolled(10L, 100L)).thenReturn(true);
        when(attemptRepository.countByStudentIdAndQuizId(10L, 1L)).thenReturn(0);
        when(attemptRepository.save(any())).thenReturn(attempt);
        when(answerRepository.saveAll(any())).thenReturn(List.of());
        when(optionRepository.findById(2L)).thenReturn(Optional.of(wrong));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q1));
        when(optionRepository.findByQuestionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(correct, wrong));

        var submission = new QuizSubmissionDTO();
        var answer = new QuizAnswerDTO();
        answer.setQuestionId(1L);
        answer.setSelectedOptionId(2L);
        submission.setAnswers(List.of(answer));

        var result = service.submitQuiz(1L, 10L, submission);

        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getPassed()).isFalse();
    }

    @Test
    @DisplayName("Submitting quiz beyond maxAttempts throws exception")
    void submitQuiz_maxAttemptsExceeded_throws() {
        Quiz quiz = sampleQuiz();
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(enrollmentClient.isEnrolled(10L, 100L)).thenReturn(true);
        when(attemptRepository.countByStudentIdAndQuizId(10L, 1L)).thenReturn(3);

        var submission = new QuizSubmissionDTO();
        submission.setAnswers(List.of());

        assertThatThrownBy(() -> service.submitQuiz(1L, 10L, submission))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("attempt");
    }

    @Test
    @DisplayName("Non-enrolled student cannot submit quiz")
    void submitQuiz_notEnrolled_throws() {
        Quiz quiz = sampleQuiz();
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(enrollmentClient.isEnrolled(10L, 100L)).thenReturn(false);

        var submission = new QuizSubmissionDTO();
        submission.setAnswers(List.of());

        assertThatThrownBy(() -> service.submitQuiz(1L, 10L, submission))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("submitQuiz reports correct attemptsRemaining")
    void submitQuiz_attemptsRemainingCalculated() {
        Quiz quiz = sampleQuiz();
        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(2L); attempt.setStudentId(10L); attempt.setQuiz(quiz);
        attempt.setScore(0.0); attempt.setPassed(false);
        attempt.setCorrectAnswers(0); attempt.setTotalQuestions(0);

        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(enrollmentClient.isEnrolled(10L, 100L)).thenReturn(true);
        when(attemptRepository.countByStudentIdAndQuizId(10L, 1L)).thenReturn(1);
        when(attemptRepository.save(any())).thenReturn(attempt);
        when(answerRepository.saveAll(any())).thenReturn(Collections.emptyList());

        var submission = new QuizSubmissionDTO();
        submission.setAnswers(Collections.emptyList());

        var result = service.submitQuiz(1L, 10L, submission);

        assertThat(result.getAttemptsUsed()).isEqualTo(2);
        assertThat(result.getAttemptsRemaining()).isEqualTo(1);
    }

    // ── countAttempts ─────────────────────────────────────────────────

    @Test
    @DisplayName("countAttempts delegates to repository")
    void countAttempts_delegatesToRepo() {
        when(attemptRepository.countByStudentIdAndQuizId(10L, 1L)).thenReturn(2);

        assertThat(service.countAttempts(1L, 10L)).isEqualTo(2);
    }

    // ── getLeaderboard ────────────────────────────────────────────────

    @Test
    @DisplayName("getLeaderboard assigns LEGEND rank for score >= 90")
    void getLeaderboard_highScore_legendRank() {
        Quiz quiz = sampleQuiz();
        QuizAttempt attempt = QuizAttempt.builder()
            .id(1L).studentId(99L).quiz(quiz).score(95.0).passed(true)
            .correctAnswers(19).totalQuestions(20).build();

        when(attemptRepository.findTop5ByQuizIdOrderByScoreDescAttemptedAtAsc(1L))
            .thenReturn(List.of(attempt));
        when(userClient.getUserById(99L)).thenReturn(Map.of("firstName", "Alice", "lastName", "Smith"));

        var result = service.getLeaderboard(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRankTitle()).isEqualTo("LEGEND");
        assertThat(result.get(0).getStudentName()).isEqualTo("Alice Smith");
    }

    @Test
    @DisplayName("getLeaderboard assigns CHAMPION rank for score 75-89")
    void getLeaderboard_score80_championRank() {
        Quiz quiz = sampleQuiz();
        QuizAttempt attempt = QuizAttempt.builder()
            .id(1L).studentId(50L).quiz(quiz).score(80.0).passed(true)
            .correctAnswers(16).totalQuestions(20).build();

        when(attemptRepository.findTop5ByQuizIdOrderByScoreDescAttemptedAtAsc(1L))
            .thenReturn(List.of(attempt));
        when(userClient.getUserById(50L)).thenThrow(new RuntimeException("service down"));

        var result = service.getLeaderboard(1L);

        assertThat(result.get(0).getRankTitle()).isEqualTo("CHAMPION");
        assertThat(result.get(0).getStudentName()).isEqualTo("Player #50");
    }

    @Test
    @DisplayName("getLeaderboard assigns FIGHTER rank for score 60-74")
    void getLeaderboard_score65_fighterRank() {
        Quiz quiz = sampleQuiz();
        QuizAttempt attempt = QuizAttempt.builder()
            .id(1L).studentId(7L).quiz(quiz).score(65.0).passed(false)
            .correctAnswers(13).totalQuestions(20).build();

        when(attemptRepository.findTop5ByQuizIdOrderByScoreDescAttemptedAtAsc(1L))
            .thenReturn(List.of(attempt));
        when(userClient.getUserById(7L)).thenReturn(Map.of("firstName", "Bob", "lastName", ""));

        var result = service.getLeaderboard(1L);

        assertThat(result.get(0).getRankTitle()).isEqualTo("FIGHTER");
    }

    @Test
    @DisplayName("getLeaderboard assigns CHALLENGER rank for score < 60")
    void getLeaderboard_lowScore_challengerRank() {
        Quiz quiz = sampleQuiz();
        QuizAttempt attempt = QuizAttempt.builder()
            .id(1L).studentId(5L).quiz(quiz).score(40.0).passed(false)
            .correctAnswers(8).totalQuestions(20).build();

        when(attemptRepository.findTop5ByQuizIdOrderByScoreDescAttemptedAtAsc(1L))
            .thenReturn(List.of(attempt));
        when(userClient.getUserById(5L)).thenReturn(Map.of());

        var result = service.getLeaderboard(1L);

        assertThat(result.get(0).getRankTitle()).isEqualTo("CHALLENGER");
    }

    // ── updateQuiz ────────────────────────────────────────────────────

    @Test
    @DisplayName("updateQuiz persists title, description and score changes")
    void updateQuiz_updatesFields() {
        Quiz existing = sampleQuiz();
        when(quizRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(quizRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(Collections.emptyList());

        var dto = new QuizRequestDTO();
        dto.setTitle("Updated Quiz");
        dto.setPassingScore(85);
        dto.setMaxAttempts(2);

        QuizDTO result = service.updateQuiz(1L, dto);

        assertThat(result.getTitle()).isEqualTo("Updated Quiz");
        assertThat(result.getPassingScore()).isEqualTo(85);
    }

    @Test
    @DisplayName("updateQuiz throws when quiz not found")
    void updateQuiz_notFound_throws() {
        when(quizRepository.findById(99L)).thenReturn(Optional.empty());

        QuizRequestDTO emptyDto = new QuizRequestDTO();
        assertThatThrownBy(() -> service.updateQuiz(99L, emptyDto))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }

    // ── getBestScoreForStudentAndCourse ───────────────────────────────

    @Test
    @DisplayName("getBestScoreForStudentAndCourse returns null when no attempts")
    void getBestScore_noAttempts_returnsNull() {
        when(attemptRepository.findBestScoreByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.empty());

        assertThat(service.getBestScoreForStudentAndCourse(10L, 100L)).isNull();
    }

    @Test
    @DisplayName("getBestScoreForStudentAndCourse returns best score")
    void getBestScore_withAttempts_returnsHighest() {
        when(attemptRepository.findBestScoreByStudentIdAndCourseId(10L, 100L))
            .thenReturn(Optional.of(87.5));

        assertThat(service.getBestScoreForStudentAndCourse(10L, 100L)).isEqualTo(87.5);
    }

    // ── getStudentAvgScore ────────────────────────────────────────────

    @Test
    @DisplayName("getStudentAvgScore returns 50.0 when no attempts exist")
    void getStudentAvgScore_noAttempts_returns50() {
        when(attemptRepository.findByStudentId(10L)).thenReturn(Collections.emptyList());

        Map<String, Double> result = service.getStudentAvgScore(10L);

        assertThat(result)
            .containsEntry("avgScore", 50.0)
            .containsEntry("attemptCount", 0.0);
    }

    @Test
    @DisplayName("getStudentAvgScore calculates average across all attempts")
    void getStudentAvgScore_withAttempts_calculatesAverage() {
        Quiz quiz = sampleQuiz();
        QuizAttempt a1 = QuizAttempt.builder().studentId(10L).quiz(quiz)
            .score(80.0).passed(true).correctAnswers(8).totalQuestions(10).build();
        QuizAttempt a2 = QuizAttempt.builder().studentId(10L).quiz(quiz)
            .score(60.0).passed(false).correctAnswers(6).totalQuestions(10).build();

        when(attemptRepository.findByStudentId(10L)).thenReturn(List.of(a1, a2));

        Map<String, Double> result = service.getStudentAvgScore(10L);

        assertThat(result)
            .containsEntry("avgScore", 70.0)
            .containsEntry("attemptCount", 2.0);
    }

    // ── deleteQuizzesByCourse ─────────────────────────────────────────

    @Test
    @DisplayName("deleteQuizzesByCourse deletes all quiz-related data for a course")
    void deleteQuizzesByCourse_deletesAll() {
        Quiz quiz = sampleQuiz();
        Question q = sampleQuestion(5L, 1L);

        when(quizRepository.findByCourseIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(quiz));
        when(questionRepository.findByQuizIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(q));
        when(attemptRepository.findByQuizIdIn(List.of(1L))).thenReturn(Collections.emptyList());

        service.deleteQuizzesByCourse(100L);

        verify(optionRepository).deleteAllByQuestionIdIn(List.of(5L));
        verify(questionRepository).deleteAllByQuizIdIn(List.of(1L));
        verify(quizRepository).deleteAllByCourseId(100L);
    }

    @Test
    @DisplayName("deleteQuizzesByCourse does nothing extra for course with no quizzes")
    void deleteQuizzesByCourse_noQuizzes_onlyCallsDeleteAll() {
        when(quizRepository.findByCourseIdOrderByCreatedAtAsc(100L)).thenReturn(Collections.emptyList());

        service.deleteQuizzesByCourse(100L);

        verify(quizRepository).deleteAllByCourseId(100L);
        verifyNoInteractions(questionRepository, optionRepository, answerRepository);
    }
}
