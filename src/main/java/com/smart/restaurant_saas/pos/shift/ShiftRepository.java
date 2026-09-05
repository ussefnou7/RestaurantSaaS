package com.smart.restaurant_saas.pos.shift;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * The one open shift on a drawer. Keyed on the device, which is what makes an order's branch
     * implied rather than checked (D119) -- the device belongs to one branch, so a shift reached
     * this way cannot belong to another.
     *
     * <p>The branch is fetched along with the device because every caller needs it: for the
     * tenant/branch zone (D101) on the write paths, and for the response on the read ones.
     */
    @EntityGraph(attributePaths = {"device", "device.branch"})
    Optional<Shift> findByDeviceIdAndTenantIdAndStatus(
            Long deviceId, Long tenantId, ShiftStatus status);

    /**
     * The shift whose closing count establishes this one's opening baseline, for
     * {@code handoverVariance} (D121). Ordered by {@code closedAt} rather than by id because the
     * predecessor is the last drawer to be counted, not the last row written.
     *
     * <p>Empty on a device's first ever shift, which is the case D121 requires be handled
     * explicitly rather than defaulted to zero.
     */
    Optional<Shift> findTopByDeviceIdAndTenantIdAndStatusOrderByClosedAtDesc(
            Long deviceId, Long tenantId, ShiftStatus status);

    /**
     * The shifts list (D125).
     *
     * <p><b>Ordered by the size of the variance, not by date</b>, and by magnitude rather than by
     * signed value: a drawer that persistently runs *over* is at least as strong a signal as one
     * that runs short, because it means money is coming in that the system was not told about
     * (D119). Sorting by signed variance would file every surplus at the far end of the list,
     * which removes half the detection. Open shifts have no variance yet and sort last.
     *
     * <p>The ordering is fixed here rather than left to a {@code Pageable} default so that it
     * cannot be silently replaced by a date sort — the screen exists to bring the anomalous to the
     * top, and a caller who wants a different order is asking a different question.
     */
    @Query("""
        SELECT s.id AS id,
               s.businessDate AS businessDate,
               d.id AS deviceId,
               d.name AS deviceName,
               d.branch.id AS branchId,
               d.branch.name AS branchName,
               s.openedByUserId AS openedByUserId,
               ou.fullName AS openedByUserName,
               s.closedByUserId AS closedByUserId,
               cu.fullName AS closedByUserName,
               s.openedAt AS openedAt,
               s.closedAt AS closedAt,
               s.status AS status,
               s.forcedClose AS forcedClose,
               s.openingCount AS openingCount,
               s.closingCount AS closingCount,
               s.expectedCash AS expectedCash,
               s.variance AS variance,
               s.handoverVariance AS handoverVariance,
               s.expensesAtClose AS expensesAtClose
        FROM Shift s
        JOIN s.device d
        LEFT JOIN User ou ON ou.id = s.openedByUserId AND ou.tenantId = s.tenantId
        LEFT JOIN User cu ON cu.id = s.closedByUserId AND cu.tenantId = s.tenantId
        WHERE s.tenantId = :tenantId
          AND (CAST(:branchId AS long) IS NULL OR d.branch.id = :branchId)
          AND (CAST(:deviceId AS long) IS NULL OR d.id = :deviceId)
          AND (CAST(:cashierUserId AS long) IS NULL OR s.openedByUserId = :cashierUserId)
          AND (CAST(:dateFrom AS LocalDate) IS NULL OR s.businessDate >= :dateFrom)
          AND (CAST(:dateTo AS LocalDate) IS NULL OR s.businessDate <= :dateTo)
          AND (CAST(:status AS string) IS NULL OR s.status = :status)
          AND (CAST(:forcedClose AS boolean) IS NULL OR s.forcedClose = :forcedClose)
        ORDER BY ABS(s.variance) DESC NULLS LAST, s.businessDate DESC, s.id DESC
        """)
    Page<ShiftListProjection> findListItems(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("deviceId") Long deviceId,
            @Param("cashierUserId") Long cashierUserId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("status") ShiftStatus status,
            @Param("forcedClose") Boolean forcedClose,
            Pageable pageable);

    @Query("""
        SELECT s.id AS id,
               s.businessDate AS businessDate,
               d.id AS deviceId,
               d.name AS deviceName,
               d.branch.id AS branchId,
               d.branch.name AS branchName,
               s.openedByUserId AS openedByUserId,
               ou.fullName AS openedByUserName,
               s.closedByUserId AS closedByUserId,
               cu.fullName AS closedByUserName,
               s.openedAt AS openedAt,
               s.closedAt AS closedAt,
               s.status AS status,
               s.forcedClose AS forcedClose,
               s.openingCount AS openingCount,
               s.closingCount AS closingCount,
               s.expectedCash AS expectedCash,
               s.variance AS variance,
               s.handoverVariance AS handoverVariance,
               s.expensesAtClose AS expensesAtClose
        FROM Shift s
        JOIN s.device d
        LEFT JOIN User ou ON ou.id = s.openedByUserId AND ou.tenantId = s.tenantId
        LEFT JOIN User cu ON cu.id = s.closedByUserId AND cu.tenantId = s.tenantId
        WHERE s.id = :id AND s.tenantId = :tenantId
        """)
    Optional<ShiftListProjection> findListItemById(
            @Param("id") Long id,
            @Param("tenantId") Long tenantId);

    /**
     * The shifts a manager may attribute an expense to (D124): this branch, recently.
     *
     * <p><b>Both filters are load-bearing.</b> The branch filter keeps an expense from being
     * charged to another branch's drawer. The window keeps the list readable -- unbounded, it
     * becomes unusable within months, and an explicit choice nobody can read is not an explicit
     * choice. The caller supplies {@code from}, so the default window is extendable rather than
     * fixed here.
     *
     * <p>Closed shifts are included and selectable. They are labelled by the client with the
     * consequence rather than just the state: linking to one does not move its recorded variance.
     *
     * <p>Ordered newest first because the shift being attributed is almost always a recent one.
     */
    @Query("""
        SELECT s.id AS id,
               s.businessDate AS businessDate,
               d.id AS deviceId,
               d.name AS deviceName,
               d.branch.id AS branchId,
               s.openedByUserId AS cashierUserId,
               u.fullName AS cashierName,
               s.openedAt AS openedAt,
               s.closedAt AS closedAt,
               s.status AS status
        FROM Shift s
        JOIN s.device d
        LEFT JOIN User u
          ON u.id = s.openedByUserId
         AND u.tenantId = s.tenantId
        WHERE s.tenantId = :tenantId
          AND d.branch.id = :branchId
          AND s.businessDate >= :from
        ORDER BY s.businessDate DESC, s.openedAt DESC
        """)
    List<SelectableShiftProjection> findSelectableForExpense(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDate from);
}
