package com.smart.restaurant_saas.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * "How many, worth how much, and since when" for one branch or warehouse — the read shape every
 * dashboard alert condition reduces to.
 *
 * <p><b>Why this sits in {@code common} rather than in a module.</b> Five modules answer this
 * question — orders, shifts, purchasing, stock and counts — because the dashboard asks all five the
 * same thing. Declaring it in any one of them would make the other four depend on that module for
 * a type with no behaviour in it; declaring it in the dashboard package would make five module
 * repositories depend on the dashboard, which inverts the only dependency arrow that matters here.
 * Copying it five times is the worst of the three: the copies look identical on the day they are
 * written and drift the first time one query needs a sixth column. So it goes where the other
 * cross-module, behaviour-free types already are.
 *
 * <p><b>Every query must select all five labels, including the ones it has no value for.</b> These
 * are native-query interface projections mapped by column label, so an omitted label is not a null
 * — it is a missing mapping. Use an explicit {@code CAST(NULL AS bigint)} / {@code numeric} /
 * {@code timestamp} for the columns a condition genuinely has no answer for, which also forces the
 * author to decide that the answer is absent rather than leave it unconsidered.
 *
 * <p><b>A null is never a zero here.</b> {@code totalValue} null means the condition carries no
 * money; {@code oldestAt} null means the source row records no moment at which the condition began
 * — which is the normal case for a stock minimum, and is not the same statement as "it began just
 * now". Rendering either as 0 is the specific failure the dashboard's rule 5 exists to prevent.
 */
public interface ScopedConditionAggregate {

    /** The branch, or null for a scope that has none — a central warehouse, or tenant-wide. */
    Long getBranchId();

    /** The warehouse, or null where the condition is not per-warehouse. */
    Long getWarehouseId();

    /** Underlying rows in this condition, in this scope. */
    Long getItemCount();

    /** EGP at stake, or null where the condition carries no money. */
    BigDecimal getTotalValue();

    /** When the oldest instance began, or null where the source records no such moment. */
    LocalDateTime getOldestAt();
}
