package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.normalizeCode;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimRequired;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.request.CreateMaterialCatalogRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateMaterialCatalogRequest;
import com.smart.restaurant_saas.inventory.dto.response.MaterialCatalogResponse;
import com.smart.restaurant_saas.inventory.entity.MaterialCatalog;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.mapper.MaterialCatalogMapper;
import com.smart.restaurant_saas.inventory.repository.MaterialCatalogRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialCatalogService {

    private final MaterialCatalogRepository materialRepository;
    private final MaterialCategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final MaterialCatalogMapper materialMapper;

    @Transactional(readOnly = true)
    public List<MaterialCatalogResponse> listMaterials(
            Long categoryId,
            Long stockUomId,
            Long displayUomId,
            String search,
            Boolean active
    ) {
        return materialRepository.findByFilters(categoryId, stockUomId, displayUomId, searchPattern(search), active).stream()
                .map(materialMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaterialCatalogResponse getMaterial(Long id) {
        return materialMapper.toResponse(findMaterial(id));
    }

    @Transactional
    public MaterialCatalogResponse createMaterial(CreateMaterialCatalogRequest request) {
        String code = normalizeCode(request.code(), "code");
        if (materialRepository.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material code already exists: " + code);
        }

        MaterialCatalog material = new MaterialCatalog();
        applyCreateFields(material, request, code);

        return materialMapper.toResponse(materialRepository.save(material));
    }

    @Transactional
    public MaterialCatalogResponse updateMaterial(Long id, UpdateMaterialCatalogRequest request) {
        MaterialCatalog material = findMaterial(id);
        String code = normalizeCode(request.code(), "code");
        if (!material.getCode().equals(code) && materialRepository.existsByCodeAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material code already exists: " + code);
        }

        applyUpdateFields(material, request, code);

        return materialMapper.toResponse(materialRepository.saveAndFlush(material));
    }

    @Transactional
    public MaterialCatalogResponse activateMaterial(Long id) {
        MaterialCatalog material = findMaterial(id);
        material.setActive(true);
        return materialMapper.toResponse(materialRepository.saveAndFlush(material));
    }

    @Transactional
    public MaterialCatalogResponse deactivateMaterial(Long id) {
        MaterialCatalog material = findMaterial(id);
        material.setActive(false);
        return materialMapper.toResponse(materialRepository.saveAndFlush(material));
    }

    private MaterialCatalog findMaterial(Long id) {
        return materialRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material not found: " + id));
    }

    private void applyCreateFields(
            MaterialCatalog material,
            CreateMaterialCatalogRequest request,
            String code
    ) {
        material.setCode(code);
        material.setName(trimRequired(request.name(), "name"));
        material.setNameAr(trimToNull(request.nameAr()));
        material.setCategory(findCategory(request.categoryId()));
        UomPair uomPair = findCompatibleActiveUomPair(request.defaultStockUomId(), request.defaultDisplayUomId());
        material.setDefaultStockUom(uomPair.stockUom());
        material.setDefaultDisplayUom(uomPair.displayUom());
        material.setActive(request.active() == null || request.active());
        material.setSortOrder(request.sortOrder());
    }

    private void applyUpdateFields(
            MaterialCatalog material,
            UpdateMaterialCatalogRequest request,
            String code
    ) {
        material.setCode(code);
        material.setName(trimRequired(request.name(), "name"));
        material.setNameAr(trimToNull(request.nameAr()));
        material.setCategory(findCategory(request.categoryId()));
        UomPair uomPair = findCompatibleActiveUomPair(request.defaultStockUomId(), request.defaultDisplayUomId());
        material.setDefaultStockUom(uomPair.stockUom());
        material.setDefaultDisplayUom(uomPair.displayUom());
        if (request.active() != null) {
            material.setActive(request.active());
        }
        material.setSortOrder(request.sortOrder());
    }

    private MaterialCategory findCategory(Long id) {
        return categoryRepository.findByIdAndTenantIdIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid global material category: " + id));
    }

    private UomPair findCompatibleActiveUomPair(Long stockUomId, Long displayUomId) {
        Uom stockUom = findActiveUom(stockUomId, "stockUomId");
        Uom displayUom = findActiveUom(displayUomId, "displayUomId");
        if (stockUom.getType() != displayUom.getType()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stock UOM and display UOM must have the same type");
        }
        return new UomPair(stockUom, displayUom);
    }

    private Uom findActiveUom(Long id, String fieldName) {
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        Uom uom = uomRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + id));
        if (!Boolean.TRUE.equals(uom.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is inactive: " + id);
        }
        return uom;
    }

    private record UomPair(Uom stockUom, Uom displayUom) {
    }
}
