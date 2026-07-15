package com.smart.restaurant_saas.order.intake;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.order.OrderErrorCode;
import com.smart.restaurant_saas.order.core.OrderService;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.intake.dto.IncomingOrderRequestCreateRequest;
import com.smart.restaurant_saas.order.intake.dto.IncomingOrderRequestFilters;
import com.smart.restaurant_saas.order.intake.dto.IncomingOrderRequestResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncomingOrderRequestService {

    private final IncomingOrderRequestRepository requestRepository;
    private final BranchRepository branchRepository;
    private final OrderService orderService;
    private final IncomingOrderRequestMapper mapper;

    @Transactional
    public IncomingOrderRequestResponse createRequest(IncomingOrderRequestCreateRequest request,
                                                      Long tenantId,
                                                      Long userId) {
        validateBranchIfPresent(request.getBranchId(), tenantId);

        IncomingOrderRequest incoming = new IncomingOrderRequest();
        incoming.setTenantId(tenantId);
        incoming.setCreatedBy(userId);
        incoming.setSource(request.getSource());
        incoming.setAggregatorName(request.getAggregatorName());
        incoming.setExternalReferenceId(request.getExternalReferenceId());
        incoming.setBranchId(request.getBranchId());
        incoming.setPayload(request.getPayload());
        incoming.setStatus(IncomingOrderRequestStatus.RECEIVED);

        return mapper.toResponse(requestRepository.save(incoming), null);
    }

    @Transactional
    public IncomingOrderRequestResponse markSentToPos(Long requestId, Long tenantId, Long userId) {
        IncomingOrderRequest request = loadOwned(requestId, tenantId);
        assertStatus(request, IncomingOrderRequestStatus.RECEIVED, "markSentToPos");

        request.setStatus(IncomingOrderRequestStatus.SENT_TO_POS);
        request.setSentToPosAt(LocalDateTime.now());
        request.setUpdatedBy(userId);

        return mapper.toResponse(requestRepository.save(request), null);
    }

    @Transactional
    public IncomingOrderRequestResponse linkToCompletedOrder(Long requestId,
                                                             Long orderId,
                                                             Long tenantId,
                                                             Long userId) {
        IncomingOrderRequest request = loadOwned(requestId, tenantId);
        assertStatus(request, IncomingOrderRequestStatus.SENT_TO_POS, "linkToCompletedOrder");
        OrderSummaryResponse linkedOrder = orderService.getOrderSummaryById(orderId, tenantId);

        request.setCompletedOrderId(linkedOrder.getId());
        request.setStatus(IncomingOrderRequestStatus.LINKED);
        request.setUpdatedBy(userId);

        return mapper.toResponse(requestRepository.save(request), linkedOrder);
    }

    @Transactional(readOnly = true)
    public IncomingOrderRequestResponse getRequestById(Long requestId, Long tenantId) {
        IncomingOrderRequest request = loadOwned(requestId, tenantId);
        return mapper.toResponse(request, resolveLinkedOrder(request, tenantId));
    }

    @Transactional(readOnly = true)
    public Page<IncomingOrderRequestResponse> listRequests(Long tenantId,
                                                           IncomingOrderRequestFilters filters,
                                                           Pageable pageable) {
        return requestRepository.findByFilters(
                tenantId,
                filters.source(),
                filters.status(),
                filters.branchId(),
                filters.fromDate(),
                filters.toDate(),
                pageable)
            .map(request -> mapper.toResponse(request, null));
    }

    private void validateBranchIfPresent(Long branchId, Long tenantId) {
        if (branchId == null) {
            return;
        }
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.BRANCH_NOT_FOUND,
                "Branch not found: " + branchId,
                ErrorParams.of("entityType", "Branch", "entityId", branchId)));
        if (!Boolean.TRUE.equals(branch.getActive())) {
            throw new ResourceNotFoundException(OrderErrorCode.BRANCH_NOT_FOUND,
                "Branch is inactive: " + branchId,
                ErrorParams.of("entityType", "Branch", "entityId", branchId));
        }
    }

    private void assertStatus(IncomingOrderRequest request,
                              IncomingOrderRequestStatus requiredStatus,
                              String action) {
        if (request.getStatus() != requiredStatus) {
            throw new BusinessException(OrderErrorCode.INVALID_REQUEST_STATUS_TRANSITION,
                "Invalid incoming order request status transition",
                ErrorParams.of("requestId", request.getId(),
                    "currentStatus", request.getStatus().name(),
                    "requiredStatus", requiredStatus.name(),
                    "action", action));
        }
    }

    private OrderSummaryResponse resolveLinkedOrder(IncomingOrderRequest request, Long tenantId) {
        if (request.getCompletedOrderId() == null) {
            return null;
        }
        return orderService.getOrderSummaryById(request.getCompletedOrderId(), tenantId);
    }

    private IncomingOrderRequest loadOwned(Long requestId, Long tenantId) {
        return requestRepository.findByIdAndTenantId(requestId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.INCOMING_ORDER_REQUEST_NOT_FOUND,
                "Incoming order request not found: " + requestId,
                ErrorParams.of("entityType", "IncomingOrderRequest", "entityId", requestId)));
    }
}
