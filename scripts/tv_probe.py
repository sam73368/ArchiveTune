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
    source = "dpad" if "DPAD" in code else "keyboard"
    if longpress:
        adb("shell", "input", source, "keyevent", "--longpress", code)
    else:
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
    if step[0] % 12 == 0:
        subprocess.run(["bash", "scripts/tv_probe_publish.sh", OUT])
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

if os.environ.get("PROBE_FOCUS") != "player":
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

    # Library
    key("KEYCODE_DPAD_DOWN")
    key("KEYCODE_DPAD_DOWN")
    key("KEYCODE_DPAD_CENTER", 4)
    snap("library_open")
    key("KEYCODE_DPAD_RIGHT")
    snap("library_right")
    for i in range(6):
        key("KEYCODE_DPAD_DOWN", 0.9)
        snap(f"library_down_{i}")
    for i in range(3):
        key("KEYCODE_DPAD_RIGHT", 0.9)
        snap(f"library_right_{i}")

    # Search with the on-screen keyboard
    for i in range(8):
        key("KEYCODE_DPAD_LEFT", 0.6)
    for i in range(4):
        key("KEYCODE_DPAD_UP", 0.6)
    xml = snap("rail_reset")
    key("KEYCODE_DPAD_DOWN")
    key("KEYCODE_DPAD_CENTER", 4)
    snap("search_open")
    rows = ["ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ'-&.", "123456", "7890ÉÈ"]
    pos = [0, 0]


    def type_char(ch):
        for r, row in enumerate(rows):
            if ch in row:
                c = row.index(ch)
                break
        while pos[0] < r:
            key("KEYCODE_DPAD_DOWN", 0.5); pos[0] += 1
        while pos[0] > r:
            key("KEYCODE_DPAD_UP", 0.5); pos[0] -= 1
        while pos[1] < c:
            key("KEYCODE_DPAD_RIGHT", 0.5); pos[1] += 1
        while pos[1] > c:
            key("KEYCODE_DPAD_LEFT", 0.5); pos[1] -= 1
        key("KEYCODE_DPAD_CENTER", 0.7)


    for ch in "EMINEM":
        type_char(ch)
    snap("search_typed")
    xml = walk_until("KEYCODE_DPAD_DOWN", ["Search"], 6, "to_search_key")
    key("KEYCODE_DPAD_CENTER", 8)
    snap("search_results")
    for i in range(6):
        key("KEYCODE_DPAD_DOWN", 0.9)
        snap(f"results_down_{i}")
    key("KEYCODE_DPAD_RIGHT", 0.9)
    snap("results_right")
    key("KEYCODE_BACK", 3)
    key("KEYCODE_BACK", 3)
    for i in range(8):
        key("KEYCODE_DPAD_LEFT", 0.6)
    for i in range(4):
        key("KEYCODE_DPAD_UP", 0.6)
    snap("rail_home_again")
    key("KEYCODE_DPAD_CENTER", 3)



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
key("KEYCODE_DPAD_DOWN", 1.0)
snap("album_first_song")
key("KEYCODE_DPAD_CENTER", 8)
snap("play_attempt")
for i in range(3):
    key("KEYCODE_DPAD_LEFT", 0.8)
    snap(f"dialog_left_{i}")
key("KEYCODE_DPAD_CENTER", 3)
snap("dialog_closed")
for i in range(5):
    key("KEYCODE_DPAD_DOWN", 0.9)
    snap(f"to_miniplayer_{i}")
key("KEYCODE_DPAD_CENTER", 5)
snap("miniplayer_center")
for k in ["KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_LEFT"]:
    key(k, 1.0)
    snap("player_" + k.split("_")[-1].lower())
key("KEYCODE_BACK", 3)
snap("player_back")
for i in range(3):
    key("KEYCODE_DPAD_UP", 0.9)
    snap(f"after_player_up_{i}")

subprocess.run(f"adb logcat -d -t 4000 > {OUT}/logcat.txt", shell=True)
report.close()
subprocess.run(["bash", "scripts/tv_probe_publish.sh", OUT])
