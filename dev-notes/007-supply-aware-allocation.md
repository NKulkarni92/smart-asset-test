# Fix #7 — Supply-aware allocation across groups

> Resolves: multiple groups over-allocating from the same quarter's limited supply

## Problem
The optimizer evaluated each group independently against the full order book.
If two groups both targeted the same cheap quarter, they'd each plan to buy the full available
quantity — exceeding what's actually on the market.

Example: quarter has 3 MWh for sale. Group A plans to buy 2 MWh. Group B also plans to buy 2 MWh.
Total planned = 4 MWh, but only 3 MWh exists.

## Fix
Introduced a shared `supplyUsed: MutableMap<DeliveryPeriod, BigDecimal>` that tracks how much
of each quarter's available supply has been claimed by earlier groups in the same optimization pass.

Each group's allocation is capped by: `min(maxPerQuarter, remainingNeed, availableSupply - alreadyUsed)`

Groups are processed in config order (A → F), which is fine since the PDF doesn't specify priority.

## Implementation logic

**New method on OrderBook:**
```kotlin
fun getAvailableSupply(period: DeliveryPeriod): BigDecimal {
    val asks = asksPerPeriod[period] ?: return BigDecimal.ZERO
    return asks.values.fold(BigDecimal.ZERO) { acc, qty -> acc + qty }
}
```
Sums ALL resting ask quantities across all price levels for that period.
This is the total energy available for purchase in that quarter.

**Shared supply tracking in optimizePlan:**
```kotlin
fun optimizePlan(orderBook: OrderBook, chargedSoFar: Map<String, BigDecimal> = emptyMap()): ChargingPlan {
    val supplyUsed = mutableMapOf<DeliveryPeriod, BigDecimal>()  // shared across groups

    val groupAllocations = chargingGroups.map { group ->
        optimizeGroup(group, orderBook, chargedSoFar[group.id] ?: BigDecimal.ZERO, supplyUsed)
    }
    ...
}
```

**Inside optimizeGroup — supply cap per quarter:**
```kotlin
val totalSupply = orderBook.getAvailableSupply(period)
val alreadyUsed = supplyUsed[period] ?: BigDecimal.ZERO
val availableSupply = (totalSupply - alreadyUsed).max(BigDecimal.ZERO)

if (availableSupply <= BigDecimal.ZERO) continue

val toAllocate = maxPerQuarter
    .min(remainingNeed - allocated)
    .min(availableSupply)            // NEW: capped by remaining supply

supplyUsed[period] = alreadyUsed + toAllocate  // claim it
```

Since groups are processed sequentially (A first, then B, etc.), earlier groups get first
pick of cheap quarters. Later groups see reduced available supply. The order follows config
sequence which is deterministic.

## Files changed
- `domain/OrderBook.kt` — added `getAvailableSupply(period)` method
- `service/ChargingOptimizedService.kt` — `optimizeGroup` now takes and updates `supplyUsed`
- `service/ChargingOptimizerServiceTest.kt` — added test for shared supply contention