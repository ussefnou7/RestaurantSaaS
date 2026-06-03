package com.smart.restaurant_saas.job.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

import com.smart.restaurant_saas.job.entity.Job;
import java.time.LocalDateTime;

public record JobResponse(
        Long id,
        String name,
        String nameEn,
        String nameAr,
        String code,
        String description,
        String descriptionEn,
        String descriptionAr,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static JobResponse from(Job job) {
        String nameEn = englishOrLegacy(job.getNameEn(), job.getNameAr(), job.getName());
        String descriptionEn = englishOrLegacy(job.getDescriptionEn(), job.getDescriptionAr(), job.getDescription());
        return new JobResponse(
                job.getId(),
                firstNonBlank(job.getName(), nameEn, job.getNameAr()),
                nameEn,
                job.getNameAr(),
                job.getCode(),
                firstNonBlank(job.getDescription(), descriptionEn, job.getDescriptionAr()),
                descriptionEn,
                job.getDescriptionAr(),
                job.getActive(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
