package com.ybrainy.joboffer.client;

import com.ybrainy.joboffer.dto.ExistsResponse;
import com.ybrainy.joboffer.dto.PartnershipSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "partnership-service")
public interface PartnershipClient {

  @GetMapping("/api/partnerships/{id}/exists")
  ExistsResponse exists(@PathVariable("id") String id);

  @GetMapping("/api/partnerships/{id}")
  PartnershipSummary getById(@PathVariable("id") String id);
}
