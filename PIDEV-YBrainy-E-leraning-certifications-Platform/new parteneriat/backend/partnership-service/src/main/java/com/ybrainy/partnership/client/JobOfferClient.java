package com.ybrainy.partnership.client;

import com.ybrainy.partnership.dto.JobOfferSummary;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "job-offer-service")
public interface JobOfferClient {

  @GetMapping("/api/offers")
  List<JobOfferSummary> findByPartnership(
      @RequestParam("partnershipId") String partnershipId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "50") int size);
}

