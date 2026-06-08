package tn.esprit.quizservice.services;

import tn.esprit.quizservice.dto.LeaderboardEntryDTO;
import tn.esprit.quizservice.dto.QuestionDTO;
import tn.esprit.quizservice.dto.QuestionRequestDTO;
import tn.esprit.quizservice.dto.QuizDTO;
import tn.esprit.quizservice.dto.QuizRequestDTO;
import tn.esprit.quizservice.dto.QuizResultDTO;
import tn.esprit.quizservice.dto.QuizSubmissionDTO;

import java.util.List;
import java.util.Map;

public interface IQuizService {
    List<QuizDTO> getQuizzesByCourse(Long courseId);
    QuizDTO getQuizById(Long courseId, Long quizId);
    QuizDTO createQuiz(Long courseId, QuizRequestDTO request);
    void deleteQuiz(Long courseId, Long quizId);
    List<QuestionDTO> getQuestionsByQuiz(Long quizId);
    QuestionDTO addQuestion(Long quizId, QuestionRequestDTO request);
    void deleteQuestion(Long quizId, Long questionId);
    QuizResultDTO submitQuiz(Long quizId, Long studentId, QuizSubmissionDTO submission);
    int countAttempts(Long quizId, Long studentId);
    QuizDTO updateQuiz(Long quizId, QuizRequestDTO dto);
    QuestionDTO updateQuestion(Long questionId, QuestionRequestDTO dto);
    List<LeaderboardEntryDTO> getLeaderboard(Long quizId);
    Double getBestScoreForStudentAndCourse(Long studentId, Long courseId);
    Map<String, Double> getStudentAvgScore(Long studentId);
    void deleteQuizzesByCourse(Long courseId);
}
