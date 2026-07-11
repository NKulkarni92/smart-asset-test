# Fix #11 — Final hardening pass

> Resolves: remaining thread safety, file I/O race, price accuracy, dead imports

## Issues fixed

### 1. OrderBook TreeMap thread safety
**Problem:** `ConcurrentHashMap` only protects the period-to-TreeMap lookup. The TreeMap itself
(bids/asks per period) was accessed without synchronization. Concurrent `processOrder` and
`getBestAsk` calls for the same period could cause `ConcurrentModificationException` or corrupt state.

**Fix:** All TreeMap access is now wrapped in `synchronized(treeMap)`:
```kotlin
fun processOrder(...): BigDecimal {
    val bids = bidsPerPeriod.computeIfAbsent(period) { TreeMap(Comparator.reverseOrder()) }
    val asks = asksPerPeriod.computeIfAbsent(period) { TreeMap() }
    return synchronized(bids) {
        synchronized(asks) { ... }
    }
}

fun getBestAsk(period: DeliveryPeriod): PriceLevel? {
    val asks = asksPerPeriod[period] ?: return null
    return synchronized(asks) { asks.firstEntry()?.let { PriceLevel(it.key, it.value) } }
}
```
Lock ordering (bids before asks) prevents deadlocks.

### 2. File client TOCTOU race
**Problem:** `ensureFileExists()` had check-then-act: `if (!exists) createFile()`. Two concurrent
threads could both pass the check and both try to create → `FileAlreadyExistsException`.

**Fix:** Catch and swallow `FileAlreadyExistsException`:
```kotlin
private fun ensureFileExists() {
    if (!Files.exists(file)) {
        file.parent?.let { Files.createDirectories(it) }
        try { Files.createFile(file) } catch (_: FileAlreadyExistsException) { }
    }
}
```

### 3. Price accuracy — allocation capped at best ask quantity
**Problem:** Optimizer used `getAvailableSupply()` (total across all price levels) but recorded
cost at `getBestAsk().price` only. Allocated quantity could exceed what exists at that price.

**Fix:** Changed to use `getBestAsk().quantity` directly:
```kotlin
val quartersWithPrices = quarters.mapNotNull { period ->
    val bestAsk = orderBook.getBestAsk(period)
    bestAsk?.let { Triple(period, it.price, it.quantity) }  // use qty from best level
}.sortedBy { it.second }

for ((period, price, bestAskQty) in quartersWithPrices) {
    val availableSupply = (bestAskQty - alreadyUsed).max(BigDecimal.ZERO)
    ...
}
```
This ensures: allocated quantity ≤ available at recorded price. Cost is always accurate.

### 4. Unused `OrderSide` import removed from OrderBookService

## Files changed
- `domain/OrderBook.kt` — synchronized all TreeMap access
- `client/MarketOrderClient.kt` — `FileAlreadyExistsException` catch
- `client/SteeringSignalClient.kt` — `FileAlreadyExistsException` catch
- `service/ChargingOptimizedService.kt` — uses `bestAsk.quantity` instead of `getAvailableSupply()`
- `service/OrderBookService.kt` — removed unused `OrderSide` import