"""Verify built module identity and fail-closed embedded migration without an Android device.
Requires JAVA_HOME, ANDROID_HOME, Python 3, built APKs and .tools/patch-deps jars.
"""
from pathlib import Path
import hashlib
import os
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SDK = Path(os.environ["ANDROID_HOME"])
JAVA = Path(os.environ["JAVA_HOME"]) / "bin"
SUFFIX = ".exe" if os.name == "nt" else ""
ANALYZER = SDK / "cmdline-tools/latest/bin" / ("apkanalyzer.bat" if os.name == "nt" else "apkanalyzer")
SIGNER = SDK / "build-tools/35.0.0" / ("apksigner.bat" if os.name == "nt" else "apksigner")
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
A = "{http://schemas.android.com/apk/res/android}"
expected = "io.github.ling_la.randombubble"
legacy = "io.github.ling.randombubble"
checks = []

def run(*args):
    return subprocess.check_output([str(x) for x in args], cwd=ROOT, text=True, encoding="utf-8", errors="strict")

def check(name, condition):
    if not condition:
        raise AssertionError(name)
    checks.append(name)
    print("PASS " + name, flush=True)

build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
version = re.search(r"versionName '([^']+)'", build).group(1)
code = re.search(r"versionCode (\d+)", build).group(1)
module = ET.fromstring(run(ANALYZER, "manifest", "print", APK))
test = ET.fromstring(run(ANALYZER, "manifest", "print", TEST_APK))
check("built APK has maintainer-owned application ID", module.get("package") == expected)
check("built version matches source", module.get(A + "versionCode") == code and module.get(A + "versionName") == version)
provider = module.find("application/provider")
check("provider has only new authority", provider is not None and provider.get(A + "authorities") == expected + ".config")
metadata = {x.get(A + "name"): x for x in module.findall("application/meta-data")}
check("Xposed module metadata retained", metadata["xposedmodule"].get(A + "value") == "true" and metadata["xposedminversion"].get(A + "value") == "82" and "xposedscope" in metadata)
check("no new application permissions", not module.findall("uses-permission"))
launcher = next(x for x in module.findall("application/activity") if x.find("intent-filter") is not None)
check("new application resolves existing settings class", launcher.get(A + "name") == legacy + ".ui.MainActivity")
with zipfile.ZipFile(APK) as z:
    entry = z.read("assets/xposed_init").decode().strip()
check("Xposed entry remains declared", entry == legacy + ".hook.HookEntry")
# Disassemble actual DEX definitions, not merely Java sources or manifest strings.
for class_name in (entry, launcher.get(A + "name")):
    text = run(ANALYZER, "dex", "code", "--class", class_name, APK)
    check("DEX class exists: " + class_name, "L" + class_name.replace(".", "/") + ";" in text)
compiled_provider = run(ANALYZER, "dex", "code", "--class", provider.get(A + "name"), APK)
check("compiled provider URI uses new identity", '"content://' + expected + '.config"' in compiled_provider and '"' + legacy + '.config"' not in compiled_provider)
instrumentation = test.find("instrumentation")
check("test APK targets new application", test.get("package") == expected + ".test" and instrumentation.get(A + "targetPackage") == expected)
check("test runner class resolves in DEX", "L" + instrumentation.get(A + "name").replace(".", "/") + ";" in run(ANALYZER, "dex", "code", "--class", instrumentation.get(A + "name"), TEST_APK))
for apk in (APK, TEST_APK):
    run(SIGNER, "verify", str(apk))
check("module and test APK signatures verify", True)

# Private synthetic ZIPs: these cases must fail before loading a manager or writing an output.
with tempfile.TemporaryDirectory(prefix="lingbubble-identity-") as temp:
    folder = Path(temp)
    deps = ROOT / ".tools/patch-deps/*"
    run(JAVA / ("javac" + SUFFIX), "-encoding", "UTF-8", "-cp", deps, "-d", folder, ROOT / "tools/UpdateEmbeddedModule.java")
    for label, identities in (("legacy", [legacy]), ("dual", [legacy, expected]), ("missing", [])):
        source, output = folder / (label + ".apk"), folder / (label + "-output.apk")
        with zipfile.ZipFile(source, "w") as z:
            for identity in identities:
                z.writestr("assets/lspatch/modules/" + identity + ".apk", b"synthetic fixture")
        result = subprocess.run([str(JAVA / ("java" + SUFFIX)), "-cp", str(folder) + os.pathsep + str(deps), "UpdateEmbeddedModule", str(source), str(output), str(APK), str(folder / "absent-manager.apk"), "unused"], capture_output=True, text=True, encoding="utf-8")
        marker = "Legacy module identity found" if identities else "Expected new-package embedded module absent"
        check("updater refuses " + label + " identity before output/signing access", result.returncode != 0 and marker in result.stderr and not output.exists())
print("RESULT: " + str(len(checks)) + " APK/embedded identity checks passed. No Android device or QQ delivery tested.")
print("APK SHA256 " + hashlib.sha256(APK.read_bytes()).hexdigest())
