package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.CreateJobTitleRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateJobTitleRequest;
import com.smart.restaurant_saas.hr.dto.response.JobTitleResponse;
import com.smart.restaurant_saas.hr.service.JobTitleService;
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
@RequestMapping("/api/hr/job-titles")
public class JobTitleController {

    private final JobTitleService jobTitleService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_JOB_TITLES_VIEW')")
    public List<JobTitleResponse> listJobTitles() {
        return jobTitleService.listJobTitles();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_JOB_TITLES_CREATE')")
    public JobTitleResponse createJobTitle(@Valid @RequestBody CreateJobTitleRequest request) {
        return jobTitleService.createJobTitle(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_JOB_TITLES_VIEW')")
    public JobTitleResponse getJobTitle(@PathVariable Long id) {
        return jobTitleService.getJobTitle(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_JOB_TITLES_UPDATE')")
    public JobTitleResponse updateJobTitle(
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobTitleRequest request
    ) {
        return jobTitleService.updateJobTitle(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_JOB_TITLES_UPDATE')")
    public JobTitleResponse updateJobTitleStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveStatusRequest request
    ) {
        return jobTitleService.updateJobTitleStatus(id, request);
    }
}
