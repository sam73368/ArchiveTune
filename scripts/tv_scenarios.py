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

SCENARIOS = [
    ("settings_player", "settings/player", ["RIGHT"] + ["DOWN"] * 7 + ["UP"] * 8 + ["DOWN"]),
    ("settings_appearance", "settings/appearance", ["RIGHT"] + ["DOWN"] * 9 + ["UP"] * 10),
    ("settings_sources", "settings/sources", ["RIGHT"] + ["DOWN"] * 5 + ["UP"] * 6),
    ("artist_main_full", f"artist/{ARTIST}", ["RIGHT"] + ["DOWN"] * 6 + ["UP"] * 7 + ["LEFT"]),
    ("artist_albums_full", f"artist/{ARTIST}/albums", ["RIGHT", "DOWN", "UP", "DOWN"]),
    ("library_playlists_full", "library_playlists", ["RIGHT"] + ["DOWN"] * 3 + ["UP"] * 4),
    ("history_full", "history", ["RIGHT", "DOWN", "DOWN", "UP", "UP", "UP"]),
    ("stats_full", "stats", ["RIGHT", "DOWN", "UP"]),
    ("mini_player", f"artist/{ARTIST}", ["TAP:Play", "LEFT", "DOWN", "DOWN", "DOWN", "DOWN", "RIGHT", "LEFT", "DOWN", "RIGHT", "RIGHT", "RIGHT", "LEFT", "UP"]),
    ("rail_home_from_artist", f"artist/{ARTIST}", ["RIGHT", "DOWN", "LEFT", "UP", "UP", "CENTER"]),
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


def run_scenario(name, route, keys):
    adb("shell", "am", "start", "-n", ACTIVITY, "--es", "navigate_to", route)
    time.sleep(7)
    dismiss_dialogs()
    shot(f"{name}_00_start")
    lines = [f"## {name} ({route})", "   start: " + describe_focus()]
    for i, k in enumerate(keys, start=1):
        if k.startswith("TAP:"):
            tapped = tap_text_nodes([k[4:]])
            time.sleep(5)
            shot(f"{name}_{i:02d}_TAP")
            lines.append(f"   {i:02d} {k} tapped={tapped}: " + describe_focus())
            continue
        code = "KEYCODE_DPAD_CENTER" if k == "CENTER" else f"KEYCODE_DPAD_{k}"
        key(code, 1.2)
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

for name, route, keys in SCENARIOS:
    try:
        run_scenario(name, route, keys)
    except Exception as error:
        say(f"## {name} SCENARIO_ERROR {error}")
    subprocess.run(["bash", "scripts/tv_crawl_publish.sh", OUT])

subprocess.run(f"adb logcat -d -t 6000 | grep -E 'FATAL EXCEPTION|ANR in|AndroidRuntime' > {OUT}/crashes.txt", shell=True)
report.close()
subprocess.run(["bash", "scripts/tv_crawl_publish.sh", OUT])
