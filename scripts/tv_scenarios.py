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
    try:
        r = subprocess.run(["convert", png, "-resize", "50%", "-quality", "55", jpg], capture_output=True)
        if r.returncode == 0:
            os.remove(png)
    except Exception:
        pass


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



DIALOG_BUTTONS = ["Later", "Not now", "Dismiss", "No thanks", "Skip", "Cancel", "Close", "Plus tard", "Pas maintenant"]
ARTIST = "UCedvOgsKFzcK3hA5taf3KoQ"

PLAYER_KEYS = ['TAP:Play', 'DISMISS', 'EXPAND', 'UP', 'UP', 'UP', 'DOWN', 'DOWN', 'DOWN', 'DOWN', 'DOWN', 'LEFT', 'LEFT', 'LEFT', 'LEFT', 'LEFT', 'RIGHT', 'RIGHT', 'UP', 'UP', 'LEFT', 'LEFT', 'LEFT', 'LEFT', 'LEFT', 'RIGHT', 'DOWN', 'DOWN', 'RIGHT', 'LEFT', 'LEFT', 'LEFT', 'LEFT', 'LEFT', 'DOWN', 'DOWN', 'DOWN', 'RIGHT', 'RIGHT', 'RIGHT']

LYRICS_KEYS = ["TAP:Play", "DISMISS", "EXPAND", "TAP:Lyrics", "DOWN", "DOWN", "LEFT", "RIGHT", "RIGHT", "TAP:Lyrics", "DOWN", "UP", "UP", "LEFT", "LEFT", "RIGHT"]

QUEUE_KEYS = ["TAP:Play", "DISMISS", "EXPAND", "TAP:Queue", "DOWN", "DOWN", "UP", "CENTER", "BACK", "DOWN", "LEFT", "BACK", "BACK"]

SETTINGS_ROUTES = [
    "settings", "settings/account", "settings/appearance", "settings/appearance/extras", "settings/appearance/navigation_bar",
    "settings/appearance/palette_picker", "settings/appearance/icon", "settings/player", "settings/player/audiophile",
    "settings/player/canvas", "settings/lyrics", "settings/lyrics/providers", "settings/content", "settings/privacy",
    "settings/storage", "settings/downloads", "settings/backup_restore", "settings/sources", "settings/integration",
    "settings/internet", "settings/tidal", "settings/qobuz", "settings/deezer", "settings/lastfm", "settings/discord",
    "settings/ai_integration", "settings/android_auto", "settings/language_packs", "settings/hidden_playlists",
    "settings/misc", "settings/update", "settings/about", "settings/po_token", "settings/jiosaavn", "settings/telegram",
]

SETTINGS_KEYS = ["RIGHT", "DOWN", "DOWN", "DOWN", "DOWN", "UP", "UP", "CENTER", "DOWN", "BACK", "UP", "LEFT"]

PAGE_ROUTES = [
    "home", "search", "library", "local_songs", "history", "library_playlists", "library_artists", "stats", "news",
    "account", "new_release", "charts_screen", "mood_and_genres", "smart_playlists", "listen_together", "customize_background",
    f"artist/{ARTIST}/songs", f"artist/{ARTIST}/albums",
]

PAGE_KEYS = ["RIGHT", "DOWN", "DOWN", "DOWN", "UP", "UP", "RIGHT", "LEFT", "LEFT", "CENTER", "BACK", "DOWN"]

TOP_SONGS_KEYS = ["RIGHT", "DOWN", "DOWN", "UP", "UP", "UP", "UP", "UP", "UP", "UP", "UP", "CENTER", "DOWN", "DOWN", "DOWN", "UP", "UP", "UP", "UP", "UP", "UP", "UP", "DOWN", "DOWN", "DOWN", "LEFT"]

MINI_KEYS = ["TAP:Play", "DISMISS", "EXPAND", "LEFT", "LEFT", "LEFT", "LEFT", "LEFT", "DOWN", "DOWN", "DOWN", "DOWN", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "LEFT", "LEFT", "LEFT", "RIGHT"]

TOP_SONGS_ITEMS_KEYS = ["TAP:Top songs", "DISMISS", "DOWN", "DOWN", "UP", "UP", "UP", "UP", "UP", "UP", "UP", "UP", "DOWN", "DOWN", "DOWN", "LEFT"]

SCENARIOS = [
    ("top_songs_page", f"artist/{ARTIST}", TOP_SONGS_KEYS, "all"),
    ("top_songs_items", f"artist/{ARTIST}", TOP_SONGS_ITEMS_KEYS, "all"),
    ("artist_main_full", f"artist/{ARTIST}", ["RIGHT"] + ["DOWN"] * 8 + ["UP"] * 9 + ["LEFT"], "sparse"),
] + [(r.replace("/", "_"), r, SETTINGS_KEYS, "sparse") for r in SETTINGS_ROUTES] + [(r.replace("/", "_"), r, PAGE_KEYS, "sparse") for r in PAGE_ROUTES] + [
    ("mini_player_rail", f"artist/{ARTIST}", MINI_KEYS, "all"),
    ("player_nav", f"artist/{ARTIST}", PLAYER_KEYS, "all"),
    ("player_lyrics", f"artist/{ARTIST}", LYRICS_KEYS, "all"),
    ("player_queue", f"artist/{ARTIST}", QUEUE_KEYS, "all"),
]


def tap_text_nodes(labels):
    xml = dump()
    root = parse(xml)
    if root is None:
        return False
    for el in root.iter("node"):
        text = (el.get("text") or el.get("content-desc") or "").strip()
        if text in labels:
            b = bounds_of(el)
            if b:
                cx, cy = center(b)
                adb("shell", "input", "tap", str(int(cx)), str(int(cy)))
                time.sleep(1.2)
                return True
    return False


def dismiss_dialogs():
    for _ in range(4):
        if not tap_text_nodes(DIALOG_BUTTONS):
            break


def describe_focus():
    focused, nodes = scan(dump())
    if focused is None:
        return "focus=None interactive=%d" % len(nodes)
    return "focus='%s' bounds=%s interactive=%d" % (focused[1], list(focused[0]), len(nodes))


def run_scenario(name, route, keys, shots="all"):
    adb("shell", "am", "start", "-n", ACTIVITY, "--es", "navigate_to", route)
    time.sleep(7)
    dismiss_dialogs()
    shot(f"{name}_00_start")
    lines = [f"## {name} ({route})", "   start: " + describe_focus()]
    for i, k in enumerate(keys, start=1):
        if k == "EXPAND":
            adb("shell", "am", "start", "-n", ACTIVITY, "--ez", "expand_player", "true")
            time.sleep(5)
            shot(f"{name}_{i:02d}_EXPAND")
            lines.append(f"   {i:02d} EXPAND: " + describe_focus())
            continue
        if k == "BACK":
            key("KEYCODE_BACK", 1.2)
            if shots == "all" or i % 3 == 0:
                shot(f"{name}_{i:02d}_BACK")
            lines.append(f"   {i:02d} BACK: " + describe_focus())
            continue
        if k == "DISMISS":
            dismiss_dialogs()
            shot(f"{name}_{i:02d}_DISMISS")
            lines.append(f"   {i:02d} DISMISS: " + describe_focus())
            continue
        if k.startswith("TAP:"):
            tapped = tap_text_nodes([k[4:]])
            time.sleep(5)
            shot(f"{name}_{i:02d}_TAP")
            lines.append(f"   {i:02d} {k} tapped={tapped}: " + describe_focus())
            continue
        code = "KEYCODE_DPAD_CENTER" if k == "CENTER" else f"KEYCODE_DPAD_{k}"
        key(code, 1.2)
        if shots == "all" or i % 3 == 0 or k == "CENTER":
            shot(f"{name}_{i:02d}_{k}")
        lines.append(f"   {i:02d} {k}: " + describe_focus())
    for line in lines:
        say(line)
    for _ in range(3):
        key("KEYCODE_BACK", 0.8)


adb("shell", "settings", "put", "global", "window_animation_scale", "0")
adb("shell", "monkey", "-p", PKG, "1")
time.sleep(25)
for _ in range(8):
    xml = dump()
    root = parse(xml)
    labels = [e.get("text") for e in root.iter("node") if e.get("text")] if root is not None else []
    if any(w in labels for w in ["Next", "Skip for now", "Start listening"]):
        key("KEYCODE_DPAD_CENTER", 3)
        continue
    break
time.sleep(6)
dismiss_dialogs()

for name, route, keys, shots in SCENARIOS:
    try:
        run_scenario(name, route, keys, shots)
    except Exception as error:
        say(f"## {name} SCENARIO_ERROR {error}")
    subprocess.run(["bash", "scripts/tv_crawl_publish.sh", OUT])

subprocess.run(f"adb logcat -d -t 6000 | grep -E 'FATAL EXCEPTION|ANR in|AndroidRuntime' > {OUT}/crashes.txt", shell=True)
report.close()
subprocess.run(["bash", "scripts/tv_crawl_publish.sh", OUT])
