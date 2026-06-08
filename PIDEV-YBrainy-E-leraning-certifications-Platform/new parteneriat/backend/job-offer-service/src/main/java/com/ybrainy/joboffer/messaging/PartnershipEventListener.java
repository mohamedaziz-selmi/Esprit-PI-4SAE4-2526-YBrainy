package com.ybrainy.joboffer.messaging;

import com.ybrainy.joboffer.entity.JobOffer;
import com.ybrainy.joboffer.entity.OfferStatus;
import com.ybrainy.joboffer.repository.JobOfferRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PartnershipEventListener {

  private static final Logger log = LoggerFactory.getLogger(PartnershipEventListener.class);

  private final JobOfferRepository jobOfferRepository;

  public PartnershipEventListener(JobOfferRepository jobOfferRepository) {
    this.jobOfferRepository = jobOfferRepository;
  }

  @Transactional
  @RabbitListener(queues = "${app.rabbitmq.partnership.queue}")
  public void handlePartnershipEvent(PartnershipEvent event) {
    if (event == null || event.partnershipId() == null || event.partnershipId().isBlank()) {
      log.warn("Skipping invalid partnership event: {}", event);
      return;
    }

    boolean shouldCloseOffers =
        "PARTNERSHIP_DELETED".equals(event.eventType())
            || ("PARTNERSHIP_UPDATED".equals(event.eventType()) && !event.active());

    if (!shouldCloseOffers) {
      log.info("Received partnership event {} for partnership {}", event.eventType(), event.partnershipId());
      return;
    }

    List<JobOffer> offers = jobOfferRepository.findAllByPartnershipId(event.partnershipId());
    if (offers.isEmpty()) {
      log.info("No job offers found for partnership {}", event.partnershipId());
      return;
    }

    offers.stream().filter(offer -> offer.getStatus() != OfferStatus.CLOSED).forEach(offer -> offer.setStatus(OfferStatus.CLOSED));
    jobOfferRepository.saveAll(offers);
    log.info(
        "Closed {} job offer(s) after partnership event {} for partnership {}",
        offers.size(),
        event.eventType(),
        event.partnershipId());
  }
}
