# Fix #3 — Wire chargedSoFar into optimization

> Resolves: [TODO.md #4](../TODO.md#4-chargedsofar-never-wired-in)

## Problem
`optimize()` was called without passing previously purchased quantities.
The optimizer planned from scratch every time as if no energy had been bought yet.
Result: over-allocation and redundant steering signals.

## Fix
- Added `getChargedPerGroup()` to `PurchaseTrackerService` — aggregates purchased MWh by group
- Optimizer reads this before planning, subtracts already-charged energy from each group's need

## Implementation logic

**PurchaseTrackerService.getChargedPerGroup():**
```kotlin
fun getChargedPerGroup(): Map<String, BigDecimal> {
    return purchases.groupBy { it.groupId }
        .mapValues { (_, groupPurchases) -> groupPurchases.sumOf { it.quantity } }
}
```
Groups all purchase entries by `groupId`, then sums their quantities.
Returns e.g. `{"A" -> 3.5, "B" -> 7.0}` meaning group A has bought 3.5 MWh total.

**Optimizer.optimize():**
```kotlin
fun optimize(orderBook: OrderBook): ChargingPlan {
    val chargedSoFar = purchaseTrackerService.getChargedPerGroup()  // read actuals
    val plan = optimizePlan(orderBook, chargedSoFar)                // pass to planner
    ...
}
```

**Inside optimizeGroup():**
```kotlin
val remainingNeed = (group.neededChargeMWh - alreadyCharged).max(BigDecimal.ZERO)
```
If group A needs 5 MWh and already bought 3.5 MWh, it only allocates for the remaining 1.5 MWh.
The `.max(ZERO)` prevents negative needs if we over-bought.

## Files changed
- `service/PurchaseTrackerService.kt` — added `getChargedPerGroup()`
- `service/ChargingOptimizerService.kt` — passes chargedSoFar from tracker into planning