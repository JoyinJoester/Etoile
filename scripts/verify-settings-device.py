"""Run production UI tests on emulator-5554, retaining installed apps and local data.

Uses explicit `adb install -r` and `am instrument`; never calls connectedAndroidTest,
uninstall, pm clear or an orchestrator. Screenshots may contain visible account data;
review locally before choosing any evidence to commit.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SERIAL = "emulator-5554"
PACKAGE = "takagi.joyin.github.etoile"
CLASS = "takagi.ru.monica.acceptance.SettingsDeviceTest"


def parse_results(output):
    """Require completed test statuses; adb's exit status alone does not prove success."""
    pending = {}
    results = []
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, sep, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if sep:
                pending[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.rsplit(":", 1)[1])
            if code != 1 and "test" in pending:
                results.append({"class": pending.get("class", ""), "name": pending["test"],
                                "code": code, "passed": code == 0})
            pending = {}
    expected = {"bundledLegalDocumentsHaveContentAndReturnToAbout",
                "settingsChildrenReturnToTheSameSettingsPage",
                "repeatedSettingsBackNavigationKeepsHomeUsable"}
    passed = (len(results) == len(expected)
              and {r["name"] for r in results} == expected
              and all(r["passed"] and r["class"] == CLASS for r in results)
              and bool(re.search(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.M))
              and "OK (3 tests)" in output
              and "INSTRUMENTATION_FAILED" not in output)
    return results, passed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-build", action="store_true", help="Use existing Debug and test APKs")
    parser.add_argument("--output", type=Path, help="New output directory (must not exist)")
    args = parser.parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    out = args.output or ROOT / "app/build/reports/device-settings" / run_id
    out.mkdir(parents=True, exist_ok=False)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    adb = str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")) if sdk else shutil.which("adb")
    if not adb:
        raise RuntimeError("Set ANDROID_HOME or put adb on PATH")

    def execute(command, timeout=60, log=None):
        result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding="utf-8", errors="replace", timeout=timeout)
        if log:
            (out / log).write_text(result.stdout, encoding="utf-8")
        if result.returncode:
            raise RuntimeError(f"Command failed; inspect {out / log if log else command[0]}")
        return result.stdout.strip()

    def device(*arguments, **kwargs):
        return execute([adb, "-s", SERIAL, *arguments], **kwargs)

    def install_time():
        info = device("shell", "dumpsys", "package", PACKAGE)
        match = re.search(r"^\s*firstInstallTime=(.+)$", info, re.M)
        return match[1].strip() if match else None

    if device("get-state") != "device" or device("shell", "getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Only the authorized emulator-5554 is supported")
    if not args.no_build:
        wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
        execute([str(ROOT / wrapper), ":app:assembleDebug", ":app:assembleDebugAndroidTest",
                 "--console=plain"], timeout=900, log="build.log")
    metadata = json.loads((ROOT / "app/build/outputs/apk/debug/output-metadata.json").read_text())
    abis = device("shell", "getprop", "ro.product.cpu.abilist").split(",")
    compatible = [(abis.index(f["value"]), element) for element in metadata["elements"]
                  for f in element["filters"] if f["filterType"] == "ABI" and f["value"] in abis]
    if not compatible:
        raise RuntimeError("No matching Debug APK; application has not been changed")
    element = min(compatible, key=lambda pair: pair[0])[1]
    app_apk = ROOT / "app/build/outputs/apk/debug" / element["outputFile"]
    test_apk = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    record = {"serial": SERIAL, "runId": run_id, "revision": execute(["git", "rev-parse", "HEAD"]),
              "trackedChanges": execute(["git", "status", "--porcelain", "--untracked-files=no"]),
              "trackedDiffSha256": hashlib.sha256(execute(["git", "diff", "HEAD"]).encode()).hexdigest(),
              "appApk": app_apk.name, "appSha256": hashlib.sha256(app_apk.read_bytes()).hexdigest(),
              "testSha256": hashlib.sha256(test_apk.read_bytes()).hexdigest(),
              "display": device("shell", "wm", "density"),
              "fontScale": device("shell", "settings", "get", "system", "font_scale"),
              "firstInstallTimeBefore": install_time(),
              "status": "running", "scope": "Production settings navigation; no provider authentication or writes"}
    (out / "result.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    for name, apk in [("app", app_apk), ("test", test_apk)]:
        installed = device("install", "-r", str(apk), timeout=120, log=f"install-{name}.log")
        if "Success" not in installed.splitlines():
            raise RuntimeError(f"Installation did not report Success: {name}")
    # Check actual installed bytes rather than equating build output with the installed app.
    package_path = device("shell", "pm", "path", PACKAGE).splitlines()[0].removeprefix("package:")
    installed_apk = out / "installed-base.apk"
    device("pull", package_path, str(installed_apk), timeout=120)
    record["installedSha256"] = hashlib.sha256(installed_apk.read_bytes()).hexdigest()
    record["firstInstallTimeAfter"] = install_time()
    if record["firstInstallTimeBefore"] and record["firstInstallTimeBefore"] != record["firstInstallTimeAfter"]:
        raise RuntimeError("Unexpected installation-time change; app data retention is unverified")
    if record["installedSha256"] != record["appSha256"]:
        raise RuntimeError("Installed APK differs from the selected build")
    installed_apk.unlink()  # Delete only the freshly pulled local duplicate, never app data.
    output = device("shell", "am", "instrument", "-w", "-r", "-e", "class", CLASS,
                    "-e", "evidenceRunId", run_id,
                    f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner",
                    timeout=300, log="instrumentation.log")
    tests, passed = parse_results(output)
    record.update(tests=tests, status="passed" if passed else "failed")
    pulled = subprocess.run([adb, "-s", SERIAL, "pull",
                             f"/sdcard/Android/data/{PACKAGE}/files/settings-acceptance/{run_id}",
                             str(out / "screenshots")], capture_output=True, timeout=60)
    record["screenshotsCopied"] = pulled.returncode == 0
    suite = ET.Element("testsuite", name=CLASS, tests=str(len(tests)),
                       failures=str(sum(not test["passed"] for test in tests)),
                       errors="0" if passed or any(not t["passed"] for t in tests) else "1", skipped="0")
    if suite.get("errors") == "1":
        ET.SubElement(suite, "error", message="Incomplete or invalid instrumentation run; see instrumentation.log")
    for test in tests:
        case = ET.SubElement(suite, "testcase", name=test["name"], classname=test["class"])
        if not test["passed"]:
            ET.SubElement(case, "failure", message=f"Instrumentation status {test['code']}; see instrumentation.log")
    ET.ElementTree(suite).write(out / "junit.xml", encoding="utf-8", xml_declaration=True)
    # Instrumentation may leave another app in front; return to the production launcher.
    device("shell", "am", "start", "-W", "-n", f"{PACKAGE}/takagi.ru.monica.EtoileActivity")
    record["targetStillInstalled"] = device("shell", "pm", "path", PACKAGE).startswith("package:")
    (out / "result.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(f"{record['status']}: {len(tests)} completed tests; evidence: {out}")
    return 0 if passed and record["screenshotsCopied"] and record["targetStillInstalled"] else 1


if __name__ == "__main__":
    sys.exit(main())
