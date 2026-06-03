package com.smart.restaurant_saas.job.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.job.dto.request.CreateJobRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.job.dto.request.UpdateJobRequest;
import com.smart.restaurant_saas.job.dto.response.JobResponse;
import com.smart.restaurant_saas.job.entity.Job;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.job.repository.JobRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantCodeService.ValidatedCode;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantCodeService tenantCodeService;
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
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.JOB
        );
        Long tenantId = validatedCode.tenantId();
        String code = validatedCode.code();
        if (jobRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Job code already exists for tenant: " + code);
        }

        Job job = new Job();
        applyBilingualFields(job, request.nameEn(), request.nameAr(), request.name(),
                request.descriptionEn(), request.descriptionAr(), request.description());
        job.setTenantId(tenantId);
        job.setCode(code);
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
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.JOB
        );
        Long tenantId = validatedCode.tenantId();
        Job job = findJob(tenantId, id);
        String code = validatedCode.code();
        if (!job.getCode().equals(code)
                && jobRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Job code already exists for tenant: " + code);
        }

        applyBilingualFields(job, request.nameEn(), request.nameAr(), request.name(),
                request.descriptionEn(), request.descriptionAr(), request.description());
        job.setCode(code);
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job not found: " + id));
    }

    private void applyStatusChange(Long tenantId, Job job, boolean active) {
        if (Boolean.TRUE.equals(job.getActive()) && !active
                && employeeRepository.existsByTenantIdAndJobIdAndActiveTrue(tenantId, job.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot deactivate a job used by active employees");
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one of nameEn or nameAr is required");
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
}
