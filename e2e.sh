#!/bin/bash

BASE_URL="http://localhost:8080/api"
TODAY=$(date +%Y-%m-%d)

echo "=== E2E Pseudo Test ==="
echo "Using date: $TODAY"
echo ""

echo "POST /api/orderupdate - Place SELL order"
curl -s -X POST "$BASE_URL/orderupdate" \
  -H "Content-Type: application/json" \
  -d "{
    \"delivery_start_time\": \"${TODAY}T10:00:00\",
    \"delivery_end_time\": \"${TODAY}T10:15:00\",
    \"order_side\": \"SELL\",
    \"quantity\": 10,
    \"price\": 50.00
  }" | jq .
echo ""

echo "POST /api/orderupdate - Place matching BUY order"
curl -s -X POST "$BASE_URL/orderupdate" \
  -H "Content-Type: application/json" \
  -d "{
    \"delivery_start_time\": \"${TODAY}T10:00:00\",
    \"delivery_end_time\": \"${TODAY}T10:15:00\",
    \"order_side\": \"BUY\",
    \"quantity\": 5,
    \"price\": 50.00
  }" | jq .
echo ""

echo "POST /api/orderupdate - Place another SELL order"
curl -s -X POST "$BASE_URL/orderupdate" \
  -H "Content-Type: application/json" \
  -d "{
    \"delivery_start_time\": \"${TODAY}T10:15:00\",
    \"delivery_end_time\": \"${TODAY}T10:30:00\",
    \"order_side\": \"SELL\",
    \"quantity\": 20,
    \"price\": 45.00
  }" | jq .
echo ""

echo "GET /api/market/overview - Get market overview"
curl -s -X GET "$BASE_URL/market/overview" | jq .
echo ""

echo "GET /api/purchases/average - Get average purchase price"
curl -s -X GET "$BASE_URL/purchases/average" | jq .
echo ""

echo "=== Done ==="
