package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.hr.dto.response.LeaveTypeResponse;
import com.smart.restaurant_saas.hr.repository.LeaveTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> listLeaveTypes() {
        return leaveTypeRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(LeaveTypeResponse::from)
                .toList();
    }
}
