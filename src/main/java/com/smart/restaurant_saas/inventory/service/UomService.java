package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.normalizeCode;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.parseUomType;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimRequired;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.validatePositiveFactor;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.request.CreateUomRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateUomRequest;
import com.smart.restaurant_saas.inventory.dto.response.UomResponse;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.enums.UomType;
import com.smart.restaurant_saas.inventory.mapper.UomMapper;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UomService {

    private final UomRepository uomRepository;
    private final UomMapper uomMapper;

    @Transactional(readOnly = true)
    public List<UomResponse> listUoms(String type, Boolean active) {
        UomType parsedType = parseUomType(type);
        return uomRepository.findByFilters(parsedType, active).stream()
                .map(uomMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UomResponse getUom(Long id) {
        return uomMapper.toResponse(findUom(id));
    }

    @Transactional
    public UomResponse createUom(CreateUomRequest request) {
        String code = normalizeCode(request.code(), "code");
        if (uomRepository.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "UOM code already exists: " + code);
        }

        Uom uom = new Uom();
        applyCreateFields(uom, request, code);

        return uomMapper.toResponse(uomRepository.save(uom));
    }

    @Transactional
    public UomResponse updateUom(Long id, UpdateUomRequest request) {
        Uom uom = findUom(id);
        String code = normalizeCode(request.code(), "code");
        if (!uom.getCode().equals(code) && uomRepository.existsByCodeAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "UOM code already exists: " + code);
        }

        applyUpdateFields(uom, request, code);

        return uomMapper.toResponse(uomRepository.saveAndFlush(uom));
    }

    @Transactional
    public UomResponse activateUom(Long id) {
        Uom uom = findUom(id);
        uom.setActive(true);
        return uomMapper.toResponse(uomRepository.saveAndFlush(uom));
    }

    @Transactional
    public UomResponse deactivateUom(Long id) {
        Uom uom = findUom(id);
        uom.setActive(false);
        return uomMapper.toResponse(uomRepository.saveAndFlush(uom));
    }

    private Uom findUom(Long id) {
        return uomRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UOM not found: " + id));
    }

    private void applyCreateFields(Uom uom, CreateUomRequest request, String code) {
        validatePositiveFactor(request.factorToBase());
        uom.setCode(code);
        uom.setName(trimRequired(request.name(), "name"));
        uom.setNameAr(trimToNull(request.nameAr()));
        uom.setSymbol(trimRequired(request.symbol(), "symbol"));
        uom.setType(request.type());
        uom.setBaseCode(normalizeCode(request.baseCode(), "baseCode"));
        uom.setFactorToBase(request.factorToBase());
        uom.setActive(request.active() == null || request.active());
        uom.setSortOrder(request.sortOrder());
    }

    private void applyUpdateFields(Uom uom, UpdateUomRequest request, String code) {
        validatePositiveFactor(request.factorToBase());
        uom.setCode(code);
        uom.setName(trimRequired(request.name(), "name"));
        uom.setNameAr(trimToNull(request.nameAr()));
        uom.setSymbol(trimRequired(request.symbol(), "symbol"));
        uom.setType(request.type());
        uom.setBaseCode(normalizeCode(request.baseCode(), "baseCode"));
        uom.setFactorToBase(request.factorToBase());
        if (request.active() != null) {
            uom.setActive(request.active());
        }
        uom.setSortOrder(request.sortOrder());
    }
}
