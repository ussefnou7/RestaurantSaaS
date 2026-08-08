package com.smart.restaurant_saas.job.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import com.smart.restaurant_saas.hr.service.HrErrorCode;
import com.smart.restaurant_saas.job.dto.request.CreateJobRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.job.dto.request.UpdateJobRequest;
import com.smart.restaurant_saas.job.dto.response.JobResponse;
import com.smart.restaurant_saas.job.entity.Job;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.job.repository.JobRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantSequenceService tenantSequenceService;
    private final JobRepository jobRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<JobResponse> listJobs() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return jobRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
                .map(JobResponse::from)
                .toList();
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();

        Job job = new Job();
        applyBilingualFields(job, request.nameEn(), request.nameAr(), request.name(),
                request.descriptionEn(), request.descriptionAr(), request.description());
        job.setTenantId(tenantId);
        job.setCode(generateUniqueCode(tenantId));
        job.setActive(request.active() == null || request.active());
        job.setCreatedBy(currentTenantProvider.getActorUserId());

        return JobResponse.from(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return JobResponse.from(findJob(tenantId, id));
    }

    @Transactional
    public JobResponse updateJob(Long id, UpdateJobRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Job job = findJob(tenantId, id);

        applyBilingualFields(job, request.nameEn(), request.nameAr(), request.name(),
                request.descriptionEn(), request.descriptionAr(), request.description());
        if (request.active() != null) {
            applyStatusChange(tenantId, job, request.active());
        }
        job.setUpdatedBy(currentTenantProvider.getActorUserId());

        return JobResponse.from(jobRepository.saveAndFlush(job));
    }

    @Transactional
    public JobResponse updateJobStatus(Long id, UpdateActiveStatusRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Job job = findJob(tenantId, id);
        applyStatusChange(tenantId, job, request.active());
        job.setUpdatedBy(currentTenantProvider.getActorUserId());

        return JobResponse.from(jobRepository.saveAndFlush(job));
    }

    private Job findJob(Long tenantId, Long id) {
        return jobRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Job not found: " + id,
                        ErrorParams.of("entityType", "Job", "entityId", id)));
    }

    private void applyStatusChange(Long tenantId, Job job, boolean active) {
        if (Boolean.TRUE.equals(job.getActive()) && !active
                && employeeRepository.existsByTenantIdAndJobIdAndActiveTrue(tenantId, job.getId())) {
            throw new BusinessException(HrErrorCode.DEACTIVATION_BLOCKED,
                    "Cannot deactivate a job used by active employees",
                    ErrorParams.of("entityType", "Job", "blockedByEntityType", "Employee"));
        }
        job.setActive(active);
    }

    private void applyBilingualFields(
            Job job,
            String requestedNameEn,
            String requestedNameAr,
            String legacyName,
            String requestedDescriptionEn,
            String requestedDescriptionAr,
            String legacyDescription
    ) {
        String nameEn = firstNonBlank(requestedNameEn, legacyName);
        String nameAr = trimToNull(requestedNameAr);
        String displayName = firstNonBlank(nameEn, nameAr);
        if (displayName == null) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "At least one of nameEn or nameAr is required",
                    ErrorParams.of("field", "name"));
        }

        String descriptionEn = firstNonBlank(requestedDescriptionEn, legacyDescription);
        String descriptionAr = trimToNull(requestedDescriptionAr);

        job.setName(displayName);
        job.setNameEn(nameEn);
        job.setNameAr(nameAr);
        job.setDescription(firstNonBlank(descriptionEn, descriptionAr));
        job.setDescriptionEn(descriptionEn);
        job.setDescriptionAr(descriptionAr);
    }

    private String generateUniqueCode(Long tenantId) {
        String code;
        do {
            code = tenantSequenceService.generateEntityCode(tenantId, TenantEntityPrefix.JOB);
        } while (jobRepository.existsByTenantIdAndCode(tenantId, code));
        return code;
    }
}
