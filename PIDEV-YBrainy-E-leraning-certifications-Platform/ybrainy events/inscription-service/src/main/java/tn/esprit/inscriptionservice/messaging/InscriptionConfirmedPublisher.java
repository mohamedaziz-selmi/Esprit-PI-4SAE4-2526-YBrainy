package tn.esprit.inscriptionservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tn.esprit.inscriptionservice.config.RabbitMqConfig;
import tn.esprit.inscriptionservice.dto.InscriptionConfirmedMessage;
import tn.esprit.inscriptionservice.entity.Inscription;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InscriptionConfirmedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Inscription inscription, String source) {
        InscriptionConfirmedMessage payload = new InscriptionConfirmedMessage(
                inscription.getIdInscription(),
                inscription.getEventId(),
                inscription.getStudentId(),
                inscription.getStatut() != null ? inscription.getStatut().name() : "",
                LocalDateTime.now(),
                source
        );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.INSCRIPTION_EXCHANGE,
                RabbitMqConfig.INSCRIPTION_CONFIRMED_ROUTING_KEY,
                payload
        );
    }
}
