#!/usr/bin/env python3
import os, re, subprocess, time
import xml.etree.ElementTree as ET

PKG = os.environ.get("PROBE_PKG", "moe.rukamori.archivetune.debug")
OUT = os.environ.get("PROBE_OUT", "probe-out")
os.makedirs(OUT, exist_ok=True)
report = open(os.path.join(OUT, "report.txt"), "w")
step = [0]


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout


def key(code, wait=1.2, longpress=False):
    if longpress:
        adb("shell", "input", "keyevent", "--longpress", code)
    else:
        adb("shell", "input", "keyevent", code)
    time.sleep(wait)


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")


def parse(xml):
    try:
        return ET.fromstring(xml)
    except Exception:
        return None


def focused_info(xml):
    root = parse(xml)
    if root is None:
        return None, []
    for el in root.iter("node"):
        if el.get("focused") == "true":
            sub = [e.get("text") or e.get("content-desc") for e in el.iter("node")]
            sub = [t for t in sub if t]
            return el.get("bounds"), sub
    return None, []


def texts(xml):
    root = parse(xml)
    if root is None:
        return []
    return [e.get("text") for e in root.iter("node") if e.get("text")]


def snap(name):
    step[0] += 1
    tag = f"{step[0]:02d}_{name}"
    xml = dump()
    open(os.path.join(OUT, tag + ".xml"), "w").write(xml)
    subprocess.run(f"adb exec-out screencap -p > {OUT}/{tag}.png", shell=True)
    b, sub = focused_info(xml)
    line = f"[{tag}] focus={b} focus_texts={sub[:4]} visible={texts(xml)[:8]}"
    print(line)
    report.write(line + "\n")
    report.flush()
    return xml


def focus_contains(xml, words):
    _, sub = focused_info(xml)
    joined = " | ".join(sub)
    return any(w in joined for w in words)


def has_any(xml, words):
    t = texts(xml)
    return any(w in t for w in words)


def walk_until(direction, words, maxsteps, label):
    xml = dump()
    for i in range(maxsteps):
        if focus_contains(xml, words):
            return snap(f"{label}_found")
        key(direction)
        xml = snap(f"{label}_{i}")
    return xml


adb("shell", "settings", "put", "global", "window_animation_scale", "0")
adb("shell", "monkey", "-p", PKG, "1")
time.sleep(22)
xml = snap("launch")

for i in range(8):
    if has_any(xml, ["Install now"]):
        key("KEYCODE_BACK", 2)
        xml = snap("dismiss_update")
        continue
    if has_any(xml, ["Next", "Skip for now", "Start listening"]):
        key("KEYCODE_DPAD_CENTER", 3)
        xml = snap(f"onboarding_{i}")
        continue
    break

time.sleep(8)
xml = snap("home")
if has_any(xml, ["Install now"]):
    key("KEYCODE_BACK", 2)
    snap("home_after_update")

# Settings: rail -> settings -> Appearance -> scroll down
for i in range(3):
    key("KEYCODE_DPAD_DOWN")
key("KEYCODE_DPAD_CENTER", 4)
snap("settings_open")
key("KEYCODE_DPAD_RIGHT")
snap("settings_right")
walk_until("KEYCODE_DPAD_DOWN", ["Appearance"], 8, "to_appearance")
key("KEYCODE_DPAD_CENTER", 4)
snap("appearance_open")
for i in range(10):
    key("KEYCODE_DPAD_DOWN", 0.9)
    snap(f"appearance_down_{i}")
for i in range(4):
    key("KEYCODE_DPAD_UP", 0.9)
    snap(f"appearance_up_{i}")
key("KEYCODE_BACK", 3)
snap("back_to_settings")
walk_until("KEYCODE_DPAD_DOWN", ["Lyrics"], 12, "to_lyrics")
key("KEYCODE_DPAD_CENTER", 4)
snap("lyrics_open")
for i in range(6):
    key("KEYCODE_DPAD_DOWN", 0.9)
    snap(f"lyrics_down_{i}")
key("KEYCODE_BACK", 3)
key("KEYCODE_BACK", 3)
snap("back_home")

# Home content -> find the "New releases" shelf
def node_bounds(xml, text):
    root = parse(xml)
    if root is None:
        return None
    for el in root.iter("node"):
        if el.get("text") == text:
            return [int(v) for v in re.findall(r"\d+", el.get("bounds"))]
    return None


def under_header(xml, header):
    hb = node_bounds(xml, header)
    fb, _ = focused_info(xml)
    if hb is None or fb is None:
        return False
    f = [int(v) for v in re.findall(r"\d+", fb)]
    return 0 <= f[1] - hb[3] <= 160


key("KEYCODE_DPAD_RIGHT")
xml = snap("home_content")
for i in range(18):
    if under_header(xml, "New releases"):
        break
    key("KEYCODE_DPAD_DOWN", 1.0)
    xml = snap(f"home_scan_{i}")
snap("home_card_choice")
key("KEYCODE_DPAD_CENTER", 8)
snap("album_open")
for i in range(8):
    key("KEYCODE_DPAD_DOWN", 1.0)
    snap(f"album_down_{i}")
for i in range(3):
    key("KEYCODE_DPAD_UP", 1.0)
    snap(f"album_up_{i}")
key("KEYCODE_DPAD_CENTER", 5)
snap("album_play")
key("KEYCODE_DPAD_CENTER", 2, longpress=True)
snap("longpress_menu")
for i in range(3):
    key("KEYCODE_DPAD_DOWN", 0.9)
    snap(f"menu_down_{i}")
key("KEYCODE_BACK", 2)
snap("menu_closed")
key("KEYCODE_DPAD_DOWN", 1.0)
snap("after_menu_down")
for i in range(12):
    key("KEYCODE_DPAD_UP", 0.8)
xml = snap("album_top")
for i in range(4):
    key("KEYCODE_DPAD_RIGHT", 0.8)
    snap(f"album_top_right_{i}")
key("KEYCODE_BACK", 3)
snap("album_back")
for i in range(10):
    key("KEYCODE_DPAD_DOWN", 0.8)
xml = snap("bottom_reach")
for i in range(3):
    key("KEYCODE_DPAD_DOWN", 0.8)
    snap(f"bottom_more_{i}")
key("KEYCODE_DPAD_CENTER", 4)
snap("bottom_center")
for i in range(4):
    key("KEYCODE_DPAD_DOWN", 0.8)
    snap(f"player_down_{i}")
key("KEYCODE_DPAD_RIGHT", 0.8)
snap("player_right")
key("KEYCODE_BACK", 3)
snap("player_back")

subprocess.run(f"adb logcat -d -t 4000 > {OUT}/logcat.txt", shell=True)
report.close()
