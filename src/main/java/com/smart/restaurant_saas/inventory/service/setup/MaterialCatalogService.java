package com.smart.restaurant_saas.inventory.service.setup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.enums.MaterialImportSkipReason;
import com.smart.restaurant_saas.inventory.mapper.MaterialCatalogMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.material.MaterialCatalog;
import com.smart.restaurant_saas.inventory.material.dto.ImportMaterialsRequest;
import com.smart.restaurant_saas.inventory.material.dto.ImportMaterialsResponse;
import com.smart.restaurant_saas.inventory.material.dto.MaterialCatalogResponse;
import com.smart.restaurant_saas.inventory.material.dto.SkippedMaterialDto;
import com.smart.restaurant_saas.inventory.repository.MaterialCatalogRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialCatalogService {

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialRepository materialRepository;
    private final MaterialCatalogMapper mapper;

    @Transactional(readOnly = true)
    public List<MaterialCatalogResponse> findAll(Long tenantId, String search,
                                                 Long categoryId, Long uomId) {
        List<MaterialCatalog> items = catalogRepository.findByFilters(blankToNull(search), categoryId, uomId);
        Map<Long, Long> importedByCatalogId = importedMaterialIdsByCatalog(tenantId, items);
        return items.stream()
            .map(c -> mapper.toResponse(c, importedByCatalogId.get(c.getId())))
            .toList();
    }

    @Transactional
    public ImportMaterialsResponse importMaterials(ImportMaterialsRequest request, Long tenantId) {
        List<Long> catalogIds = request.getCatalogIds();

        Set<Long> alreadyImported = new HashSet<>(
            materialRepository.findAlreadyImportedCatalogIds(tenantId, catalogIds));
        Set<String> createdCodes = new HashSet<>();

        int created = 0;
        List<SkippedMaterialDto> skipped = new ArrayList<>();

        for (Long catalogId : catalogIds) {
            MaterialCatalog catalog = catalogRepository.findById(catalogId).orElse(null);

            if (catalog == null) {
                skipped.add(skip(catalogId, null, null, MaterialImportSkipReason.NOT_FOUND));
                continue;
            }
            if (Boolean.FALSE.equals(catalog.getActive())) {
                skipped.add(skip(catalogId, catalog.getCode(), catalog.getName(),
                    MaterialImportSkipReason.INACTIVE_CATALOG_MATERIAL));
                continue;
            }
            if (alreadyImported.contains(catalogId)) {
                skipped.add(skip(catalogId, catalog.getCode(), catalog.getName(),
                    MaterialImportSkipReason.ALREADY_IMPORTED));
                continue;
            }
            if (createdCodes.contains(catalog.getCode())
                    || materialRepository.existsByTenantIdAndCode(tenantId, catalog.getCode())) {
                skipped.add(skip(catalogId, catalog.getCode(), catalog.getName(),
                    MaterialImportSkipReason.CODE_ALREADY_EXISTS));
                continue;
            }

            materialRepository.save(buildMaterial(catalog, tenantId));
            created++;
            alreadyImported.add(catalogId);
            createdCodes.add(catalog.getCode());
        }

        log.info("Catalog import tenant={} requested={} created={} skipped={}",
            tenantId, catalogIds.size(), created, skipped.size());

        return ImportMaterialsResponse.builder()
            .requestedCount(catalogIds.size())
            .createdCount(created)
            .skippedCount(skipped.size())
            .skippedMaterials(skipped)
            .build();
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private Material buildMaterial(MaterialCatalog catalog, Long tenantId) {
        Material m = new Material();
        m.setTenantId(tenantId);
        m.setCatalog(catalog);
        m.setCategory(catalog.getCategory());
        m.setStockUom(catalog.getDefaultStockUom());
        m.setDisplayUom(catalog.getDefaultDisplayUom());
        m.setCode(catalog.getCode());
        m.setName(catalog.getName());
        m.setNameAr(catalog.getNameAr());
        m.setActive(true);
        return m;
    }

    private Map<Long, Long> importedMaterialIdsByCatalog(Long tenantId, List<MaterialCatalog> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> catalogIds = items.stream().map(MaterialCatalog::getId).toList();
        Map<Long, Long> map = new HashMap<>();
        for (Object[] pair : materialRepository.findImportedCatalogPairs(tenantId, catalogIds)) {
            map.put((Long) pair[0], (Long) pair[1]);
        }
        return map;
    }

    private SkippedMaterialDto skip(Long catalogId, String code, String name,
                                    MaterialImportSkipReason reason) {
        return SkippedMaterialDto.builder()
            .catalogId(catalogId)
            .code(code)
            .name(name)
            .reason(reason.name())
            .build();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
