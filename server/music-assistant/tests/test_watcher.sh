#!/usr/bin/env bash
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/.."
T="$(mktemp -d)"
cleanup() { [ -n "${HTTP_PID:-}" ] && kill "$HTTP_PID" 2>/dev/null; rm -rf "$T"; }
trap cleanup EXIT
fail() { echo "FAIL: $*"; echo "--- docker log"; cat "$T/docker.log" 2>/dev/null; echo "--- out"; cat "$T/out.log" 2>/dev/null; exit 1; }

mkdir -p "$T/bin" "$T/site/archivetune" "$T/ma/providers" "$T/data" "$T/bundled"
cp -r "$ROOT/archivetune/." "$T/site/archivetune/"
cp -r "$ROOT/archivetune/." "$T/bundled/"
echo "$T/ma/providers" > "$T/dst"
echo "cid-1" > "$T/id"
: > "$T/docker.log"

cat > "$T/bin/docker" <<STUB
#!/bin/bash
echo "docker \$*" >> "$T/docker.log"
DST="$T/ma/providers"
case "\$1" in
  info) exit 0 ;;
  ps) [ -f "$T/stopped" ] || echo "addon_d5369777_music_assistant"; echo "addon_local_archivetune_ma_watcher" ;;
  inspect) cat "$T/id" ;;
  exec)
    shift; shift
    case "\$1" in
      sh) echo python3.12; echo python3.14 ;;
      test) shift; p="\${2//\/app\/venv\/lib\/python3.14\/site-packages\/music_assistant\/providers/\$DST}"; if [ "\$1" = "-d" ]; then [ -d "\$p" ]; else [ -f "\$p" ]; fi ;;
      cat) p="\${2//\/app\/venv\/lib\/python3.14\/site-packages\/music_assistant\/providers/\$DST}"; cat "\$p" ;;
      rm) p="\${3//\/app\/venv\/lib\/python3.14\/site-packages\/music_assistant\/providers/\$DST}"; rm -rf "\$p" ;;
    esac ;;
  cp) cp -r "\$2" "\$DST/" ;;
  restart) echo restarted >> "$T/restarts" ;;
esac
STUB
chmod +x "$T/bin/docker"

python3 -m http.server 0 --bind 127.0.0.1 --directory "$T/site" >"$T/http.log" 2>&1 &
HTTP_PID=$!
for _ in 1 2 3 4 5 6 7 8 9 10; do
  PORT="$(sed -n 's/.*port \([0-9]*\).*/\1/p' "$T/http.log" | head -n1)"
  [ -n "$PORT" ] && break
  sleep 0.3
done
[ -n "$PORT" ] || fail "http server did not start"

export PATH="$T/bin:$PATH" BUNDLED="$T/bundled" CACHE="$T/data/archivetune" OPTIONS_FILE="$T/data/options.json"
export RAW_BASE="http://127.0.0.1:$PORT/archivetune" POLL_SECONDS=0 SETTLE_SECONDS=0 MAX_ITERATIONS=3
echo '{"auto_update": true, "update_interval_hours": 6, "ref": "main"}' > "$OPTIONS_FILE"
INSTALLED="$T/ma/providers/archivetune"

run_main() { bash -c ". '$ROOT/addon/watcher_lib.sh'; main" >"$T/out.log" 2>&1; }

run_main || fail "main failed on first run"
[ -f "$INSTALLED/__init__.py" ] || fail "provider not installed"
[ -f "$INSTALLED/.content_hash" ] || fail "hash marker missing"
[ "$(wc -l < "$T/restarts")" = "1" ] || fail "expected one restart"
grep -q "addon_local_archivetune_ma_watcher" "$T/docker.log" && grep -q "restart addon_local" "$T/docker.log" && fail "watcher container was targeted"
grep -q "restart addon_d5369777_music_assistant" "$T/docker.log" || fail "MA not restarted"

run_main || fail "main failed on second run"
[ "$(wc -l < "$T/restarts")" = "1" ] || fail "identical files must not restart MA again"
grep -q "already installed" "$T/out.log" || fail "no already-installed message"

rm -rf "$INSTALLED"; echo "cid-2" > "$T/id"
run_main || fail "main failed after container recreation"
[ -f "$INSTALLED/__init__.py" ] || fail "provider not reinstalled after recreation"
[ "$(wc -l < "$T/restarts")" = "2" ] || fail "expected second restart"

echo '# changed' >> "$T/site/archivetune/strings.json.tmp"
python3 - <<PY
import json,pathlib
p=pathlib.Path("$T/site/archivetune/strings.json")
d=json.loads(p.read_text()); d["manifest"]["description"]="Updated description"; p.write_text(json.dumps(d))
PY
run_main || fail "main failed on update"
grep -q "Updated description" "$INSTALLED/strings.json" || fail "update not installed"
[ -d "$CACHE" ] || fail "cache not created"
[ "$(wc -l < "$T/restarts")" = "3" ] || fail "expected third restart after update"

echo "broken" > "$T/site/archivetune/__init__.py"
before="$(wc -l < "$T/restarts")"
run_main || fail "main failed on broken download"
grep -q "not a valid provider" "$T/out.log" || fail "broken provider not rejected"
[ "$(wc -l < "$T/restarts")" = "$before" ] || fail "broken provider triggered a restart"
grep -q "class ArchiveTuneProvider" "$INSTALLED/__init__.py" || fail "broken provider installed"

kill "$HTTP_PID"; HTTP_PID=""; sleep 1
run_main || fail "main failed with GitHub unreachable"
grep -q "download of" "$T/out.log" || fail "unreachable GitHub not reported"
grep -q "class ArchiveTuneProvider" "$INSTALLED/__init__.py" || fail "provider lost while offline"

echo '{"auto_update": false}' > "$OPTIONS_FILE"
rm -rf "$INSTALLED" "$CACHE"; echo "cid-3" > "$T/id"
run_main || fail "main failed with auto update off"
[ -f "$INSTALLED/__init__.py" ] || fail "bundled provider not installed with auto update off"

touch "$T/stopped"
run_main || fail "main failed with MA stopped"
echo "watcher test ok"
