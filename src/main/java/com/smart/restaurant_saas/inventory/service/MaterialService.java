package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimRequired;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.request.CreateMaterialRequest;
import com.smart.restaurant_saas.inventory.dto.request.ImportMaterialsRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateMaterialRequest;
import com.smart.restaurant_saas.inventory.dto.response.ImportMaterialsResponse;
import com.smart.restaurant_saas.inventory.dto.response.MaterialResponse;
import com.smart.restaurant_saas.inventory.dto.response.SkippedMaterialImportResponse;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.MaterialCatalog;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.enums.MaterialImportSkipReason;
import com.smart.restaurant_saas.inventory.mapper.MaterialMapper;
import com.smart.restaurant_saas.inventory.repository.MaterialCatalogRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantRepository tenantRepository;
    private final MaterialRepository materialRepository;
    private final MaterialCatalogRepository catalogRepository;
    private final MaterialCategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final MaterialMapper materialMapper;

    @Transactional(readOnly = true)
    public List<MaterialResponse> listMaterials(
            String search,
            Long categoryId,
            Long stockUomId,
            Long displayUomId,
            Boolean active,
            Long catalogId
    ) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return materialRepository.findByTenantIdAndFilters(
                        tenantId,
                        searchPattern(search),
                        categoryId,
                        stockUomId,
                        displayUomId,
                        active,
                        catalogId
                ).stream()
                .map(materialMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaterialResponse getMaterial(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return materialMapper.toResponse(findMaterial(tenantId, id));
    }

    @Transactional
    public MaterialResponse createMaterial(CreateMaterialRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        String code = validateAndNormalizeMaterialCode(tenantId, request.code());
        if (materialRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material code already exists for tenant: " + code);
        }

        Material material = new Material();
        material.setTenantId(tenantId);
        material.setCatalog(null);
        applyCreateFields(tenantId, material, request, code);

        return materialMapper.toResponse(materialRepository.save(material));
    }

    @Transactional
    public MaterialResponse updateMaterial(Long id, UpdateMaterialRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Material material = findMaterial(tenantId, id);
        String code = validateAndNormalizeMaterialCode(tenantId, request.code());
        if (!material.getCode().equals(code)
                && materialRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material code already exists for tenant: " + code);
        }

        applyUpdateFields(tenantId, material, request, code);

        return materialMapper.toResponse(materialRepository.saveAndFlush(material));
    }

    @Transactional
    public MaterialResponse activateMaterial(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Material material = findMaterial(tenantId, id);
        material.setActive(true);
        return materialMapper.toResponse(materialRepository.saveAndFlush(material));
    }

    @Transactional
    public MaterialResponse deactivateMaterial(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Material material = findMaterial(tenantId, id);
        material.setActive(false);
        return materialMapper.toResponse(materialRepository.saveAndFlush(material));
    }

    @Transactional
    public ImportMaterialsResponse importMaterials(ImportMaterialsRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        String tenantCodePrefix = getTenantCodePrefix(tenantId);
        List<MaterialResponse> createdMaterials = new ArrayList<>();
        List<SkippedMaterialImportResponse> skippedMaterials = new ArrayList<>();

        for (Long catalogId : request.catalogIds()) {
            MaterialCatalog catalog = catalogRepository.findDetailedById(catalogId).orElse(null);
            if (catalog == null) {
                skippedMaterials.add(skip(catalogId, MaterialImportSkipReason.NOT_FOUND));
                continue;
            }
            if (!Boolean.TRUE.equals(catalog.getActive())) {
                skippedMaterials.add(skip(catalogId, MaterialImportSkipReason.INACTIVE_CATALOG_MATERIAL));
                continue;
            }
            if (materialRepository.existsByTenantIdAndCatalogId(tenantId, catalogId)) {
                skippedMaterials.add(skip(catalogId, MaterialImportSkipReason.ALREADY_IMPORTED));
                continue;
            }

            String code = tenantCodePrefix + catalog.getCode().trim().toUpperCase(Locale.ROOT);
            if (materialRepository.existsByTenantIdAndCode(tenantId, code)) {
                skippedMaterials.add(skip(catalogId, MaterialImportSkipReason.CODE_ALREADY_EXISTS));
                continue;
            }

            Material material = new Material();
            material.setTenantId(tenantId);
            material.setCatalog(catalog);
            material.setCategory(catalog.getCategory());
            material.setStockUom(catalog.getDefaultStockUom());
            material.setDisplayUom(catalog.getDefaultDisplayUom());
            material.setCode(code);
            material.setName(catalog.getName());
            material.setNameAr(catalog.getNameAr());
            material.setMinimumStockLevel(BigDecimal.ZERO);
            material.setActive(true);

            createdMaterials.add(materialMapper.toResponse(materialRepository.save(material)));
        }

        return new ImportMaterialsResponse(
                request.catalogIds().size(),
                createdMaterials.size(),
                skippedMaterials.size(),
                createdMaterials,
                skippedMaterials
        );
    }

    private Material findMaterial(Long tenantId, Long id) {
        return materialRepository.findDetailedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material not found: " + id));
    }

    private void applyCreateFields(
            Long tenantId,
            Material material,
            CreateMaterialRequest request,
            String code
    ) {
        material.setCategory(findAccessibleCategory(tenantId, request.categoryId()));
        UomPair uomPair = findCompatibleActiveUomPair(request.stockUomId(), request.displayUomId());
        material.setStockUom(uomPair.stockUom());
        material.setDisplayUom(uomPair.displayUom());
        material.setCode(code);
        material.setName(trimRequired(request.name(), "name"));
        material.setNameAr(trimToNull(request.nameAr()));
        material.setMinimumStockLevel(normalizeMinimumStockLevel(request.minimumStockLevel()));
        material.setActive(request.active() == null || request.active());
        material.setNotes(trimToNull(request.notes()));
    }

    private void applyUpdateFields(
            Long tenantId,
            Material material,
            UpdateMaterialRequest request,
            String code
    ) {
        material.setCategory(findAccessibleCategory(tenantId, request.categoryId()));
        UomPair uomPair = findCompatibleActiveUomPair(request.stockUomId(), request.displayUomId());
        material.setStockUom(uomPair.stockUom());
        material.setDisplayUom(uomPair.displayUom());
        material.setCode(code);
        material.setName(trimRequired(request.name(), "name"));
        material.setNameAr(trimToNull(request.nameAr()));
        material.setMinimumStockLevel(normalizeMinimumStockLevel(request.minimumStockLevel()));
        if (request.active() != null) {
            material.setActive(request.active());
        }
        material.setNotes(trimToNull(request.notes()));
    }

    private MaterialCategory findAccessibleCategory(Long tenantId, Long categoryId) {
        return categoryRepository.findAccessibleById(categoryId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid material category: " + categoryId));
    }

    private UomPair findCompatibleActiveUomPair(Long stockUomId, Long displayUomId) {
        Uom stockUom = findActiveUom(stockUomId, "stockUomId");
        Uom displayUom = findActiveUom(displayUomId, "displayUomId");
        if (stockUom.getType() != displayUom.getType()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stock UOM and display UOM must have the same type");
        }
        return new UomPair(stockUom, displayUom);
    }

    private Uom findActiveUom(Long uomId, String fieldName) {
        if (uomId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        Uom uom = uomRepository.findById(uomId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + uomId));
        if (!Boolean.TRUE.equals(uom.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is inactive: " + uomId);
        }
        return uom;
    }

    private BigDecimal normalizeMinimumStockLevel(BigDecimal minimumStockLevel) {
        if (minimumStockLevel == null) {
            return BigDecimal.ZERO;
        }
        if (minimumStockLevel.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "minimumStockLevel must be greater than or equal to 0");
        }
        return minimumStockLevel;
    }

    private String validateAndNormalizeMaterialCode(Long tenantId, String requestedCode) {
        String code = trimRequired(requestedCode, "code").toUpperCase(Locale.ROOT);
        String expectedPrefix = getTenantCodePrefix(tenantId);
        if (!code.startsWith(expectedPrefix)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Material code must start with " + expectedPrefix);
        }
        return code;
    }

    private String getTenantCodePrefix(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid tenant id: " + tenantId));
        return tenant.getCode().trim().toUpperCase(Locale.ROOT) + "-";
    }

    private SkippedMaterialImportResponse skip(Long catalogId, MaterialImportSkipReason reason) {
        return new SkippedMaterialImportResponse(catalogId, reason);
    }

    private record UomPair(Uom stockUom, Uom displayUom) {
    }
}
