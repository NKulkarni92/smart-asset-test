# Smart Asset — Design Notes

## Context

Frank Energie operates in the intraday electricity market where energy is traded per 15-minute delivery quarter.  
Quantity is in MWh, price in EUR/MWh.

This service receives live market order updates, maintains an internal order book, and continuously re-optimizes a charging plan for our EV fleet — deciding *when* and *how much* to charge each group at the lowest possible cost.

---

## EV Fleet Charging Groups

| Group | Charging Window | Energy Need (MWh) | Max Power (MW) |
|-------|----------------|-------------------|----------------|
| A     | 00:00 – 08:30  | 5                 | 2              |
| B     | 00:00 – 11:00  | 10                | 3              |
| C     | 13:00 – 18:00  | 4                 | 1              |
| D     | 13:00 – 21:00  | 20                | 6              |
| E     | 17:30 – 22:00  | 5                 | 2              |
| F     | 17:30 – 23:59  | 15                | 5              |

Each group has a fixed time window, a total energy requirement, and a max power draw per quarter.

---

## Architecture Overview

```
POST /api/orderupdate
        │
        ▼
┌──────────────────┐      ┌─────────────────────┐
│  OrderBookService │─────▶│  OrderBook (domain)  │
└──────────────────┘      └─────────────────────┘
        │                           │
        │ (on match → BUY side)     │ (current market state)
        ▼                           ▼
┌──────────────────────┐   ┌────────────────────────────┐
│  PurchaseTrackerService│   │  ChargingOptimizerService   │
└──────────────────────┘   └────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼                               ▼
        ┌────────────────────┐          ┌──────────────────────┐
        │ SteeringSignalClient│          │  MarketOrderClient    │
        │  (log/steering_     │          │  (log/market_orders   │
        │   signals.log)      │          │   .log)               │
        └────────────────────┘          └──────────────────────┘
```

---

## Core Components

### 1. Order Book (`OrderBook.kt`)

Price-time priority matching engine, per delivery period.

- BUY order comes in → sweep ASK side from lowest price up (as long as ask ≤ buy price)
- SELL order comes in → sweep BID side from highest price down (as long as bid ≥ sell price)
- Matched quantity cancels out; remainder rests at the incoming price level
- Aggregates at same price level (no individual order tracking needed)
- Separate book per 15-min delivery period

### 2. Charging Optimizer (`ChargingOptimizerService.kt`)

Greedy strategy: for each group, rank available quarters by cheapest ask price, allocate up to `maxPower × 0.25h` per quarter until the energy need is met.

Triggered on every order update. Accounts for energy already purchased (`chargedSoFar`).

Emits steering signals to car groups indicating charge power per quarter.

### 3. Purchase Tracker (`PurchaseTrackerService.kt`)

Records every executed purchase (matched BUY). Calculates:
- Total MWh purchased
- Total cost (EUR)
- Weighted average price (EUR/MWh)

### 4. Steering Signal Client (`SteeringSignalClient.kt`)

File-backed mock. Logs every steering signal: group, quarter window, charge power.

### 5. Market Order Client (`MarketOrderClient.kt`)

File-backed mock. Logs every order sent to market: order ID, group, delivery window, side, quantity, price.

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST   | `/api/orderupdate` | Submit a market order update. Triggers matching + re-optimization. |
| GET    | `/api/market/overview` | Returns all quarters with highest bid and lowest ask. |
| GET    | `/api/purchases/average` | Returns total MWh, total cost, and weighted avg price/MWh. |
| GET    | `/api/health` | Liveness check. |

---

## Key Design Decisions

- **In-memory state** — no DB; order book and purchase history live in memory. Sufficient for the scope of this exercise.
- **File-based mocking** — steering signals and market orders write to flat log files under `log/`. Easy to inspect, trivial to replace with an HTTP client later.
- **Re-optimization on every order** — ensures the plan always reflects the latest market conditions. Cheap enough given the small number of groups and quarters.
- **BigDecimal everywhere** — no floating-point drift in financial calculations.
- **ConcurrentHashMap + TreeMap** — thread-safe period lookup, price-ordered levels within each period.

---

## Running

```bash
# Unit tests
./gradlew clean test

# Boot the app + smoke test
./gradlew bootRun
./e2e.sh
```