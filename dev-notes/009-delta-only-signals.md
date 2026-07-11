# Fix #9 — Delta-only steering signals

> Resolves: steering signals log growing with repeated identical entries on every optimization cycle

## Problem
On every order update, the optimizer re-emitted steering signals for ALL current allocations,
even if nothing changed. This caused the log to fill with duplicate signals, making it useless
for auditing actual charge commands.

## Fix
Steering signals are now emitted only for allocations that changed (the delta).

- New allocation → signal with the new charge power
- Dropped allocation → signal with power = 0 (implicit from delta key being in the map)
- Unchanged allocation → no signal emitted

The delta is already computed for market orders. Signals now piggyback on the same loop:
each delta entry emits one signal with the NEW power level for that (group, quarter) pair.

## Implementation logic

**Before (emit all, every time):**
```kotlin
// ran AFTER the delta loop
private fun emitSteeringSignals(plan: ChargingPlan) {
    plan.groupAllocations.forEach { groupAllocation ->
        groupAllocation.allocations.forEach { allocation ->
            steeringSignalClient.sendSignal(...)  // always emits everything
        }
    }
}
```
This produced N signals per order update where N = total allocated quarters.
If nothing changed, you'd still get N identical signals.

**After (emit only deltas):**
```kotlin
for ((key, change) in delta) {
    // ... market order logic ...

    steeringSignalClient.sendSignal(
        SteeringSignal(
            groupId = key.groupId,
            quarterStart = key.startTime,
            quarterEnd = key.endTime,
            chargePowerMW = (newAllocations[key] ?: BigDecimal.ZERO).divide(quarterDurationHours)
        )
    )
}
```

Key insight: the signal carries the **new** power level, not the delta.
- If allocation increased: signal says "charge at X MW" (higher than before)
- If allocation dropped: `newAllocations[key]` is null → power = 0 → "stop charging"
- If unchanged: not in delta → no signal emitted

This means the signal log only contains actionable changes. If you replay the log,
each entry represents a real charge instruction update.

**Test verifies idempotency:**
```kotlin
optimizer.optimize(orderBook)
val signalsAfterFirst = steeringSignalClient.getAllSignals().size

optimizer.optimize(orderBook)  // same state, no change
val signalsAfterSecond = steeringSignalClient.getAllSignals().size

assertEquals(signalsAfterFirst, signalsAfterSecond)  // no new signals
```

## Files changed
- `service/ChargingOptimizedService.kt` — moved signal emission into delta loop, removed `emitSteeringSignals`
- `service/ChargingOptimizerServiceTest.kt` — added test verifying no duplicate signals on identical re-optimization