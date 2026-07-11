# Fix #1 — Remove double optimization call

> Resolves: [TODO.md #1](../TODO.md#1-double-optimization-on-order-update)

## Problem
`OrderUpdateController.orderUpdate()` calls `chargingOptimizerService.optimize()` after 
`orderBookService.processOrder()`, but `processOrder` already triggers optimization internally.

Result: optimizer runs twice per order, potentially emitting duplicate steering signals.

## Fix
Removed the explicit `optimize` call from the controller. The service owns that lifecycle.

## Implementation logic
The call chain was:
```
Controller.orderUpdate()
  → orderBookService.processOrder()
      → orderBook.processOrder()      // updates book
      → chargingOptimizerService.optimize()  // 1st call
  → chargingOptimizerService.optimize()      // 2nd call (redundant)
```

After fix:
```
Controller.orderUpdate()
  → orderBookService.processOrder()
      → orderBook.processOrder()
      → chargingOptimizerService.optimize()  // single call, service owns lifecycle
```

The controller now only delegates to the service and returns the response.
The `ChargingOptimizerService` dependency was removed from the controller entirely.

## Files changed
- `controller/OrderUpdateController.kt` — removed redundant optimize + unnecessary chargingOptimizerService dependency