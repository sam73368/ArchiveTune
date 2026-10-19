#!/usr/bin/env python3
import os, re, subprocess, time, json
import xml.etree.ElementTree as ET

PKG = os.environ.get("PROBE_PKG", "moe.rukamori.archivetune.debug")
ACTIVITY = f"{PKG}/moe.rukamori.archivetune.MainActivity"
OUT = os.environ.get("CRAWL_OUT", "crawl-out")
STEPS = int(os.environ.get("CRAWL_STEPS", "14"))
os.makedirs(OUT, exist_ok=True)
report = open(os.path.join(OUT, "report.txt"), "w")
summary = []
SCREEN_W, SCREEN_H = 1920, 1080

ROUTES = [
    ("home", "home"),
    ("search", "search"),
    ("library", "library"),
    ("library_playlists", "library_playlists"),
    ("library_artists", "library_artists"),
    ("local_songs", "local_songs"),
    ("history", "history"),
    ("stats", "stats"),
    ("charts", "charts_screen"),
    ("new_release", "new_release"),
    ("news", "news"),
    ("account", "account"),
    ("listen_together", "listen_together"),
    ("customize_background", "customize_background"),
    ("auto_liked", "auto_playlist/liked"),
    ("top_playlist", "top_playlist/50"),
    ("cache_playlist", "cache_playlist/cache"),
    ("artist", "artist/UCedvOgsKFzcK3hA5taf3KoQ"),
    ("artist_songs", "artist/UCedvOgsKFzcK3hA5taf3KoQ/songs"),
    ("artist_albums", "artist/UCedvOgsKFzcK3hA5taf3KoQ/albums"),
    ("album", "album/MPREb_9nqEki4ZDpp"),
    ("settings", "settings"),
    ("settings_account", "settings/account"),
    ("settings_appearance", "settings/appearance"),
    ("settings_appearance_theme", "settings/appearance?section=theme"),
    ("settings_palette", "settings/appearance/palette_picker"),
    ("settings_player", "settings/player"),
    ("settings_player_playback", "settings/player?section=playback"),
    ("settings_player_queue", "settings/player?section=queue"),
    ("settings_player_misc", "settings/player?section=misc"),
    ("settings_audiophile", "settings/player/audiophile"),
    ("settings_canvas", "settings/player/canvas"),
    ("settings_sources", "settings/sources"),
    ("settings_lyrics", "settings/lyrics"),
    ("settings_lyrics_providers", "settings/lyrics/providers"),
    ("settings_content", "settings/content"),
    ("settings_privacy", "settings/privacy"),
    ("settings_integration", "settings/integration"),
    ("settings_lastfm", "settings/lastfm"),
    ("settings_ai", "settings/ai_integration"),
    ("settings_internet", "settings/internet"),
    ("settings_storage", "settings/storage"),
    ("settings_downloads", "settings/downloads"),
    ("settings_backup", "settings/backup_restore"),
    ("settings_misc", "settings/misc"),
    ("settings_update", "settings/update"),
    ("settings_about", "settings/about"),
    ("settings_hidden_playlists", "settings/hidden_playlists"),
    ("settings_android_auto", "settings/android_auto"),
    ("settings_aod", "settings/appearance/aod_customized"),
    ("settings_extras", "settings/appearance/extras"),
    ("settings_icon", "settings/appearance/icon"),
    ("settings_liquid_glass", "settings/appearance/liquid_glass"),
    ("settings_navbar", "settings/appearance/navigation_bar"),
    ("settings_theme_creator", "settings/appearance/theme_creator"),
    ("settings_applemusic", "settings/applemusic"),
    ("settings_deezer", "settings/deezer"),
    ("settings_discord", "settings/discord"),
    ("settings_jiosaavn", "settings/jiosaavn"),
    ("settings_qobuz", "settings/qobuz"),
    ("settings_telegram", "settings/telegram"),
    ("settings_tidal", "settings/tidal"),
    ("settings_lt", "settings/integrations/listen_together"),
    ("settings_romanisation", "settings/lyrics/romanisation"),
    ("settings_changelog", "settings/changelog"),
    ("settings_export_songs", "settings/storage/export_songs"),
    ("settings_language_packs", "settings/language_packs"),
    ("settings_logcat", "settings/logcat"),
    ("lastfm_dashboard", "lastfm_dashboard"),
    ("year_in_music", "year_in_music"),
    ("library_spotify", "library_spotify_playlists"),
    ("car_mode", "car_mode"),
]


def adb(*args, timeout=60):
    try:
        return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout).stdout
    except Exception:
        return ""


def key(code, wait=0.7):
    source = "dpad" if "DPAD" in code else "keyboard"
    adb("shell", "input", source, "keyevent", code)
    time.sleep(wait)


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")


def parse(xml):
    try:
        return ET.fromstring(xml)
    except Exception:
        return None


def bounds_of(el):
    nums = [int(v) for v in re.findall(r"-?\d+", el.get("bounds") or "")]
    return nums if len(nums) == 4 else None


def label_of(el):
    parts = [el.get("text") or el.get("content-desc") or ""]
    for sub in el.iter("node"):
        t = sub.get("text") or sub.get("content-desc")
        if t:
            parts.append(t)
    joined = " | ".join(p for p in parts if p)
    return joined[:60]


def scan(xml):
    root = parse(xml)
    focused = None
    nodes = []
    if root is None:
        return None, []
    for el in root.iter("node"):
        b = bounds_of(el)
        if not b:
            continue
        w, h = b[2] - b[0], b[3] - b[1]
        on_screen = b[2] > 0 and b[3] > 0 and b[0] < SCREEN_W and b[1] < SCREEN_H and w > 8 and h > 8
        interactive = el.get("focusable") == "true" or el.get("clickable") == "true"
        if el.get("focused") == "true" and on_screen:
            focused = (tuple(b), label_of(el))
        if interactive and on_screen and el.get("enabled") != "false":
            nodes.append((tuple(b), label_of(el)))
    return focused, nodes


def shot(tag):
    png = os.path.join(OUT, tag + ".png")
    subprocess.run(f"adb exec-out screencap -p > {png}", shell=True)
    jpg = os.path.join(OUT, tag + ".jpg")
    r = subprocess.run(["convert", png, "-resize", "50%", "-quality", "55", jpg], capture_output=True)
    if r.returncode == 0:
        os.remove(png)


def say(line):
    print(line, flush=True)
    report.write(line + "\n")
    report.flush()


def center(b):
    return ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)


def in_direction(src, dst, direction):
    sx, sy = center(src)
    dx, dy = center(dst)
    if direction == "DOWN":
        return dst[1] >= src[3] - 4 and dy > sy
    if direction == "UP":
        return dst[3] <= src[1] + 4 and dy < sy
    if direction == "RIGHT":
        return dst[0] >= src[2] - 4 and dx > sx
    return dst[2] <= src[0] + 4 and dx < sx


def crawl(name, route):
    adb("shell", "am", "start", "-n", ACTIVITY, "--es", "navigate_to", route)
    time.sleep(7)
    xml = dump()
    focused, nodes = scan(xml)
    shot(f"{name}_00")
    issues = []
    if not nodes:
        issues.append("NO_INTERACTIVE_NODES (blank, loading or not reachable)")
    if focused is None and nodes:
        key("KEYCODE_DPAD_DOWN", 1.0)
        xml = dump()
        focused, nodes2 = scan(xml)
        if focused is None:
            issues.append("NO_FOCUS_AFTER_FIRST_KEY")
        nodes = nodes2 or nodes
    visited = {focused[0]} if focused else set()
    seen_all = {n[0]: n[1] for n in nodes}
    sequence = ["DOWN"] * STEPS + ["RIGHT"] * 5 + ["LEFT"] * 5 + ["UP"] * 6 + ["RIGHT"] * 3 + ["DOWN"] * 4
    last_new = 0
    for i, direction in enumerate(sequence):
        before = focused
        key(f"KEYCODE_DPAD_{direction}", 0.6)
        xml = dump()
        focused, cur_nodes = scan(xml)
        for n in cur_nodes:
            seen_all.setdefault(n[0], n[1])
        if focused is None:
            issues.append(f"FOCUS_LOST after {direction} (step {i})")
            shot(f"{name}_lost{i}")
            key("KEYCODE_DPAD_DOWN", 0.8)
            xml = dump()
            focused, _ = scan(xml)
            continue
        moved = before is None or focused[0] != before[0]
        if moved:
            visited.add(focused[0])
            last_new = i
        elif before is not None:
            blockers = [n for n in cur_nodes if n[0] not in visited and in_direction(before[0], n[0], direction)]
            if blockers:
                issues.append(
                    f"STUCK {direction} at '{before[1]}' but reachable-looking target(s): "
                    + "; ".join(f"'{b[1]}'" for b in blockers[:3])
                )
                shot(f"{name}_stuck{i}")
    unreached = [lab for b, lab in seen_all.items() if b not in visited]
    coverage = (len(visited) / max(1, len(seen_all))) * 100
    if unreached and coverage < 70:
        issues.append(f"LOW_COVERAGE {coverage:.0f}% unreached: " + "; ".join(f"'{u}'" for u in unreached[:6]))
    shot(f"{name}_end")
    say(f"## {name} ({route}) interactive={len(seen_all)} visited={len(visited)} coverage={coverage:.0f}%")
    for issue in issues[:12]:
        say(f"   - {issue}")
    summary.append({"name": name, "route": route, "interactive": len(seen_all), "visited": len(visited), "coverage": round(coverage), "issues": issues[:12]})
    for _ in range(3):
        key("KEYCODE_BACK", 0.8)


adb("shell", "settings", "put", "global", "window_animation_scale", "0")
adb("shell", "monkey", "-p", PKG, "1")
time.sleep(25)
for i in range(8):
    xml = dump()
    root = parse(xml)
    labels = [e.get("text") for e in root.iter("node") if e.get("text")] if root is not None else []
    if "Install now" in labels:
        key("KEYCODE_BACK", 2)
        continue
    if any(w in labels for w in ["Next", "Skip for now", "Start listening"]):
        key("KEYCODE_DPAD_CENTER", 3)
        continue
    break
time.sleep(6)

for name, route in ROUTES:
    try:
        crawl(name, route)
    except Exception as error:
        say(f"## {name} ({route}) CRAWL_ERROR {error}")
    if len(summary) % 8 == 0:
        subprocess.run(["bash", "scripts/tv_crawl_publish.sh", OUT])

total = sum(len(s["issues"]) for s in summary)
say(f"TOTAL routes={len(summary)} issues={total}")
json.dump(summary, open(os.path.join(OUT, "summary.json"), "w"), indent=1)
subprocess.run(f"adb logcat -d -t 6000 | grep -E 'FATAL EXCEPTION|ANR in|AndroidRuntime' > {OUT}/crashes.txt", shell=True)
report.close()
subprocess.run(["bash", "scripts/tv_crawl_publish.sh", OUT])
