#!/usr/bin/env bash
set -u
BASE="http://127.0.0.1:8780"
TOKEN="smoke"
ARCHIVETUNE_TOKEN="$TOKEN" ARCHIVETUNE_PORT=8780 server/service/build/install/server/bin/server > server-run.log 2>&1 &
PID=$!
for _ in $(seq 1 60); do
  curl -sf "$BASE/api/health" > /dev/null && break
  sleep 1
done
{
  echo "health: $(curl -s "$BASE/api/health" | tr -d '\n ')"
  echo "page: $(curl -s -o /dev/null -w '%{http_code} %{size_download}' "$BASE/")"
  echo "without token: $(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/search?q=test")"
  search=$(curl -s "$BASE/api/search?q=Nightmare%20Avenged%20Sevenfold&token=$TOKEN")
  echo "search: $(echo "$search" | tr -d '\n' | head -c 500)"
  id=$(echo "$search" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin); print(d["tracks"][0]["id"])
except Exception:
    print("")')
  echo "first id: $id"
  if [ -n "$id" ]; then
    echo "resolve: $(curl -s --max-time 60 "$BASE/api/resolve?id=$id&token=$TOKEN" | tr -d '\n' | head -c 500)"
    echo "stream: $(curl -s --max-time 60 -o /dev/null -r 0-65535 -w '%{http_code} %{size_download} %{content_type}' "$BASE/stream/$id?token=$TOKEN")"
  fi
  echo "--- server log"
  tail -60 server-run.log
} > smoke.log 2>&1
kill "$PID" 2>/dev/null || true
msg=$(cat smoke.log)
msg="${msg//'%'/'%25'}"
msg="${msg//$'\n'/'%0A'}"
msg="${msg//$'\r'/'%0D'}"
echo "::warning title=smoke test::${msg}"
