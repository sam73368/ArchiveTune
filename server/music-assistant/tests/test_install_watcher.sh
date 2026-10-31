#!/bin/sh
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../scripts/install_watcher_addon.sh"
SRC="$HERE/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/addons"

PATH="$T/bin:$PATH" sh "$SCRIPT" --local "$SRC" --addons-dir "$T/addons" --no-ha-cli >"$T/out.log" 2>&1 || { cat "$T/out.log"; exit 1; }
D="$T/addons/archivetune_ma_watcher"
for f in config.yaml build.yaml Dockerfile run.sh watcher_lib.sh translations/en.yaml archivetune/__init__.py archivetune/manifest.json archivetune/setup_flow.py; do
  [ -f "$D/$f" ] || { echo "missing $f"; exit 1; }
done
grep -q '^version: "1\.0\.[0-9]\{14\}"$' "$D/config.yaml" || { echo "version not stamped"; cat "$D/config.yaml"; exit 1; }
grep -q "__ADDON_VERSION__" "$D/config.yaml" && { echo "placeholder left"; exit 1; }
grep -q "Finish in Home Assistant" "$T/out.log" || { echo "manual steps missing"; exit 1; }

cat > "$T/bin/ha" <<STUB
#!/bin/sh
echo "ha \$*" >> "$T/ha.log"
case "\$2" in
  info) [ -f "$T/installed" ] || exit 1 ;;
  install) touch "$T/installed" ;;
esac
exit 0
STUB
chmod +x "$T/bin/ha"
PATH="$T/bin:$PATH" sh "$SCRIPT" --local "$SRC" --addons-dir "$T/addons" >"$T/out2.log" 2>&1 || { cat "$T/out2.log"; exit 1; }
grep -q "ha addons install local_archivetune_ma_watcher" "$T/ha.log" || { echo "no install call"; cat "$T/ha.log"; exit 1; }
grep -q "security local_archivetune_ma_watcher --protected=false" "$T/ha.log" || { echo "no protection call"; exit 1; }
grep -q "start local_archivetune_ma_watcher\|restart local_archivetune_ma_watcher" "$T/ha.log" || { echo "no start call"; exit 1; }
grep -q "Finish in Home Assistant" "$T/out2.log" && { echo "manual steps shown despite success"; exit 1; }

: > "$T/ha.log"
PATH="$T/bin:$PATH" sh "$SCRIPT" --local "$SRC" --addons-dir "$T/addons" >"$T/out3.log" 2>&1
grep -q "ha addons update local_archivetune_ma_watcher" "$T/ha.log" || { echo "no update call on reinstall"; cat "$T/ha.log"; exit 1; }

if sh "$SCRIPT" --local "$T/nope" --addons-dir "$T/addons" --no-ha-cli >/dev/null 2>&1; then echo "missing dir accepted"; exit 1; fi
echo "install watcher test ok"
