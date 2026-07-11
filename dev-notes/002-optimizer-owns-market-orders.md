# Fix #2 — Restructure optimizer to own market order lifecycle

> Resolves: [TODO.md #3](../TODO.md#3-optimizer-doesnt-place-market-orders) and [TODO.md #5](../TODO.md#5-purchase-recording-only-happens-on-external-order-match)

## Problem
Three separate concerns were conflated in `OrderBookService.processOrder()`:
1. Updating the order book (correct)
2. Logging market orders via `MarketOrderClient` (wrong — these are external orders, not ours)
3. Recording purchases via `PurchaseTrackerService` (wrong — should happen when WE decide to buy)

The PDF says: "Create a client to keep track of the orders YOU are sending to the market."
Our buy/sell decisions come from the optimizer, not from incoming order updates.

## Fix
- Moved `MarketOrderClient` and `PurchaseTrackerService` from `OrderBookService` into `ChargingOptimizerService`
- Optimizer now places BUY orders for newly allocated quarters and SELL orders for dropped quarters
- Optimizer records purchases when it decides to buy
- `OrderBookService` slimmed down to: update book + trigger optimizer

## Design
```
External order → OrderBook update → Optimizer re-plans → Diff vs previous plan
  → New allocations: BUY order + record purchase + steering signal
  → Dropped allocations: SELL order + steering signal (power=0)
```

## Implementation logic

**Before:** `OrderBookService` held both `MarketOrderClient` and `PurchaseTrackerService`.
When an external BUY order matched, it logged that external order as "our" market order and
recorded the match as a purchase. This conflated external market activity with our decisions.

**After:** `ChargingOptimizerService` constructor now takes:
```kotlin
class ChargingOptimizerService(
    private val chargingGroups: List<ChargingGroup>,
    private val steeringSignalClient: SteeringSignalClient,
    private val marketOrderClient: MarketOrderClient,       // moved here
    private val purchaseTrackerService: PurchaseTrackerService  // moved here
)
```

`OrderBookService` constructor reduced to:
```kotlin
class OrderBookService(
    private val chargingOptimizerService: ChargingOptimizerService
)
```

The optimizer's `optimize()` method now:
1. Computes the optimal plan
2. Diffs against previous plan (see fix #4)
3. For positive deltas → calls `marketOrderClient.sendOrder(BUY)` + `purchaseTrackerService.recordPurchase()`
4. For negative deltas → calls `marketOrderClient.sendOrder(SELL)`
5. Emits steering signals

This ensures only optimizer-driven decisions produce market orders and purchases.

## Files changed
- `service/ChargingOptimizerService.kt` — added MarketOrderClient, PurchaseTrackerService, plan diffing
- `service/OrderBookService.kt` — removed MarketOrderClient, removed purchase recording
- `controller/OrderUpdateController.kt` — simplified dependencies