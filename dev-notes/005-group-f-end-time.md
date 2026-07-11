# Fix #5 — Group F end time

> Resolves: [TODO.md #2](../TODO.md#2-group-f-end-time-mismatch)

## Problem
Spec says Group F charges from 17:30 to 23:59.
Code used `today.plusDays(1).atTime(0, 0)` which is 00:00 next day.

In practice, `23:59` likely means the last full quarter is `23:45–00:00`.
Using midnight as the exclusive end boundary is actually correct for quarter generation:
the loop `while (current < end)` with 15-min steps produces quarters up to 23:45.

## Decision
No change needed. `00:00 next day` as exclusive upper bound correctly generates all quarters
through 23:45–00:00. If we used `23:59`, the last quarter would be 23:45–23:59 (14 minutes),
which is inconsistent with the 15-min market standard.

The current implementation is correct.

## Implementation logic

The quarter generation loop:
```kotlin
private fun getQuartersInWindow(start: LocalDateTime, end: LocalDateTime): List<DeliveryPeriod> {
    val quarters = mutableListOf<DeliveryPeriod>()
    var current = start
    while (current < end) {
        val quarterEnd = current.plusMinutes(15)
        quarters.add(DeliveryPeriod(current, quarterEnd.coerceAtMost(end)))
        current = quarterEnd
    }
    return quarters
}
```

With `end = 00:00 next day`:
- Last iteration: `current = 23:45`, `quarterEnd = 00:00`, condition `23:45 < 00:00` is true
- Produces quarter `DeliveryPeriod(23:45, 00:00)` — a full 15-min quarter

With `end = 23:59`:
- Last iteration: `current = 23:45`, `quarterEnd = 00:00`, `coerceAtMost(23:59)` = 23:59
- Produces quarter `DeliveryPeriod(23:45, 23:59)` — only 14 minutes, wrong

Conclusion: the spec's "23:59" is a human-readable shorthand for "end of day".
Using midnight as the exclusive boundary is the correct interpretation.