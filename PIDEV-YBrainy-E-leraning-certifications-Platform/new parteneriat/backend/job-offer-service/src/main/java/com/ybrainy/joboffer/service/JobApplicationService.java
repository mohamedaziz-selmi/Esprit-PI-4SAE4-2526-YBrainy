package com.ybrainy.joboffer.service;

import com.ybrainy.joboffer.dto.JobApplicationRequest;
import com.ybrainy.joboffer.dto.JobApplicationResponse;
import com.ybrainy.joboffer.dto.JobApplicationUpdateRequest;
import java.util.List;

public interface JobApplicationService {

  JobApplicationResponse create(String offerId, JobApplicationRequest request);

  List<JobApplicationResponse> listByOffer(String offerId);

  List<JobApplicationResponse> listAll();

  JobApplicationResponse updateReview(String applicationId, JobApplicationUpdateRequest request);

  void delete(String applicationId);
}
