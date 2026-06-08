package tn.esprit.quizservice.messaging;

import tn.esprit.quizservice.config.RabbitMQConfig;
import tn.esprit.quizservice.events.CourseDeletedEvent;
import tn.esprit.quizservice.services.IQuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CourseEventListener {

    private final IQuizService quizService;

    @RabbitListener(queues = RabbitMQConfig.COURSE_DELETED_QUEUE)
    public void handleCourseDeleted(CourseDeletedEvent event) {
        log.info("[RabbitMQ] Received course.deleted: courseId={} title={}",
            event.getCourseId(), event.getCourseTitle());
        try {
            quizService.deleteQuizzesByCourse(event.getCourseId());
            log.info("[RabbitMQ] Deleted all quizzes for courseId={}",
                event.getCourseId());
        } catch (Exception e) {
            log.error("[RabbitMQ] Failed to delete quizzes for courseId={}: {}",
                event.getCourseId(), e.getMessage());
        }
    }
}
