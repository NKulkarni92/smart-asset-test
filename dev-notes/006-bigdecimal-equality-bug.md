# Fix #6 — BigDecimal equality bug in OrderBookService

> Resolves: scale-sensitive `==` on BigDecimal causing incorrect FILLED/PARTIALLY_FILLED status

## Problem
`matchedQuantity == request.quantity` uses Kotlin's `==` which delegates to `BigDecimal.equals()`.
`equals()` considers scale: `BigDecimal("10.00").equals(BigDecimal("10"))` returns **false**.

This means a fully matched order could incorrectly report PARTIALLY_FILLED if the scales differ
between the input quantity and the arithmetic result.

## Fix
Replaced `==` with `compareTo() == 0` which is scale-independent.

```kotlin
// Before (broken)
matchedQuantity == request.quantity

// After (correct)
matchedQuantity.compareTo(request.quantity) == 0
```

## Implementation logic

Kotlin's `==` on BigDecimal calls `equals()`, which checks both value AND scale:
```kotlin
BigDecimal("10").equals(BigDecimal("10"))     // true  (same scale 0)
BigDecimal("10.00").equals(BigDecimal("10"))   // FALSE (scale 2 vs 0)
BigDecimal("10.00").compareTo(BigDecimal("10")) // 0    (value-only)
```

In `OrderBook.processOrder()`, the returned `matchedQuantity` is computed via arithmetic:
```kotlin
return quantity - remaining  // result may have different scale from input
```

If `quantity` came in as `BigDecimal("10")` (scale 0) but after subtraction the result
is `BigDecimal("10.00")` (scale 2 from matching against a 2-decimal ask), then
`matchedQuantity == request.quantity` would be `false` even though the order was fully filled.

The fix:
```kotlin
val status = when {
    matchedQuantity.compareTo(request.quantity) == 0 -> "FILLED"
    matchedQuantity > BigDecimal.ZERO -> "PARTIALLY_FILLED"
    else -> "ACCEPTED"
}
```

Note: `>` already uses `compareTo` under the hood (Kotlin operator overloading for Comparable),
so only the `==` check needed fixing.

## Files changed
- `service/OrderBookService.kt` — fixed status comparison
- `service/OrderBookServiceTest.kt` — added test with different scales (10.00 vs 10)