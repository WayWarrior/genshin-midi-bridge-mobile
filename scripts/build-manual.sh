#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
APP_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$APP_SDK_ROOT" ]]; then echo 'Set ANDROID_SDK_ROOT to SDK with Android 35 and build-tools 35.0.0.' >&2; exit 1; fi
APP_BUILD_TOOLS="$APP_SDK_ROOT/build-tools/35.0.0"
APP_ANDROID_JAR="$APP_SDK_ROOT/platforms/android-35/android.jar"
APP_BUILD_DIR="$PWD/app/build/manual"
rm -rf "$APP_BUILD_DIR/classes" "$APP_BUILD_DIR/gen"
mkdir -p "$APP_BUILD_DIR/classes"
rm -f "$APP_BUILD_DIR/unsigned.apk" "$APP_BUILD_DIR/classes.jar"
"$APP_BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o "$APP_BUILD_DIR/res.zip"
python3 - "$APP_BUILD_DIR" <<'PYCODE'
import sys
from pathlib import Path
src=Path('app/src/main/AndroidManifest.xml').read_text()
Path(sys.argv[1], 'AndroidManifest.xml').write_text(src.replace('<manifest xmlns:', '<manifest package="com.junliang.midibridge" xmlns:'))
PYCODE
"$APP_BUILD_TOOLS/aapt2" link -A app/src/main/assets -o "$APP_BUILD_DIR/unsigned.apk" -I "$APP_ANDROID_JAR" --manifest "$APP_BUILD_DIR/AndroidManifest.xml" --java "$APP_BUILD_DIR/gen" --min-sdk-version 26 --target-sdk-version 32 --version-code 18 --version-name 0.18.0 --debug-mode "$APP_BUILD_DIR/res.zip"
APP_SOURCES=(app/src/main/java/com/junliang/midibridge/*.java "$APP_BUILD_DIR/gen/com/junliang/midibridge/R.java")
if [[ -n "${ECJ_JAR:-}" ]]; then
 java -jar "$ECJ_JAR" -8 -proc:none -classpath "$APP_ANDROID_JAR" -d "$APP_BUILD_DIR/classes" "${APP_SOURCES[@]}"
else
 java com.sun.tools.javac.Main -source 8 -target 8 -classpath "$APP_ANDROID_JAR" -d "$APP_BUILD_DIR/classes" "${APP_SOURCES[@]}"
fi
(cd "$APP_BUILD_DIR/classes" && zip -qr "$APP_BUILD_DIR/classes.jar" .)
"$APP_BUILD_TOOLS/d8" --lib "$APP_ANDROID_JAR" --min-api 26 --output "$APP_BUILD_DIR" "$APP_BUILD_DIR/classes.jar"
python3 - "$APP_BUILD_DIR" <<'PYCODE'
import sys,zipfile
from pathlib import Path
p=Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
 apk.write(p/'classes.dex','classes.dex')
PYCODE
"$APP_BUILD_TOOLS/zipalign" -f 4 "$APP_BUILD_DIR/unsigned.apk" "$APP_BUILD_DIR/aligned.apk"
APP_KEYSTORE="${APP_SIGNING_KEYSTORE:-$PWD/signing/debug.keystore}"
if [[ ! -f "$APP_KEYSTORE" ]]; then
 keytool -genkeypair -keystore "$APP_KEYSTORE" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
fi
APP_SIGN_TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$APP_SIGN_TEMP_DIR"' EXIT
# Stage signing outside the workspace, then publish ordinary file writes rather than mmap writes.
"$APP_BUILD_TOOLS/apksigner" sign --ks "$APP_KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$APP_SIGN_TEMP_DIR/signed.apk" "$APP_BUILD_DIR/aligned.apk"
"$APP_BUILD_TOOLS/apksigner" verify "$APP_SIGN_TEMP_DIR/signed.apk"
python3 - "$APP_SIGN_TEMP_DIR/signed.apk" "$APP_BUILD_DIR/GenshinMidiBridge.apk" <<'PYCODE'
import sys,zipfile
from pathlib import Path
source,dest=map(Path,sys.argv[1:])
with zipfile.ZipFile(source) as apk:
 if apk.testzip() is not None: raise SystemExit('Signed APK is incomplete')
dest.write_bytes(source.read_bytes())
PYCODE
"$APP_BUILD_TOOLS/apksigner" verify --verbose "$APP_BUILD_DIR/GenshinMidiBridge.apk"
echo "Built: $APP_BUILD_DIR/GenshinMidiBridge.apk"
