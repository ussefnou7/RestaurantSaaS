package com.smart.restaurant_saas.job.controller;

import com.smart.restaurant_saas.job.dto.request.CreateJobRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.job.dto.request.UpdateJobRequest;
import com.smart.restaurant_saas.job.dto.response.JobResponse;
import com.smart.restaurant_saas.job.service.JobService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('JOBS_VIEW')")
    public List<JobResponse> listJobs() {
        return jobService.listJobs();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('JOBS_CREATE')")
    public JobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        return jobService.createJob(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('JOBS_VIEW')")
    public JobResponse getJob(@PathVariable Long id) {
        return jobService.getJob(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('JOBS_UPDATE')")
    public JobResponse updateJob(
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobRequest request
    ) {
        return jobService.updateJob(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('JOBS_UPDATE')")
    public JobResponse updateJobStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveStatusRequest request
    ) {
        return jobService.updateJobStatus(id, request);
    }
}
