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
  echo "help: $(curl -s -o /dev/null -w '%{http_code} %{size_download}' "$BASE/api/help")"
  echo "devices: $(curl -s "$BASE/api/devices?token=$TOKEN" | tr -d '\n ' | head -c 200)"
  albums=$(curl -s "$BASE/api/search?q=Nightmare%20Avenged%20Sevenfold&type=album&token=$TOKEN")
  album_id=$(echo "$albums" | python3 -c 'import json,sys
try:
    print(json.load(sys.stdin)["albums"][0]["id"])
except Exception:
    print("")')
  echo "album id: $album_id"
  echo "album: $(curl -s "$BASE/api/album/$album_id?token=$TOKEN" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin); print(d["album"]["title"], d["album"].get("year"), len(d["tracks"]), "tracks; first:", d["tracks"][0]["title"])
except Exception as e:
    print("ERR", e)')"
  artists=$(curl -s "$BASE/api/search?q=Avenged%20Sevenfold&type=artist&token=$TOKEN")
  artist_id=$(echo "$artists" | python3 -c 'import json,sys
try:
    print(json.load(sys.stdin)["artists"][0]["id"])
except Exception:
    print("")')
  echo "artist: $(curl -s "$BASE/api/artist/$artist_id?token=$TOKEN" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin); print(d["artist"]["name"], len(d["topTracks"]), "top,", len(d["albums"]), "albums,", len(d["singles"]), "singles")
except Exception as e:
    print("ERR", e)')"
  echo "queue song: $(curl -s "$BASE/api/queue?q=Bat%20Country&token=$TOKEN" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin); print(d["title"], len(d["tracks"]), "tracks")
except Exception as e:
    print("ERR", e)')"
  echo "play without device: $(curl -s "$BASE/api/play?q=test&token=$TOKEN" | tr -d '\n' | head -c 200)"
  mcp() { curl -s -X POST "$BASE/mcp?token=$TOKEN" -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -d "$1"; }
  echo "mcp init: $(mcp '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}' | head -c 300)"
  echo "mcp initialized: $(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/mcp?token=$TOKEN" -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","method":"notifications/initialized"}')"
  echo "mcp tools: $(mcp '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | python3 -c 'import json,sys
d=json.load(sys.stdin); print([t["name"] for t in d["result"]["tools"]])')"
  echo "mcp search: $(mcp '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_music","arguments":{"query":"Nightmare Avenged Sevenfold","type":"album"}}}' | head -c 300)"
  echo "mcp play no device: $(mcp '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"play_music","arguments":{"query":"Nightmare","device":"Salon"}}}' | head -c 300)"
  echo "--- server log"
  grep -E "^(YouTube locale|visitorData|resolve:|probe|stream )" server-run.log | head -40
} > smoke.log 2>&1
kill "$PID" 2>/dev/null || true
msg=$(cat smoke.log)
msg="${msg//'%'/'%25'}"
msg="${msg//$'\n'/'%0A'}"
msg="${msg//$'\r'/'%0D'}"
echo "::warning title=smoke test::${msg}"
