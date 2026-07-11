# Fix #10 — Thread safety on optimize()

> Resolves: concurrent requests racing on `previousAllocations` read/write

## Problem
`ChargingOptimizerService` is a Spring singleton. `previousAllocations` is a mutable field.
If two HTTP requests hit `/api/orderupdate` concurrently, both threads enter `optimize()`,
both read the same `previousAllocations`, compute different deltas, and one stomps the other's
write. This could cause duplicate BUY orders or missed SELL orders.

## Fix
Wrapped `optimize()` in `synchronized(lock)` so only one optimization runs at a time.

## Implementation logic

```kotlin
private val lock = Any()
private var previousAllocations: Map<AllocationKey, BigDecimal> = emptyMap()

fun optimize(orderBook: OrderBook): ChargingPlan = synchronized(lock) {
    val chargedSoFar = purchaseTrackerService.getChargedPerGroup()
    val plan = optimizePlan(orderBook, chargedSoFar)

    val newAllocations = buildAllocationMap(plan)
    val delta = computeDelta(previousAllocations, newAllocations)

    // ... process delta (market orders, purchases, signals) ...

    previousAllocations = newAllocations
    return plan
}
```

The `synchronized(lock)` block ensures:
1. Only one thread reads `previousAllocations` at a time
2. The write of `previousAllocations = newAllocations` completes before the next thread reads
3. Market order + purchase + signal emissions for a single delta are atomic as a group

`optimizePlan()` is NOT synchronized — it's stateless (pure function) and safe to call concurrently
for testing or read-only planning.

The lock is a dedicated `Any()` instance rather than `this` to avoid accidental contention
with other synchronized blocks on the same object.

## Files changed
- `service/ChargingOptimizedService.kt` — added `lock`, wrapped `optimize()` in `synchronized`