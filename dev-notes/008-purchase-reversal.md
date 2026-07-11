# Fix #8 — Purchase tracker supports reversal

> Resolves: chargedSoFar only increases, never decreases when optimizer drops an allocation

## Problem
When the optimizer dropped a quarter (emitted a SELL), it logged the market order but never
updated the purchase tracker. `getChargedPerGroup()` kept returning the old (higher) value,
causing the optimizer to under-plan on subsequent cycles.

## Fix
Added `reversePurchase()` to `PurchaseTrackerService`. When the optimizer emits a SELL order
for a dropped allocation, it also reverses the corresponding purchase.

The reversal is recorded as a negative-quantity entry. `getChargedPerGroup()` sums all entries
(positive + negative) so net charged reflects reality.

`getSummary().totalMWh` is clamped to zero to avoid displaying negative totals during
edge-case races.

## Implementation logic

**reversePurchase method:**
```kotlin
fun reversePurchase(groupId: String, deliveryStart: LocalDateTime, deliveryEnd: LocalDateTime, quantity: BigDecimal, pricePerMWh: BigDecimal) {
    purchases.add(
        ExecutedPurchase(
            groupId = groupId,
            deliveryStart = deliveryStart,
            deliveryEnd = deliveryEnd,
            quantity = quantity.negate(),    // negative entry
            pricePerMWh = pricePerMWh        // carry the sell price for cost netting
        )
    )
}
```

The sell price is passed in so `totalCost` (which sums `quantity * pricePerMWh`) correctly nets:
- Purchase: `+5 * 50 = +250`
- Reversal: `-2 * 45 = -90`
- Net cost: `250 - 90 = 160 EUR` for 3 MWh → avg 53.33 EUR/MWh

The optimizer uses `getBestBidPrice` (not ask) when selling, since we're hitting the bid side:
```kotlin
change < BigDecimal.ZERO -> {
    val sellPrice = getBestBidPrice(orderBook, key) ?: price
    marketOrderClient.sendOrder(MarketOrder(..., side = SELL, price = sellPrice))
    purchaseTrackerService.reversePurchase(key.groupId, key.startTime, key.endTime, change.negate(), sellPrice)
}
```

**getChargedPerGroup sums everything (including negatives):**
```kotlin
fun getChargedPerGroup(): Map<String, BigDecimal> {
    return purchases.groupBy { it.groupId }
        .mapValues { (_, groupPurchases) -> groupPurchases.sumOf { it.quantity } }
}
```
If group A bought 5 MWh then reversed 2 MWh, result is `{"A" -> 3}`.

**getSummary clamps to zero:**
```kotlin
val totalMWh = allPurchases.sumOf { it.quantity }.max(BigDecimal.ZERO)
```
Prevents negative display during transient states where reversals exceed purchases.

## Files changed
- `service/PurchaseTrackerService.kt` — added `reversePurchase()`, clamped `totalMWh`
- `service/ChargingOptimizedService.kt` — calls `reversePurchase` on SELL delta
- `service/PurchaseTrackerServiceTest.kt` — added test for reversal
- `service/ChargingOptimizerServiceTest.kt` — added test verifying MWh decreases on drop