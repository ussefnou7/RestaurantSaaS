package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.device.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * A drawer's account of one period. The drawer is the device (D119), so the device is what a
 * shift belongs to -- not the cashier, who is recorded as an actor on each end of it.
 *
 * <p><b>Branch is deliberately absent.</b> It is reached through {@link Device#getBranch()}. A
 * branch column here would be a second copy that goes stale, and it is what let an order attach
 * to a shift in another branch before this rewrite.
 *
 * <p><b>A CLOSED shift is immutable.</b> Nothing updates one. {@code chk_shift_close_fields}
 * (V56) additionally makes the two states structurally disjoint, so an OPEN row cannot carry an
 * expected figure for a read path to leak before the count is taken (D123).
 */
@Getter
@Setter
@Entity
@Table(name = "shift")
public class Shift extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /**
     * Fixed at open and never derived from {@code openedAt} at read time (D120): a shift opening
     * at 22:00 and closing at 03:00 belongs entirely to the earlier day.
     */
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /**
     * Both actors are the JWT principal, never a header. Held as scalar ids because the cashier's
     * name is resolved on read (D124) -- a denormalised name is a second copy that goes stale.
     */
    @Column(name = "opened_by_user_id", nullable = false)
    private Long openedByUserId;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    /** Derived from {@code closedBy != openedBy} (D122). Never accepted from the client. */
    @Column(name = "forced_close", nullable = false)
    private Boolean forcedClose = false;

    @Column(name = "opening_count", nullable = false, precision = 18, scale = 6)
    private BigDecimal openingCount;

    @Column(name = "closing_count", precision = 18, scale = 6)
    private BigDecimal closingCount;

    /**
     * {@code openingCount - the previous closed shift's closingCount} on this device, covering a
     * window in which the drawer sat closed. Null on a device's first shift -- there is no prior
     * count, and a zero would be a fabricated finding (D121).
     */
    @Column(name = "handover_variance", precision = 18, scale = 6)
    private BigDecimal handoverVariance;

    /**
     * Stored at close and never returned to a POS caller (D123). Present in the row so the figure
     * the variance was computed from survives, not so it can be read back at the till.
     */
    @Column(name = "expected_cash", precision = 18, scale = 6)
    private BigDecimal expectedCash;

    @Column(name = "variance", precision = 18, scale = 6)
    private BigDecimal variance;

    /** Frozen in the close transaction. Expenses arriving later never move it (D124). */
    @Column(name = "expenses_at_close", precision = 18, scale = 6)
    private BigDecimal expensesAtClose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ShiftStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
