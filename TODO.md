# Open Issues & Gaps

## 1. Double optimization on order update

> Dev note: [dev-notes/001-remove-double-optimize.md](dev-notes/001-remove-double-optimize.md)

`OrderUpdateController.orderUpdate()` calls `optimize()` explicitly, but `OrderBookService.processOrder()` already calls it internally.  
Net effect: optimizer runs twice per incoming order — wasteful and could emit duplicate steering signals.

**Fix:** Remove one of the two calls. Likely the one in the controller since the service should own that responsibility.

---

## 2. Group F end time mismatch

> Dev note: [dev-notes/005-group-f-end-time.md](dev-notes/005-group-f-end-time.md)

Spec says `23:59`. Code uses `today.plusDays(1).atTime(0, 0)` which is `00:00` next day.  
This generates an extra quarter (`23:45–00:00`) that doesn't exist in the spec's window.

**Fix:** Use `today.atTime(23, 59)` or define the boundary as `today.atTime(23, 45).plusMinutes(15)` depending on how we interpret "23:59" (likely means last quarter ends at 23:59, i.e. the window includes quarter 23:45–00:00). Need to decide: does 23:59 mean the last valid quarter *starts* at 23:45? If so, `today.plusDays(1).atTime(0, 0)` is actually correct. Clarify intent.

---

## 3. Optimizer doesn't place market orders

> Dev note: [dev-notes/002-optimizer-owns-market-orders.md](dev-notes/002-optimizer-owns-market-orders.md)

The PDF explicitly asks: *"Create a client to keep track of the orders you are sending to the market to actually purchase the energy"* and *"sell the energy for a given future quarter if your new optimization decides not to charge in that quarter."*

Currently the `MarketOrderClient` only logs orders that arrive via the API. The optimizer produces steering signals but never places buy/sell orders based on its plan.

**Fix:** After optimization, diff the new plan against the previous plan. For newly allocated quarters → place BUY orders via `MarketOrderClient`. For quarters that were previously allocated but dropped → place SELL orders to offload that energy.

---

## 4. `chargedSoFar` never wired in

> Dev note: [dev-notes/003-wire-charged-so-far.md](dev-notes/003-wire-charged-so-far.md)

`OrderBookService.processOrder()` calls `optimize(orderBook)` with no `chargedSoFar` map.  
The `PurchaseTrackerService` has the data, but nobody passes it to the optimizer.

This means the optimizer always plans from scratch as if nothing was purchased yet — it will over-allocate.

**Fix:** Wire `PurchaseTrackerService` into the optimization call. Aggregate purchases per group and pass as `chargedSoFar`.

---

## 5. Purchase recording only happens on external order match

> Dev note: [dev-notes/002-optimizer-owns-market-orders.md](dev-notes/002-optimizer-owns-market-orders.md) (solved together with #3 — same root cause)

Purchases are only recorded when an incoming BUY order matches in `processOrder()`. But in reality, the system should record purchases when the *optimizer* decides to buy energy for a group.

Currently the flow is:
```
external order → match → record purchase
```

It should be:
```
external order → update book → re-optimize → optimizer places buy → record purchase
```

**Fix:** Move purchase recording into the optimizer's market-order-placing logic. When the optimizer places a BUY order, that's the real purchase.

---

## 6. No plan diffing / state tracking

> Dev note: [dev-notes/004-plan-state-diffing.md](dev-notes/004-plan-state-diffing.md)

The optimizer is stateless — it recalculates from scratch every time. That's fine for the plan itself, but to know what to BUY/SELL on the market (gap #3), we need to diff against the *previous* plan.

**Fix:** Store the last emitted plan in `ChargingOptimizerService`. On re-optimization, compare new vs old allocations per quarter and emit the delta as market orders.

---

## Priority order for fixing

1. **#1** (trivial, 1-line delete)
2. **#4** (wire chargedSoFar — small plumbing)
3. **#6** (plan state tracking — needed by #3)
4. **#3** (autonomous market orders — the biggest gap)
5. **#5** (purchase recording moves — follows from #3)
6. **#2** (clarify Group F boundary — cosmetic)