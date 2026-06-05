package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.enums.UomType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UomRepository extends JpaRepository<Uom, Long> {

    boolean existsByCode(String code);

    Optional<Uom> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("""
            select u
            from Uom u
            where (:type is null or u.type = :type)
              and (:active is null or u.active = :active)
            order by case when u.sortOrder is null then 1 else 0 end,
                     u.sortOrder asc,
                     u.name asc,
                     u.id asc
            """)
    List<Uom> findByFilters(
            @Param("type") UomType type,
            @Param("active") Boolean active
    );
}
