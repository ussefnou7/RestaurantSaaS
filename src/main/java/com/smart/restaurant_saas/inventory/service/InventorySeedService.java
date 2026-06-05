package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.response.InventorySeedSummaryResponse;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.MaterialCatalog;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.Supplier;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import com.smart.restaurant_saas.inventory.enums.UomType;
import com.smart.restaurant_saas.inventory.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.repository.MaterialCatalogRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.SupplierRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
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
public class InventorySeedService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final UomRepository uomRepository;
    private final MaterialCategoryRepository categoryRepository;
    private final MaterialCatalogRepository catalogRepository;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public InventorySeedSummaryResponse seedGlobalCatalog() {
        SeedSummary summary = new SeedSummary();

        int sortOrder = 10;
        for (UomSeed seed : uomSeeds()) {
            upsertUom(seed.withSortOrder(sortOrder), summary);
            sortOrder += 10;
        }

        sortOrder = 10;
        for (CategorySeed seed : categorySeeds()) {
            upsertGlobalCategory(seed.withSortOrder(sortOrder), summary);
            sortOrder += 10;
        }

        sortOrder = 10;
        for (CatalogSeed seed : catalogSeeds()) {
            upsertCatalogMaterial(seed.withSortOrder(sortOrder), summary);
            sortOrder += 10;
        }

        return summary.toResponse();
    }

    @Transactional
    public InventorySeedSummaryResponse seedDemoTenantData(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId));
        String tenantCode = tenant.getCode().trim().toUpperCase(Locale.ROOT);
        SeedSummary summary = new SeedSummary();

        for (WarehouseSeed seed : warehouseSeeds()) {
            upsertWarehouse(tenantId, tenantCode, seed, summary);
        }

        for (SupplierSeed seed : supplierSeeds()) {
            upsertSupplier(tenantId, tenantCode, seed, summary);
        }

        for (String catalogCode : demoMaterialCatalogCodes()) {
            upsertDemoMaterial(tenantId, tenantCode, catalogCode, summary);
        }

        return summary.toResponse();
    }

    private void upsertUom(UomSeed seed, SeedSummary summary) {
        Uom uom = uomRepository.findByCode(seed.code()).orElse(null);
        if (uom == null) {
            uom = new Uom();
            uom.setCode(seed.code());
            uom.setName(seed.name());
            uom.setNameAr(seed.nameAr());
            uom.setSymbol(seed.symbol());
            uom.setType(seed.type());
            uom.setBaseCode(seed.baseCode());
            uom.setFactorToBase(seed.factorToBase());
            uom.setActive(true);
            uom.setSortOrder(seed.sortOrder());
            uomRepository.save(uom);
            summary.created("Created UOM " + seed.code());
            return;
        }

        if (fillMissingNameAr(uom, seed.nameAr())) {
            summary.updated("Updated UOM " + seed.code() + " Arabic name");
            return;
        }

        summary.skipped("Skipped UOM " + seed.code() + " because it already exists");
    }

    private void upsertGlobalCategory(CategorySeed seed, SeedSummary summary) {
        MaterialCategory category = categoryRepository.findByTenantIdIsNullAndCode(seed.code()).orElse(null);
        if (category == null) {
            category = new MaterialCategory();
            category.setTenantId(null);
            category.setCode(seed.code());
            category.setName(seed.name());
            category.setNameAr(seed.nameAr());
            category.setActive(true);
            category.setSortOrder(seed.sortOrder());
            categoryRepository.save(category);
            summary.created("Created material category " + seed.code());
            return;
        }

        if (fillMissingNameAr(category, seed.nameAr())) {
            summary.updated("Updated material category " + seed.code() + " Arabic name");
            return;
        }

        summary.skipped("Skipped material category " + seed.code() + " because it already exists");
    }

    private void upsertCatalogMaterial(CatalogSeed seed, SeedSummary summary) {
        MaterialCategory category = categoryRepository.findByTenantIdIsNullAndCode(seed.categoryCode()).orElse(null);
        if (category == null) {
            summary.skipped("Skipped catalog material " + seed.code()
                    + " because category " + seed.categoryCode() + " was not found");
            return;
        }

        Uom defaultStockUom = uomRepository.findByCode(seed.stockUomCode()).orElse(null);
        if (defaultStockUom == null) {
            summary.skipped("Skipped catalog material " + seed.code()
                    + " because stock UOM " + seed.stockUomCode() + " was not found");
            return;
        }

        Uom defaultDisplayUom = uomRepository.findByCode(seed.displayUomCode()).orElse(null);
        if (defaultDisplayUom == null) {
            summary.skipped("Skipped catalog material " + seed.code()
                    + " because display UOM " + seed.displayUomCode() + " was not found");
            return;
        }
        if (defaultStockUom.getType() != defaultDisplayUom.getType()) {
            summary.skipped("Skipped catalog material " + seed.code()
                    + " because stock/display UOM types are not compatible");
            return;
        }

        MaterialCatalog material = catalogRepository.findByCode(seed.code()).orElse(null);
        if (material == null) {
            material = new MaterialCatalog();
            material.setCategory(category);
            material.setDefaultStockUom(defaultStockUom);
            material.setDefaultDisplayUom(defaultDisplayUom);
            material.setCode(seed.code());
            material.setName(seed.name());
            material.setNameAr(seed.nameAr());
            material.setActive(true);
            material.setSortOrder(seed.sortOrder());
            catalogRepository.save(material);
            summary.created("Created catalog material " + seed.code());
            return;
        }

        boolean updated = fillMissingNameAr(material, seed.nameAr());
        if (material.getDefaultStockUom() == null
                || !material.getDefaultStockUom().getId().equals(defaultStockUom.getId())) {
            material.setDefaultStockUom(defaultStockUom);
            updated = true;
        }
        if (material.getDefaultDisplayUom() == null
                || !material.getDefaultDisplayUom().getId().equals(defaultDisplayUom.getId())) {
            material.setDefaultDisplayUom(defaultDisplayUom);
            updated = true;
        }
        if (updated) {
            summary.updated("Updated catalog material " + seed.code() + " seed fields");
            return;
        }

        summary.skipped("Skipped catalog material " + seed.code() + " because it already exists");
    }

    private void upsertWarehouse(Long tenantId, String tenantCode, WarehouseSeed seed, SeedSummary summary) {
        String code = tenantCode + "-" + seed.codeSuffix();
        Warehouse warehouse = warehouseRepository.findByTenantIdAndCode(tenantId, code).orElse(null);
        if (warehouse == null) {
            warehouse = new Warehouse();
            warehouse.setTenantId(tenantId);
            warehouse.setBranch(null);
            warehouse.setCode(code);
            warehouse.setName(seed.name());
            warehouse.setNameAr(seed.nameAr());
            warehouse.setType(seed.type());
            warehouse.setActive(true);
            warehouseRepository.save(warehouse);
            summary.created("Created warehouse " + code);
            return;
        }

        if (fillMissingNameAr(warehouse, seed.nameAr())) {
            summary.updated("Updated warehouse " + code + " Arabic name");
            return;
        }

        summary.skipped("Skipped warehouse " + code + " because it already exists");
    }

    private void upsertSupplier(Long tenantId, String tenantCode, SupplierSeed seed, SeedSummary summary) {
        String code = tenantCode + "-" + seed.codeSuffix();
        Supplier supplier = supplierRepository.findByTenantIdAndCode(tenantId, code).orElse(null);
        if (supplier == null) {
            supplier = new Supplier();
            supplier.setTenantId(tenantId);
            supplier.setCode(code);
            supplier.setName(seed.name());
            supplier.setNameAr(seed.nameAr());
            supplier.setActive(true);
            supplierRepository.save(supplier);
            summary.created("Created supplier " + code);
            return;
        }

        if (fillMissingNameAr(supplier, seed.nameAr())) {
            summary.updated("Updated supplier " + code + " Arabic name");
            return;
        }

        summary.skipped("Skipped supplier " + code + " because it already exists");
    }

    private void upsertDemoMaterial(Long tenantId, String tenantCode, String catalogCode, SeedSummary summary) {
        MaterialCatalog catalog = catalogRepository.findByCode(catalogCode).orElse(null);
        if (catalog == null) {
            summary.skipped("Skipped demo material " + catalogCode + " because catalog material was not found");
            return;
        }
        if (!Boolean.TRUE.equals(catalog.getActive())) {
            summary.skipped("Skipped demo material " + catalogCode + " because catalog material is inactive");
            return;
        }

        String code = tenantCode + "-" + catalog.getCode();
        Material existingByCode = materialRepository.findByTenantIdAndCode(tenantId, code).orElse(null);
        if (existingByCode != null) {
            MaterialCatalog existingCatalog = existingByCode.getCatalog();
            if (existingCatalog == null || !existingCatalog.getId().equals(catalog.getId())) {
                summary.skipped("Skipped demo material " + code + " because the tenant code already exists");
                return;
            }
            boolean updated = fillMissingNameAr(existingByCode, catalog.getNameAr());
            if (existingByCode.getStockUom() == null
                    || !existingByCode.getStockUom().getId().equals(catalog.getDefaultStockUom().getId())) {
                existingByCode.setStockUom(catalog.getDefaultStockUom());
                updated = true;
            }
            if (existingByCode.getDisplayUom() == null
                    || !existingByCode.getDisplayUom().getId().equals(catalog.getDefaultDisplayUom().getId())) {
                existingByCode.setDisplayUom(catalog.getDefaultDisplayUom());
                updated = true;
            }
            if (updated) {
                summary.updated("Updated demo material " + code + " seed fields");
                return;
            }
            summary.skipped("Skipped demo material " + code + " because it already exists");
            return;
        }

        if (materialRepository.existsByTenantIdAndCatalogId(tenantId, catalog.getId())) {
            summary.skipped("Skipped demo material " + code + " because catalog material is already imported");
            return;
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
        material.setMinimumStockLevel(ZERO);
        material.setActive(true);
        materialRepository.save(material);
        summary.created("Created demo material " + code);
    }

    private boolean fillMissingNameAr(Uom uom, String nameAr) {
        if (trimToNull(uom.getNameAr()) != null || trimToNull(nameAr) == null) {
            return false;
        }
        uom.setNameAr(nameAr);
        return true;
    }

    private boolean fillMissingNameAr(MaterialCategory category, String nameAr) {
        if (trimToNull(category.getNameAr()) != null || trimToNull(nameAr) == null) {
            return false;
        }
        category.setNameAr(nameAr);
        return true;
    }

    private boolean fillMissingNameAr(MaterialCatalog material, String nameAr) {
        if (trimToNull(material.getNameAr()) != null || trimToNull(nameAr) == null) {
            return false;
        }
        material.setNameAr(nameAr);
        return true;
    }

    private boolean fillMissingNameAr(Material material, String nameAr) {
        if (trimToNull(material.getNameAr()) != null || trimToNull(nameAr) == null) {
            return false;
        }
        material.setNameAr(nameAr);
        return true;
    }

    private boolean fillMissingNameAr(Warehouse warehouse, String nameAr) {
        if (trimToNull(warehouse.getNameAr()) != null || trimToNull(nameAr) == null) {
            return false;
        }
        warehouse.setNameAr(nameAr);
        return true;
    }

    private boolean fillMissingNameAr(Supplier supplier, String nameAr) {
        if (trimToNull(supplier.getNameAr()) != null || trimToNull(nameAr) == null) {
            return false;
        }
        supplier.setNameAr(nameAr);
        return true;
    }

    private List<UomSeed> uomSeeds() {
        return List.of(
                new UomSeed("GRAM", "Gram", "جرام", "g", UomType.WEIGHT, "GRAM", new BigDecimal("1"), null),
                new UomSeed("KG", "Kilogram", "كيلوجرام", "kg", UomType.WEIGHT, "GRAM", new BigDecimal("1000"), null),
                new UomSeed("ML", "Milliliter", "ملليلتر", "ml", UomType.VOLUME, "ML", new BigDecimal("1"), null),
                new UomSeed("LITER", "Liter", "لتر", "L", UomType.VOLUME, "ML", new BigDecimal("1000"), null),
                new UomSeed("PCS", "Piece", "قطعة", "pcs", UomType.COUNT, "PCS", new BigDecimal("1"), null)
        );
    }

    private List<CategorySeed> categorySeeds() {
        return List.of(
                new CategorySeed("VEGETABLES", "Vegetables", "خضروات", null),
                new CategorySeed("FRUITS", "Fruits", "فواكه", null),
                new CategorySeed("MEAT", "Meat", "لحوم", null),
                new CategorySeed("CHICKEN", "Chicken", "دجاج", null),
                new CategorySeed("SEAFOOD", "Seafood", "مأكولات بحرية", null),
                new CategorySeed("DAIRY", "Dairy", "ألبان", null),
                new CategorySeed("BAKERY", "Bakery", "مخبوزات", null),
                new CategorySeed("SAUCES", "Sauces", "صوصات", null),
                new CategorySeed("SPICES", "Spices", "توابل", null),
                new CategorySeed("OILS", "Oils", "زيوت", null),
                new CategorySeed("DRINKS", "Drinks", "مشروبات", null),
                new CategorySeed("PACKAGING", "Packaging", "تغليف", null),
                new CategorySeed("CLEANING", "Cleaning", "منظفات", null),
                new CategorySeed("FROZEN", "Frozen Items", "مجمدات", null),
                new CategorySeed("OTHER", "Other", "أخرى", null)
        );
    }

    private List<CatalogSeed> catalogSeeds() {
        return List.of(
                new CatalogSeed("TOMATO", "Tomato", "طماطم", "VEGETABLES", "GRAM", null),
                new CatalogSeed("POTATO", "Potato", "بطاطس", "VEGETABLES", "GRAM", null),
                new CatalogSeed("ONION", "Onion", "بصل", "VEGETABLES", "GRAM", null),
                new CatalogSeed("GARLIC", "Garlic", "ثوم", "VEGETABLES", "GRAM", null),
                new CatalogSeed("LETTUCE", "Lettuce", "خس", "VEGETABLES", "GRAM", null),
                new CatalogSeed("CUCUMBER", "Cucumber", "خيار", "VEGETABLES", "GRAM", null),
                new CatalogSeed("CARROT", "Carrot", "جزر", "VEGETABLES", "GRAM", null),
                new CatalogSeed("GREEN_PEPPER", "Green Pepper", "فلفل أخضر", "VEGETABLES", "GRAM", null),
                new CatalogSeed("MUSHROOM", "Mushroom", "مشروم", "VEGETABLES", "GRAM", null),
                new CatalogSeed("RICE", "Rice", "أرز", "OTHER", "GRAM", null),
                new CatalogSeed("BEEF", "Beef", "لحم بقري", "MEAT", "GRAM", null),
                new CatalogSeed("MINCED_BEEF", "Minced Beef", "لحم مفروم", "MEAT", "GRAM", null),
                new CatalogSeed("BURGER_PATTY", "Burger Patty", "برجر لحم", "MEAT", "GRAM", null),
                new CatalogSeed("CHICKEN_BREAST", "Chicken Breast", "صدور دجاج", "CHICKEN", "GRAM", null),
                new CatalogSeed("CHICKEN_THIGH", "Chicken Thigh", "أوراك دجاج", "CHICKEN", "GRAM", null),
                new CatalogSeed("CHICKEN_STRIPS", "Chicken Strips", "شرائح دجاج", "CHICKEN", "GRAM", null),
                new CatalogSeed("CHICKEN_WINGS", "Chicken Wings", "أجنحة دجاج", "CHICKEN", "GRAM", null),
                new CatalogSeed("CHEDDAR_CHEESE", "Cheddar Cheese", "جبنة شيدر", "DAIRY", "GRAM", null),
                new CatalogSeed("MOZZARELLA", "Mozzarella", "موزاريلا", "DAIRY", "GRAM", null),
                new CatalogSeed("MILK", "Milk", "لبن", "DAIRY", "ML", null),
                new CatalogSeed("BUTTER", "Butter", "زبدة", "DAIRY", "GRAM", null),
                new CatalogSeed("CREAM", "Cream", "كريمة", "DAIRY", "ML", null),
                new CatalogSeed("BURGER_BREAD", "Burger Bread", "خبز برجر", "BAKERY", "PCS", null),
                new CatalogSeed("SANDWICH_BREAD", "Sandwich Bread", "خبز ساندوتش", "BAKERY", "PCS", null),
                new CatalogSeed("PIZZA_DOUGH", "Pizza Dough", "عجينة بيتزا", "BAKERY", "GRAM", null),
                new CatalogSeed("TORTILLA_BREAD", "Tortilla Bread", "خبز تورتيلا", "BAKERY", "PCS", null),
                new CatalogSeed("KETCHUP", "Ketchup", "كاتشب", "SAUCES", "ML", null),
                new CatalogSeed("MAYONNAISE", "Mayonnaise", "مايونيز", "SAUCES", "GRAM", null),
                new CatalogSeed("MUSTARD", "Mustard", "مستردة", "SAUCES", "ML", null),
                new CatalogSeed("BBQ_SAUCE", "BBQ Sauce", "صوص باربكيو", "SAUCES", "ML", null),
                new CatalogSeed("HOT_SAUCE", "Hot Sauce", "صوص حار", "SAUCES", "ML", null),
                new CatalogSeed("COOKING_OIL", "Cooking Oil", "زيت طهي", "OILS", "ML", null),
                new CatalogSeed("OLIVE_OIL", "Olive Oil", "زيت زيتون", "OILS", "ML", null),
                new CatalogSeed("SALT", "Salt", "ملح", "SPICES", "GRAM", null),
                new CatalogSeed("BLACK_PEPPER", "Black Pepper", "فلفل أسود", "SPICES", "GRAM", null),
                new CatalogSeed("PAPRIKA", "Paprika", "بابريكا", "SPICES", "GRAM", null),
                new CatalogSeed("WATER_BOTTLE", "Water Bottle", "زجاجة مياه", "DRINKS", "PCS", null),
                new CatalogSeed("COLA_CAN", "Cola Can", "كانز كولا", "DRINKS", "PCS", null),
                new CatalogSeed("ORANGE_JUICE", "Orange Juice", "عصير برتقال", "DRINKS", "ML", null),
                new CatalogSeed("COFFEE_BEANS", "Coffee Beans", "بن قهوة", "DRINKS", "GRAM", null),
                new CatalogSeed("TEA", "Tea", "شاي", "DRINKS", "GRAM", null),
                new CatalogSeed("SUGAR", "Sugar", "سكر", "DRINKS", "GRAM", null),
                new CatalogSeed("PAPER_CUP", "Paper Cup", "كوب ورقي", "PACKAGING", "PCS", null),
                new CatalogSeed("PLASTIC_CUP", "Plastic Cup", "كوب بلاستيك", "PACKAGING", "PCS", null),
                new CatalogSeed("TAKEAWAY_BAG", "Takeaway Bag", "شنطة تيك أواي", "PACKAGING", "PCS", null),
                new CatalogSeed("BURGER_BOX", "Burger Box", "علبة برجر", "PACKAGING", "PCS", null),
                new CatalogSeed("PIZZA_BOX", "Pizza Box", "علبة بيتزا", "PACKAGING", "PCS", null),
                new CatalogSeed("NAPKIN", "Napkin", "منديل", "PACKAGING", "PCS", null),
                new CatalogSeed("STRAW", "Straw", "شفاطة", "PACKAGING", "PCS", null),
                new CatalogSeed("DISH_SOAP", "Dish Soap", "صابون أطباق", "CLEANING", "ML", null),
                new CatalogSeed("HAND_SANITIZER", "Hand Sanitizer", "مطهر يدين", "CLEANING", "ML", null),
                new CatalogSeed("FLOOR_CLEANER", "Floor Cleaner", "منظف أرضيات", "CLEANING", "ML", null),
                new CatalogSeed("TRASH_BAG", "Trash Bag", "كيس قمامة", "CLEANING", "PCS", null)
        );
    }

    private List<WarehouseSeed> warehouseSeeds() {
        return List.of(
                new WarehouseSeed("MAIN-WH", "Main Warehouse", "المخزن الرئيسي", WarehouseType.CENTRAL),
                new WarehouseSeed("BRANCH-STORE", "Branch Store", "مخزن الفرع", WarehouseType.BRANCH),
                new WarehouseSeed("KITCHEN-STORE", "Kitchen Store", "مخزن المطبخ", WarehouseType.KITCHEN),
                new WarehouseSeed("FREEZER-01", "Main Freezer", "الفريزر الرئيسي", WarehouseType.FREEZER),
                new WarehouseSeed("BAR-STORE", "Bar Store", "مخزن البار", WarehouseType.BAR)
        );
    }

    private List<SupplierSeed> supplierSeeds() {
        return List.of(
                new SupplierSeed("SUP-VEG", "Vegetables Supplier", "مورد الخضار"),
                new SupplierSeed("SUP-MEAT", "Meat Supplier", "مورد اللحوم"),
                new SupplierSeed("SUP-CHICKEN", "Chicken Supplier", "مورد الدجاج"),
                new SupplierSeed("SUP-DAIRY", "Dairy Supplier", "مورد الألبان"),
                new SupplierSeed("SUP-PACKAGING", "Packaging Supplier", "مورد التغليف"),
                new SupplierSeed("SUP-CLEANING", "Cleaning Supplier", "مورد المنظفات")
        );
    }

    private List<String> demoMaterialCatalogCodes() {
        return List.of(
                "TOMATO",
                "POTATO",
                "ONION",
                "CHICKEN_BREAST",
                "BEEF",
                "CHEDDAR_CHEESE",
                "BURGER_BREAD",
                "COOKING_OIL",
                "SALT",
                "WATER_BOTTLE",
                "PAPER_CUP",
                "TAKEAWAY_BAG",
                "RICE"
        );
    }

    private record UomSeed(
            String code,
            String name,
            String nameAr,
            String symbol,
            UomType type,
            String baseCode,
            BigDecimal factorToBase,
            Integer sortOrder
    ) {

        private UomSeed withSortOrder(Integer sortOrder) {
            return new UomSeed(code, name, nameAr, symbol, type, baseCode, factorToBase, sortOrder);
        }
    }

    private record CategorySeed(String code, String name, String nameAr, Integer sortOrder) {

        private CategorySeed withSortOrder(Integer sortOrder) {
            return new CategorySeed(code, name, nameAr, sortOrder);
        }
    }

    private record CatalogSeed(
            String code,
            String name,
            String nameAr,
            String categoryCode,
            String stockUomCode,
            String displayUomCode,
            Integer sortOrder
    ) {

        private CatalogSeed(
                String code,
                String name,
                String nameAr,
                String categoryCode,
                String stockUomCode,
                Integer sortOrder
        ) {
            this(code, name, nameAr, categoryCode, stockUomCode, defaultDisplayUomCode(stockUomCode), sortOrder);
        }

        private CatalogSeed withSortOrder(Integer sortOrder) {
            return new CatalogSeed(code, name, nameAr, categoryCode, stockUomCode, displayUomCode, sortOrder);
        }

        private static String defaultDisplayUomCode(String stockUomCode) {
            return switch (stockUomCode) {
                case "GRAM" -> "KG";
                case "ML" -> "LITER";
                case "PCS" -> "PCS";
                default -> stockUomCode;
            };
        }
    }

    private record WarehouseSeed(String codeSuffix, String name, String nameAr, WarehouseType type) {
    }

    private record SupplierSeed(String codeSuffix, String name, String nameAr) {
    }

    private static class SeedSummary {

        private int createdCount;
        private int updatedCount;
        private int skippedCount;
        private final List<String> messages = new ArrayList<>();

        private void created(String message) {
            createdCount++;
            messages.add(message);
        }

        private void updated(String message) {
            updatedCount++;
            messages.add(message);
        }

        private void skipped(String message) {
            skippedCount++;
            messages.add(message);
        }

        private InventorySeedSummaryResponse toResponse() {
            return new InventorySeedSummaryResponse(createdCount, updatedCount, skippedCount, messages);
        }
    }
}
