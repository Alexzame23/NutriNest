#!/usr/bin/env python3
"""Measure an installed debug build on a dedicated Android test device using ADB."""
import argparse
import json
import math
import re
import statistics
import subprocess
from pathlib import Path


PACKAGE = "com.zame.nutrinest"
TEST_CLASS = f"{PACKAGE}.data.local.PerformanceBenchmark"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--instrumentation-since", help="Reuse a completed run's logcat timestamp")
    args = parser.parse_args()

    def adb(*command):
        return subprocess.check_output(
            [args.adb, "-s", args.serial, *command], text=True, encoding="utf-8", errors="replace",
            stderr=subprocess.STDOUT, timeout=900
        ).strip()

    result = {
        "device": adb("shell", "getprop", "ro.product.model"),
        "fingerprint": adb("shell", "getprop", "ro.build.fingerprint"),
        "abi": adb("shell", "getprop", "ro.product.cpu.abi"),
        "api": adb("shell", "getprop", "ro.build.version.sdk"),
    }
    marker = args.instrumentation_since
    if marker is None:
        marker = adb("shell", "date", "+%m-%d_%H:%M:%S.000").replace("_", " ")
        print("Running catalog, cache and basket instrumentation", flush=True)
        completed = adb("shell", "am", "instrument", "-w", "-e", "class", TEST_CLASS,
                        "-e", "nutrinestPerf", "true", f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner")
        if "OK (4 tests)" not in completed:
            raise SystemExit(completed)
    log = adb("logcat", "-d", "-s", "-T", marker, "NutriPerf25:I")
    operations = {}
    for name, values in re.findall(r"NutriPerf25\s*:\s*(\w+) ms=([\d.,]+)", log):
        operations[name] = [float(value) for value in values.split(",")]
    if len(operations) != 28:
        raise SystemExit(f"Missing instrumentation samples:\n{log}")
    result["instrumentation_details"] = re.findall(r"NutriPerf25\s*:\s*(.+(?:bytes|kib)=.+)", log)

    print("Measuring Activity startup", flush=True)
    component = f"{PACKAGE}/.MainActivity"
    adb("shell", "am", "start", "-W", "-S", "-n", component)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    cold, warm, hot = [], [], []

    def launch(*flags):
        output = adb("shell", "am", "start", "-W", *flags, "-n", component)
        match = re.search(r"^TotalTime:\s*(\d+)", output, re.MULTILINE)
        if not match or "Status: ok" not in output:
            raise SystemExit(output)
        state = re.search(r"^LaunchState:\s*(\w+)", output, re.MULTILINE)
        return int(match[1]), state[1] if state else None

    for _ in range(11):
        value, state = launch("-S")
        if state != "COLD":
            raise SystemExit(f"Expected cold launch, observed {state}")
        cold.append(value)
        pid = adb("shell", "pidof", PACKAGE)
        value, state = launch("-f", "0x10008000")  # NEW_TASK | CLEAR_TASK
        if state != "WARM" or adb("shell", "pidof", PACKAGE) != pid:
            raise SystemExit(f"Expected same-process activity recreation, observed {state}")
        warm.append(value)
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        resumed = adb("shell", "am", "start", "-W", "-n", component)
        wait = re.search(r"^WaitTime:\s*(\d+)", resumed, re.MULTILINE)
        if not wait or "brought to the front" not in resumed or adb("shell", "pidof", PACKAGE) != pid:
            raise SystemExit(resumed)
        hot.append(int(wait[1]))
    operations.update(startup_cold=cold, startup_warm=warm, startup_hot_wait=hot)
    result["startup_memory"] = adb("shell", "dumpsys", "meminfo", PACKAGE)
    gfx = adb("shell", "dumpsys", "gfxinfo", PACKAGE)
    result["last_process_frames"] = re.findall(r"^(?:Total frames rendered|Janky frames|95th percentile):.*", gfx, re.MULTILINE)
    result["operations"] = {
        name: {
            "median_ms": round(statistics.median(values), 3),
            "p95_ms": round(sorted(values)[math.ceil(0.95 * len(values)) - 1], 3),
            "samples_ms": [round(value, 3) for value in values]
        } for name, values in operations.items()
    }
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
