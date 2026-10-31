#!/bin/sh
# One-time setup of the "ArchiveTune Provider Watcher" local add-on.
#
# The add-on keeps the ArchiveTune provider installed in Music Assistant after
# every restart, container recreation or Music Assistant update, and updates the
# provider from GitHub by itself. Run this once from the "Advanced SSH & Web
# Terminal" add-on (protection mode off).
#
# Usage:
#   sh install_watcher_addon.sh [--local DIR] [--ref REF] [--addons-dir DIR] [--no-ha-cli]
#
#   --local DIR       use a copy of server/music-assistant instead of downloading
#   --ref REF         git branch, tag or commit to download (default: main)
#   --addons-dir DIR  local add-ons directory (default: auto-detect)
#   --no-ha-cli       only write the files, do not call the 'ha' command

set -eu

REPO_OWNER="sam73368"
REPO_NAME="ArchiveTune"
SOURCE_SUBDIR="server/music-assistant"
ADDON_SLUG="archivetune_ma_watcher"
ADDON_FILES="addon/config.yaml addon/build.yaml addon/Dockerfile addon/run.sh addon/watcher_lib.sh addon/translations/en.yaml"
PROVIDER_FILES="__init__.py manifest.json setup_flow.py strings.json icon.svg icon_monochrome.svg"

REF="main"
LOCAL_DIR=""
ADDONS_DIR=""
USE_HA_CLI=1

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        --local) shift; LOCAL_DIR="${1:-}" ;;
        --ref) shift; REF="${1:-}" ;;
        --addons-dir) shift; ADDONS_DIR="${1:-}" ;;
        --no-ha-cli) USE_HA_CLI=0 ;;
        -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
        *) die "unknown option: $1" ;;
    esac
    shift
done

case "$REF" in
    ""|*[!A-Za-z0-9._/-]*) die "invalid --ref value '$REF'" ;;
esac

writable_dir() {
    [ -d "$1" ] || return 1
    _probe="$1/.archivetune_write_test.$$"
    if ( : > "$_probe" ) 2>/dev/null; then rm -f "$_probe"; return 0; fi
    return 1
}

if [ -z "$ADDONS_DIR" ]; then
    for _cand in /addons /addons/local /data/apps/local /data/addons/local \
        /mnt/data/supervisor/apps/local /mnt/data/supervisor/addons/local \
        /var/lib/homeassistant/apps/local /var/lib/homeassistant/addons/local \
        /usr/share/hassio/apps/local /usr/share/hassio/addons/local
    do
        if writable_dir "$_cand"; then ADDONS_DIR="$_cand"; break; fi
    done
    [ -n "$ADDONS_DIR" ] || die "no writable local add-ons directory found. Inside the SSH add-on it is /addons; pass --addons-dir DIR"
fi
[ -d "$ADDONS_DIR" ] || die "add-ons directory does not exist: $ADDONS_DIR"
log "Local add-ons directory: $ADDONS_DIR"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
STAGE="$WORK/$ADDON_SLUG"
mkdir -p "$STAGE/translations" "$STAGE/archivetune"

if [ -n "$LOCAL_DIR" ]; then
    log "Using local files from $LOCAL_DIR"
    for f in $ADDON_FILES; do
        [ -f "$LOCAL_DIR/$f" ] || die "missing $LOCAL_DIR/$f"
        cp "$LOCAL_DIR/$f" "$STAGE/${f#addon/}"
    done
    for f in $PROVIDER_FILES; do
        [ -f "$LOCAL_DIR/archivetune/$f" ] || die "missing $LOCAL_DIR/archivetune/$f"
        cp "$LOCAL_DIR/archivetune/$f" "$STAGE/archivetune/$f"
    done
else
    command -v curl >/dev/null 2>&1 || die "curl not found; use --local DIR instead"
    BASE="https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/$REF/$SOURCE_SUBDIR"
    log "Downloading from $REPO_OWNER/$REPO_NAME@$REF"
    for f in $ADDON_FILES; do
        curl -fsSL "$BASE/$f" -o "$STAGE/${f#addon/}" || die "download failed: $BASE/$f"
    done
    for f in $PROVIDER_FILES; do
        curl -fsSL "$BASE/archivetune/$f" -o "$STAGE/archivetune/$f" || die "download failed: $BASE/archivetune/$f"
    done
fi

ADDON_VERSION="1.0.$(date +%Y%m%d%H%M%S)"
sed "s/__ADDON_VERSION__/$ADDON_VERSION/" "$STAGE/config.yaml" > "$STAGE/config.yaml.new"
mv "$STAGE/config.yaml.new" "$STAGE/config.yaml"
chmod +x "$STAGE/run.sh" "$STAGE/watcher_lib.sh"

DEST="$ADDONS_DIR/$ADDON_SLUG"
rm -rf "$DEST"
cp -R "$STAGE" "$DEST"
log "Add-on written to $DEST (version $ADDON_VERSION)"

manual_steps() {
    cat <<MSG

Finish in Home Assistant (one time only):
  1. Settings > Add-ons > Add-on Store > three dots > Check for updates.
  2. Open "ArchiveTune Provider Watcher" under Local add-ons and click Install
     (or Update / Rebuild if it is already installed).
  3. On its Info tab turn Protection mode OFF, and turn on "Start on boot".
  4. Start it. The log should end with "Music Assistant restarted".
MSG
}

if [ "$USE_HA_CLI" -eq 0 ] || ! command -v ha >/dev/null 2>&1; then
    log "The 'ha' command is not available here, so the add-on must be installed by hand."
    manual_steps
    exit 0
fi

SLUG="local_$ADDON_SLUG"

ha_run() {
    ha addons "$@" >/dev/null 2>&1 || ha apps "$@" >/dev/null 2>&1
}

ok=1
ha_run reload || true
if ha_run info "$SLUG"; then
    log "Add-on already installed, updating it"
    ha_run update "$SLUG" || ha_run rebuild "$SLUG" || ok=0
else
    log "Installing the add-on"
    ha_run install "$SLUG" || ok=0
fi
if [ "$ok" -eq 1 ]; then
    ha_run security "$SLUG" --protected=false || { log "Could not turn protection mode off"; ok=0; }
    ha_run options "$SLUG" --boot=auto || log "Could not set start on boot"
    ha_run restart "$SLUG" || ha_run start "$SLUG" || { log "Could not start the add-on"; ok=0; }
fi
if [ "$ok" -eq 1 ]; then
    log "Done. Watch the add-on log: it should end with 'Music Assistant restarted'."
    log "Next: Music Assistant > Settings > Providers > Add provider > ArchiveTune."
else
    manual_steps
fi
