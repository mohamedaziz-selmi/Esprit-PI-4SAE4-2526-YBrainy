package com.ybrainy.joboffer.service;

import com.ybrainy.joboffer.entity.JobApplication;

public interface JobApplicationNotificationService {

  boolean notifyAccepted(JobApplication application, String offerTitle);
}
