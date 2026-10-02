"""Package only this module's sources and selected sanitized verification records."""
from pathlib import Path
import hashlib
import re
import zipfile

root = Path(__file__).resolve().parent.parent
version = re.search(r"versionName '([^']+)'", (root / "app/build.gradle").read_text()).group(1)
if not re.fullmatch(r"[0-9A-Za-z.-]+", version):
    raise ValueError("invalid version")
files = set()
for directory in ("app/src", "tests", "tools", ".github"):
    files.update(p for p in (root / directory).rglob("*") if p.is_file()
                 and "__pycache__" not in p.parts and p.suffix not in (".pyc", ".class"))
for name in ("README.md", "START_HERE.txt", "LICENSE", "Build-Windows.cmd", "Build-Linux-Mac.sh",
             "build.gradle", "settings.gradle", "gradle.properties", "app/build.gradle", ".gitignore", ".gitattributes", "build-validation.log"):
    files.add(root / name)
for name in ("ARCHITECTURE.md", "DEVICE_TESTS.md", "TEST_REPORT.md", "VALIDATION-2026-09-30.md", "VALIDATION-2026-10-01.md", "VALIDATION-2026-10-02.md", "VALIDATION-2026-10-02-0.1.48.md", "VALIDATION-2026-10-02-0.1.49.md", "VALIDATION-2026-10-02-0.1.50.md",
             "RECEIVER-RESULT-2026-09-30.md", "qq-fixed-change-diagnostics-0.1.23.txt",
             "qq-account-manual-diagnostics-0.1.44.txt", "qq-account-fixed-second-0.1.44.txt",
             "core-tests-0.1.45.txt", "device-safety-0.1.45.txt", "embedded-update-0.1.45.txt",
             "apk-signature-0.1.45.txt", "qq-alignment-0.1.45.txt", "library-ui-0.1.45.txt",
             "qq-account-manual-0.1.45.txt", "qq-account-low-start-0.1.45.txt", "qq-account-low-end-0.1.45.txt",
             "qq-account-background-0.1.45.txt", "qq-account-high-steady-start-0.1.45.txt",
             "qq-account-high-steady-cycles-0.1.45.txt", "qq-account-stop-0.1.45.txt",
             "qq-account-restart-0.1.45.txt"):
    files.add(root / "docs" / name)
# The current release must include its own report, not inherit historical test claims.
reports = [p for p in (root / "docs").glob("VALIDATION-*.md")
           if p.read_text(encoding="utf-8").splitlines()[0] == "# " + version + " 验证报告"]
if len(reports) != 1:
    raise ValueError("expected exactly one validation report for " + version)
files.add(reports[0])
# Public versioned records belong in docs; private device files remain excluded.
record_name = re.compile(r"[a-z0-9-]+-" + re.escape(version) + r"\.txt")
records = [p for p in (root / "docs").glob("*.txt") if record_name.fullmatch(p.name)]
files.update(records)
# Preserve prior public versioned evidence, including later sanitized failure diagnoses.
# Historical 0.1.50 records include current-pre-fix, qq-emoticon-schema and later
# reply-failure diagnoses. Unversioned and older raw logs stay out.
historical_records = []
for historical_version in ("0.1.46", "0.1.47", "0.1.48", "0.1.49", "0.1.50"):
    historical_record_name = re.compile(r"[a-z0-9-]+-" + re.escape(historical_version) + r"\.txt")
    historical_records.extend(p for p in (root / "docs").glob("*.txt")
                              if historical_record_name.fullmatch(p.name))
files.update(historical_records)
paths = sorted(files, key=lambda p: p.relative_to(root).as_posix())
manifest = "".join(hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.relative_to(root).as_posix() + "\n" for p in paths)
(root / "SOURCE_SHA256SUMS.txt").write_text(manifest, encoding="utf-8", newline="\n")
paths.append(root / "SOURCE_SHA256SUMS.txt")
out = root / "out"
out.mkdir(exist_ok=True)
archive = out / ("LingRandomBubble-" + version + "-source.zip")
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
    for p in paths:
        name = p.relative_to(root).as_posix()
        if any(part in name.split("/") for part in ("device-backup", ".tools", ".git", "__pycache__")) or p.suffix in (".apk", ".dex", ".jks", ".keystore"):
            raise ValueError("private file rejected: " + name)
        z.write(p, "LingRandomBubble/" + name)
with zipfile.ZipFile(archive) as z:
    if z.testzip() is not None:
        raise ValueError("archive corrupt")
    for line in manifest.splitlines():
        digest, name = line.split("  ", 1)
        if hashlib.sha256(z.read("LingRandomBubble/" + name)).hexdigest() != digest:
            raise ValueError("archive hash mismatch")
artifacts = (out / ("LingRandomBubble-" + version + "-debug.apk"),
             out / ("LingRandomBubble-" + version + "-debug-androidTest.apk"), archive)
(out / "SHA256SUMS.txt").write_text("".join(hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name + "\n" for p in artifacts), encoding="ascii", newline="\n")
print("PASS source archive: " + str(len(paths)) + " files; all hashes verified; private APKs excluded")
print("Current validation: " + reports[0].name + "; " + str(len(records)) + " release records")
print("Historical 0.1.46 / 0.1.47 / 0.1.48 / 0.1.49 / 0.1.50 records: " + str(len(historical_records)))
print("Created: " + str(archive))
