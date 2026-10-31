#!/bin/sh
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../scripts/install_provider.sh"
SRC="$HERE/../archivetune"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/container/app/venv/lib/python3.13/site-packages/music_assistant/providers"
LOG="$T/docker.log"
cat > "$T/bin/docker" <<STUB
#!/bin/sh
echo "docker \$*" >> "$LOG"
case "\$1" in
  ps) echo "addon_d5369777_music_assistant"; echo "addon_other_music_assistant_watcher" ;;
  inspect) exit 0 ;;
  exec)
    shift; shift
    case "\$1" in
      sh) echo python3.12; echo python3.13 ;;
      test) exit 0 ;;
      rm) rm -rf "$T/container\$3" ;;
    esac ;;
  cp) cp -r "\$2" "$T/container/app/venv/lib/python3.13/site-packages/music_assistant/providers/" ;;
  restart) exit 0 ;;
esac
STUB
chmod +x "$T/bin/docker"
PATH="$T/bin:$PATH" sh "$SCRIPT" --local "$SRC" >"$T/out.log" 2>&1 || { cat "$T/out.log"; exit 1; }
D="$T/container/app/venv/lib/python3.13/site-packages/music_assistant/providers/archivetune"
for f in __init__.py manifest.json setup_flow.py strings.json icon.svg icon_monochrome.svg; do
  [ -f "$D/$f" ] || { echo "missing $f"; exit 1; }
done
grep -q "docker restart addon_d5369777_music_assistant" "$LOG" || { echo "no restart"; cat "$LOG"; exit 1; }
grep -q "watcher" "$LOG" && grep -q "restart addon_other" "$LOG" && { echo "picked watcher"; exit 1; }
PATH="$T/bin:$PATH" sh "$SCRIPT" --local "$SRC" --no-restart >/dev/null 2>&1
[ "$(grep -c '^docker restart' "$LOG")" = "1" ] || { echo "--no-restart restarted"; exit 1; }
if PATH="$T/bin:$PATH" sh "$SCRIPT" --local "$T/nope" >/dev/null 2>&1; then echo "missing dir accepted"; exit 1; fi
echo "install script test ok"
