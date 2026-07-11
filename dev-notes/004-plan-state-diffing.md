# Fix #4 — Plan state tracking and diffing

> Resolves: [TODO.md #6](../TODO.md#6-no-plan-diffing--state-tracking)

## Problem
The optimizer was stateless. Without knowing the previous plan, it cannot determine:
- Which quarters are newly allocated (need BUY orders)
- Which quarters were dropped (need SELL orders to offload)

## Fix
- Optimizer stores `previousPlan` after each optimization cycle
- On re-optimization, compares new plan vs previous plan per (group, quarter) pair
- Delta logic:
  - New allocation not in previous → emit BUY + record purchase
  - Previous allocation not in new → emit SELL (offload energy)
  - Same allocation, different quantity → emit delta BUY or SELL

## Implementation logic

**State:**
```kotlin
private data class AllocationKey(val groupId: String, val startTime: LocalDateTime, val endTime: LocalDateTime)
private var previousAllocations: Map<AllocationKey, BigDecimal> = emptyMap()
```

**Building the allocation map from a plan:**
```kotlin
private fun buildAllocationMap(plan: ChargingPlan): Map<AllocationKey, BigDecimal> {
    val map = mutableMapOf<AllocationKey, BigDecimal>()
    plan.groupAllocations.forEach { group ->
        group.allocations.forEach { alloc ->
            map[AllocationKey(group.groupId, alloc.startTime, alloc.endTime)] = alloc.mwh
        }
    }
    return map
}
```

**Delta computation:**
```kotlin
private fun computeDelta(previous: Map<AllocationKey, BigDecimal>, current: Map<AllocationKey, BigDecimal>): Map<AllocationKey, BigDecimal> {
    val allKeys = previous.keys + current.keys
    val delta = mutableMapOf<AllocationKey, BigDecimal>()
    for (key in allKeys) {
        val diff = (current[key] ?: ZERO) - (previous[key] ?: ZERO)
        if (diff.compareTo(ZERO) != 0) delta[key] = diff
    }
    return delta
}
```

Positive diff = need to buy more. Negative diff = need to sell back.
After processing, `previousAllocations = newAllocations` so next cycle diffs against this.

## Files changed
- `service/ChargingOptimizerService.kt` — added previousAllocations map, diffing logic