#!/bin/sh
# Install the ArchiveTune provider into the running Music Assistant container.
#
# Works on Home Assistant OS (BusyBox ash) from the "Advanced SSH & Web Terminal"
# add-on with protection mode off. Needs docker, and curl unless --local is used.
#
# Usage:
#   sh install_provider.sh [--local DIR] [--ref REF] [--ma-id NAME]
#                          [--python-version VER] [--no-restart]
#
#   --local DIR           install from DIR (a copy of server/music-assistant/archivetune)
#   --ref REF             git branch, tag or commit to download (default: main)
#   --ma-id NAME          MA container name (default: auto-detect)
#   --python-version VER  e.g. python3.13 (default: auto-detect in the container)
#   --no-restart          copy the files but do not restart the container

set -eu

REPO_OWNER="sam73368"
REPO_NAME="ArchiveTune"
PROVIDER_DIR="archivetune"
SOURCE_SUBDIR="server/music-assistant/archivetune"
FILES="__init__.py manifest.json setup_flow.py strings.json icon.svg icon_monochrome.svg"

REF="main"
LOCAL_DIR=""
MA_ID=""
PYTHON_VERSION=""
NO_RESTART=0

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        --local) shift; LOCAL_DIR="${1:-}" ;;
        --ref) shift; REF="${1:-}" ;;
        --ma-id) shift; MA_ID="${1:-}" ;;
        --python-version) shift; PYTHON_VERSION="${1:-}" ;;
        --no-restart) NO_RESTART=1 ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        *) die "unknown option: $1" ;;
    esac
    shift
done

command -v docker >/dev/null 2>&1 || die "the 'docker' command was not found. On Home Assistant OS use the 'Advanced SSH & Web Terminal' add-on and turn protection mode off."

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
STAGE="$WORK/$PROVIDER_DIR"
mkdir -p "$STAGE"

if [ -n "$LOCAL_DIR" ]; then
    [ -d "$LOCAL_DIR" ] || die "--local directory not found: $LOCAL_DIR"
    log "Using local files from $LOCAL_DIR"
    for f in $FILES; do
        [ -f "$LOCAL_DIR/$f" ] || die "missing $LOCAL_DIR/$f"
        cp "$LOCAL_DIR/$f" "$STAGE/$f"
    done
else
    command -v curl >/dev/null 2>&1 || die "curl not found; use --local DIR instead"
    BASE="https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/$REF/$SOURCE_SUBDIR"
    log "Downloading provider from $REPO_OWNER/$REPO_NAME@$REF"
    for f in $FILES; do
        curl -fsSL "$BASE/$f" -o "$STAGE/$f" || die "download failed: $BASE/$f"
    done
fi

if [ -z "$MA_ID" ]; then
    MA_ID="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i 'music_assistant' | grep -vi 'watcher' | head -n 1 || true)"
    [ -n "$MA_ID" ] || die "no running Music Assistant container found. List candidates with: docker ps -a | grep -i music, then pass --ma-id NAME"
    log "Detected Music Assistant container: $MA_ID"
fi
docker inspect "$MA_ID" >/dev/null 2>&1 || die "container '$MA_ID' not found"

if [ -z "$PYTHON_VERSION" ]; then
    PYTHON_VERSION="$(docker exec "$MA_ID" sh -c 'ls /app/venv/lib/ 2>/dev/null' | grep -E '^python3\.[0-9]+$' | sort -V | tail -n 1 || true)"
    [ -n "$PYTHON_VERSION" ] || die "could not detect the Python version inside $MA_ID; pass --python-version python3.X"
    log "Detected $PYTHON_VERSION"
fi

DEST="/app/venv/lib/$PYTHON_VERSION/site-packages/music_assistant/providers"
docker exec "$MA_ID" test -d "$DEST" || die "$DEST does not exist inside $MA_ID"

log "Copying provider into $MA_ID:$DEST/$PROVIDER_DIR"
docker exec "$MA_ID" rm -rf "$DEST/$PROVIDER_DIR" 2>/dev/null || true
docker cp "$STAGE" "$MA_ID:$DEST/" || die "docker cp failed"

if [ "$NO_RESTART" -eq 1 ]; then
    log "Done. Restart with: docker restart $MA_ID"
else
    log "Restarting $MA_ID"
    docker restart "$MA_ID" >/dev/null || die "docker restart failed"
    log "Done. In Music Assistant: Settings > Providers > Add provider > ArchiveTune."
fi
log "Home Assistant can recreate the container: the ArchiveTune Provider Watcher add-on (scripts/install_watcher_addon.sh) reinstalls automatically."
