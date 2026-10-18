#!/usr/bin/env python3
import os, re, subprocess, sys, time

PKG = os.environ.get("PROBE_PKG", "moe.rukamori.archivetune.debug")
OUT = os.environ.get("PROBE_OUT", "probe-out")
os.makedirs(OUT, exist_ok=True)
report = open(os.path.join(OUT, "report.txt"), "w")
step = [0]


def adb(*args, check=False, capture=True):
    r = subprocess.run(["adb", *args], capture_output=capture, text=True)
    return r.stdout if capture else ""


def key(code, wait=1.2):
    adb("shell", "input", "keyevent", code)
    time.sleep(wait)


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")


def focused_nodes(xml):
    nodes = re.findall(r"<node [^>]*>", xml)
    res = []
    for n in nodes:
        if 'focused="true"' in n:
            text = re.search(r'text="([^"]*)"', n).group(1)
            desc = re.search(r'content-desc="([^"]*)"', n).group(1)
            cls = re.search(r'class="([^"]*)"', n).group(1)
            bounds = re.search(r'bounds="([^"]*)"', n).group(1)
            res.append(f"{cls} text='{text}' desc='{desc}' bounds={bounds}")
    return res


def texts(xml):
    return re.findall(r'text="([^"]+)"', xml)


def snap(name):
    step[0] += 1
    tag = f"{step[0]:02d}_{name}"
    xml = dump()
    open(os.path.join(OUT, tag + ".xml"), "w").write(xml)
    subprocess.run(f"adb exec-out screencap -p > {OUT}/{tag}.png", shell=True)
    foc = focused_nodes(xml)
    win = adb("shell", "dumpsys", "window", "windows")
    cur = re.findall(r"mCurrentFocus=.*", win)
    line = f"[{tag}] focus={foc if foc else 'NONE'} window={cur[:1]} visible_texts={texts(xml)[:12]}"
    print(line)
    report.write(line + "\n")
    report.flush()
    return xml


def has_any(xml, words):
    t = texts(xml)
    return any(w in t for w in words)


adb("shell", "settings", "put", "global", "window_animation_scale", "0")
adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LEANBACK_LAUNCHER", "1")
time.sleep(4)
adb("shell", "monkey", "-p", PKG, "1")
time.sleep(18)
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

time.sleep(6)
xml = snap("home")
if has_any(xml, ["Install now"]):
    key("KEYCODE_BACK", 2)
    xml = snap("home_after_update")

for i in range(4):
    key("KEYCODE_DPAD_DOWN")
    snap(f"rail_down_{i}")
key("KEYCODE_DPAD_CENTER", 4)
snap("settings_open")
key("KEYCODE_DPAD_RIGHT")
snap("settings_right")
for i in range(3):
    key("KEYCODE_DPAD_DOWN")
    snap(f"settings_down_{i}")
key("KEYCODE_DPAD_UP")
key("KEYCODE_DPAD_UP")
key("KEYCODE_DPAD_UP")
snap("settings_top")
key("KEYCODE_DPAD_CENTER", 4)
snap("subpage_open")
for i in range(6):
    key("KEYCODE_DPAD_DOWN")
    snap(f"subpage_down_{i}")
key("KEYCODE_DPAD_RIGHT")
snap("subpage_right")
key("KEYCODE_DPAD_LEFT")
snap("subpage_left")

key("KEYCODE_BACK", 3)
snap("back_settings")
key("KEYCODE_BACK", 3)
snap("back_home")
for i in range(3):
    key("KEYCODE_DPAD_UP")
key("KEYCODE_DPAD_CENTER", 3)
snap("home_selected")
key("KEYCODE_DPAD_RIGHT")
snap("home_content")
for i in range(3):
    key("KEYCODE_DPAD_DOWN")
    snap(f"home_down_{i}")
key("KEYCODE_DPAD_CENTER", 6)
snap("detail_open")
for i in range(6):
    key("KEYCODE_DPAD_DOWN")
    snap(f"detail_down_{i}")

subprocess.run(f"adb logcat -d -t 3000 > {OUT}/logcat.txt", shell=True)
report.close()
