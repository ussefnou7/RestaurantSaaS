package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateJobTitleRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateJobTitleRequest;
import com.smart.restaurant_saas.hr.dto.response.JobTitleResponse;
import com.smart.restaurant_saas.hr.entity.JobTitle;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.hr.repository.JobTitleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobTitleService {

    private final CurrentTenantProvider currentTenantProvider;
    private final JobTitleRepository jobTitleRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<JobTitleResponse> listJobTitles() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return jobTitleRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
                .map(JobTitleResponse::from)
                .toList();
    }

    @Transactional
    public JobTitleResponse createJobTitle(CreateJobTitleRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        String code = normalizeCode(request.code());
        if (jobTitleRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Job title code already exists for tenant: " + code);
        }

        JobTitle jobTitle = new JobTitle();
        jobTitle.setTenantId(tenantId);
        jobTitle.setName(request.name().trim());
        jobTitle.setCode(code);
        jobTitle.setDescription(trimToNull(request.description()));
        jobTitle.setActive(request.active() == null || request.active());
        jobTitle.setCreatedBy(currentTenantProvider.getActorUserId());

        return JobTitleResponse.from(jobTitleRepository.save(jobTitle));
    }

    @Transactional(readOnly = true)
    public JobTitleResponse getJobTitle(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return JobTitleResponse.from(findJobTitle(tenantId, id));
    }

    @Transactional
    public JobTitleResponse updateJobTitle(Long id, UpdateJobTitleRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        JobTitle jobTitle = findJobTitle(tenantId, id);
        String code = normalizeCode(request.code());
        if (!jobTitle.getCode().equals(code)
                && jobTitleRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Job title code already exists for tenant: " + code);
        }

        jobTitle.setName(request.name().trim());
        jobTitle.setCode(code);
        jobTitle.setDescription(trimToNull(request.description()));
        if (request.active() != null) {
            applyStatusChange(tenantId, jobTitle, request.active());
        }
        jobTitle.setUpdatedBy(currentTenantProvider.getActorUserId());

        return JobTitleResponse.from(jobTitleRepository.saveAndFlush(jobTitle));
    }

    @Transactional
    public JobTitleResponse updateJobTitleStatus(Long id, UpdateActiveStatusRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        JobTitle jobTitle = findJobTitle(tenantId, id);
        applyStatusChange(tenantId, jobTitle, request.active());
        jobTitle.setUpdatedBy(currentTenantProvider.getActorUserId());

        return JobTitleResponse.from(jobTitleRepository.saveAndFlush(jobTitle));
    }

    private JobTitle findJobTitle(Long tenantId, Long id) {
        return jobTitleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job title not found: " + id));
    }

    private void applyStatusChange(Long tenantId, JobTitle jobTitle, boolean active) {
        if (Boolean.TRUE.equals(jobTitle.getActive()) && !active
                && employeeRepository.existsByTenantIdAndJobTitleIdAndActiveTrue(tenantId, jobTitle.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot deactivate a job title used by active employees");
        }
        jobTitle.setActive(active);
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
