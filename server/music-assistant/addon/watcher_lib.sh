#!/usr/bin/env bash

PROVIDER_DIR="archivetune"
FILES="__init__.py manifest.json setup_flow.py strings.json icon.svg icon_monochrome.svg"
MA_NAME_RE='^(addon|app)_[0-9a-f]+_music_assistant(_beta|_nightly|_dev)?$'

REPO_OWNER="${REPO_OWNER:-sam73368}"
REPO_NAME="${REPO_NAME:-ArchiveTune}"
REF="${REF:-main}"
BUNDLED="${BUNDLED:-/provider/archivetune}"
CACHE="${CACHE:-/data/archivetune}"
OPTIONS_FILE="${OPTIONS_FILE:-/data/options.json}"
POLL_SECONDS="${POLL_SECONDS:-10}"
SETTLE_SECONDS="${SETTLE_SECONDS:-3}"
MAX_RETRY_SECONDS="${MAX_RETRY_SECONDS:-300}"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

raw_base() {
    printf '%s' "${RAW_BASE:-https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/$REF/server/music-assistant/archivetune}"
}

read_options() {
    AUTO_UPDATE="true"
    UPDATE_INTERVAL_HOURS=6
    if [ -r "$OPTIONS_FILE" ]; then
        AUTO_UPDATE="$(jq -r 'if .auto_update == false then "false" else "true" end' "$OPTIONS_FILE" 2>/dev/null || echo true)"
        UPDATE_INTERVAL_HOURS="$(jq -r 'if (.update_interval_hours|type)=="number" then (.update_interval_hours|floor) else 6 end' "$OPTIONS_FILE" 2>/dev/null || echo 6)"
        configured_ref="$(jq -r '.ref // empty' "$OPTIONS_FILE" 2>/dev/null || true)"
        case "$configured_ref" in
            ""|*[!A-Za-z0-9._/-]*) ;;
            *) REF="$configured_ref" ;;
        esac
    fi
    case "$UPDATE_INTERVAL_HOURS" in ''|*[!0-9]*) UPDATE_INTERVAL_HOURS=6 ;; esac
    [ "$UPDATE_INTERVAL_HOURS" -lt 1 ] && UPDATE_INTERVAL_HOURS=1
    UPDATE_INTERVAL=$((UPDATE_INTERVAL_HOURS * 3600))
}

content_hash() {
    (cd "$1" && for f in $FILES; do cat "$f"; done | sha256sum | cut -d' ' -f1)
}

valid_provider_dir() {
    [ -d "$1" ] || return 1
    for f in $FILES; do [ -s "$1/$f" ] || return 1; done
    grep -q 'class ArchiveTuneProvider' "$1/__init__.py" || return 1
    jq -e '.domain == "archivetune"' "$1/manifest.json" >/dev/null 2>&1 || return 1
}

provider_src() {
    if [ "${AUTO_UPDATE:-true}" = "true" ] && valid_provider_dir "$CACHE"; then
        printf '%s' "$CACHE"
    else
        printf '%s' "$BUNDLED"
    fi
}

fetch_latest() {
    local tmp base current new_hash old_hash
    tmp="$(mktemp -d)" || return 1
    base="$(raw_base)"
    mkdir -p "$tmp/$PROVIDER_DIR"
    for f in $FILES; do
        if ! curl -fsSL --connect-timeout 10 --max-time 60 "$base/$f" -o "$tmp/$PROVIDER_DIR/$f" 2>/dev/null; then
            log "update check: download of $f failed, keeping the current provider"
            rm -rf "$tmp"
            return 1
        fi
    done
    if ! valid_provider_dir "$tmp/$PROVIDER_DIR"; then
        log "update check: downloaded files are not a valid provider, keeping the current provider"
        rm -rf "$tmp"
        return 1
    fi
    new_hash="$(content_hash "$tmp/$PROVIDER_DIR")"
    current="$(provider_src)"
    old_hash="$(content_hash "$current" 2>/dev/null || echo none)"
    if [ "$new_hash" = "$old_hash" ]; then
        rm -rf "$tmp"
        return 2
    fi
    rm -rf "$CACHE.new"
    mkdir -p "$(dirname "$CACHE")"
    cp -a "$tmp/$PROVIDER_DIR" "$CACHE.new" || { rm -rf "$tmp" "$CACHE.new"; return 1; }
    rm -rf "$CACHE"
    mv "$CACHE.new" "$CACHE" || { rm -rf "$tmp"; return 1; }
    FETCHED_VERSION="$(sed -n 's/^__version__ = "\([^"]*\)".*/\1/p' "$CACHE/__init__.py" | head -n1)"
    log "update check: provider ${FETCHED_VERSION:-unknown} downloaded from $REF"
    rm -rf "$tmp"
    return 0
}

find_ma() {
    docker ps --format '{{.Names}}' 2>/dev/null | grep -E "$MA_NAME_RE" | head -n1 || true
}

container_id() {
    docker inspect -f '{{.Id}}' "$1" 2>/dev/null || true
}

detect_python() {
    docker exec "$1" sh -c 'ls /app/venv/lib/ 2>/dev/null' | grep -E '^python3\.[0-9]+$' | sort -V | tail -n1
}

install_provider() {
    local ma="$1" src py dst stage hash installed
    src="$(provider_src)"
    valid_provider_dir "$src" || { log "ERROR: provider files in $src are incomplete"; return 1; }
    sleep "$SETTLE_SECONDS"
    py="$(detect_python "$ma" || true)"
    [ -n "$py" ] || { log "ERROR: no Python found inside $ma"; return 1; }
    dst="/app/venv/lib/$py/site-packages/music_assistant/providers"
    docker exec "$ma" sh -c '[ -d "$1" ]' _ "$dst" || { log "ERROR: $dst is missing inside $ma"; return 1; }
    hash="$(content_hash "$src")"
    installed="$(docker exec "$ma" sh -c 'cat "$1"' _ "$dst/$PROVIDER_DIR/.content_hash" 2>/dev/null || true)"
    if [ "$installed" = "$hash" ]; then
        log "Provider already installed in $ma ($py), nothing to do"
        return 0
    fi
    stage="$(mktemp -d)"
    cp -a "$src" "$stage/$PROVIDER_DIR"
    printf '%s\n' "$hash" > "$stage/$PROVIDER_DIR/.content_hash"
    docker exec "$ma" sh -c 'rm -rf "$1"' _ "$dst/$PROVIDER_DIR" 2>/dev/null || true
    if ! docker cp "$stage/$PROVIDER_DIR" "$ma:$dst/"; then
        log "ERROR: docker cp failed"
        rm -rf "$stage"
        return 1
    fi
    rm -rf "$stage"
    docker exec "$ma" sh -c '[ -f "$1" ]' _ "$dst/$PROVIDER_DIR/__init__.py" || { log "ERROR: copy not found inside $ma"; return 1; }
    log "Provider copied into $ma ($py), restarting Music Assistant"
    docker restart "$ma" >/dev/null || { log "ERROR: restart failed"; return 1; }
    log "Music Assistant restarted"
    return 0
}

wait_for_docker() {
    until docker info >/dev/null 2>&1; do
        log "ERROR: no Docker access. Turn Protection mode OFF on the add-on's Info tab, then restart it."
        sleep "${DOCKER_WAIT_SECONDS:-60}"
        [ -n "${MAX_ITERATIONS:-}" ] && return 1
    done
    return 0
}

main() {
    local ma cur_id last_id="" retry_at=0 fails=0 now last_update iterations=0 delay
    read_options
    log "ArchiveTune Provider Watcher starting (auto update: $AUTO_UPDATE every ${UPDATE_INTERVAL_HOURS}h, ref: $REF)"
    wait_for_docker || return 1
    if [ "$AUTO_UPDATE" = "true" ]; then
        fetch_latest || true
    fi
    log "Provider source: $(provider_src)"
    last_update="$(date +%s)"
    while true; do
        now="$(date +%s)"
        ma="$(find_ma)"
        if [ -n "$ma" ]; then
            cur_id="$(container_id "$ma")"
            if [ -n "$cur_id" ] && [ "$cur_id" != "$last_id" ] && [ "$now" -ge "$retry_at" ]; then
                if install_provider "$ma"; then
                    last_id="$(container_id "$ma")"
                    fails=0
                else
                    fails=$((fails + 1))
                    delay=$((POLL_SECONDS * (1 << (fails > 5 ? 5 : fails))))
                    [ "$delay" -gt "$MAX_RETRY_SECONDS" ] && delay="$MAX_RETRY_SECONDS"
                    retry_at=$((now + delay))
                    log "Will retry in ${delay}s"
                fi
            fi
        elif [ -n "$last_id" ]; then
            log "Music Assistant stopped, waiting for it to come back"
            last_id=""
        fi
        if [ "$AUTO_UPDATE" = "true" ] && [ $((now - last_update)) -ge "$UPDATE_INTERVAL" ]; then
            last_update="$now"
            if fetch_latest && [ -n "$ma" ]; then
                last_id=""
                retry_at=0
            fi
        fi
        iterations=$((iterations + 1))
        if [ -n "${MAX_ITERATIONS:-}" ] && [ "$iterations" -ge "$MAX_ITERATIONS" ]; then
            return 0
        fi
        sleep "$POLL_SECONDS"
    done
}
