package com.ybrainy.joboffer.controller;

import com.ybrainy.joboffer.dto.JobApplicationUpdateRequest;
import com.ybrainy.joboffer.dto.JobApplicationResponse;
import jakarta.validation.Valid;
import com.ybrainy.joboffer.service.JobApplicationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

  private final JobApplicationService applicationService;

  public JobApplicationController(JobApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  @GetMapping
  public List<JobApplicationResponse> getAll() {
    return applicationService.listAll();
  }

  @PutMapping("/{applicationId}")
  public JobApplicationResponse updateReview(
      @PathVariable String applicationId, @Valid @RequestBody JobApplicationUpdateRequest request) {
    return applicationService.updateReview(applicationId, request);
  }

  @DeleteMapping("/{applicationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String applicationId) {
    applicationService.delete(applicationId);
  }
}
