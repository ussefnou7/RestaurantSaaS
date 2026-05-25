package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.JobTitle;
import java.time.LocalDateTime;

public record JobTitleResponse(
        Long id,
        String name,
        String code,
        String description,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static JobTitleResponse from(JobTitle jobTitle) {
        return new JobTitleResponse(
                jobTitle.getId(),
                jobTitle.getName(),
                jobTitle.getCode(),
                jobTitle.getDescription(),
                jobTitle.getActive(),
                jobTitle.getCreatedAt(),
                jobTitle.getUpdatedAt()
        );
    }
}
