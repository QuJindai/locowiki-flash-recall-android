from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
errors: list[str] = []

if "android.permission.RECORD_AUDIO" not in manifest:
    errors.append("Manifest must request RECORD_AUDIO")
if "android.permission.INTERNET" in manifest:
    errors.append("Manifest must not request INTERNET")
if 'android:usesCleartextTraffic="false"' not in manifest:
    errors.append("Cleartext traffic must be disabled")
if 'android:allowBackup="false"' not in manifest:
    errors.append("Android backup must be disabled")

allowed_schema = "http://schemas.android.com/apk/res/android"
network_symbols = re.compile(r"\b(OkHttpClient|Retrofit|HttpURLConnection|Volley|ktor\.client|WebSocket)\b")
for path in (ROOT / "app/src/main").rglob("*"):
    if not path.is_file() or path.suffix.lower() not in {".kt", ".java", ".xml", ".json", ".kts"}:
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    urls = re.findall(r"https?://[^\s\"'<>]+", text)
    for url in urls:
        if url != allowed_schema:
            errors.append(f"URL found in {path.relative_to(ROOT)}: {url}")
    if network_symbols.search(text):
        errors.append(f"Network client symbol found in {path.relative_to(ROOT)}")

if errors:
    print("PRIVACY_VERIFY_FAIL")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("PRIVACY_VERIFY_PASS")
