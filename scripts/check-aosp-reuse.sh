#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
APP_REUSE_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
APP_REUSE_ANDROID_JAR="$APP_REUSE_SDK/platforms/android-35/android.jar"
[[ -f "$APP_REUSE_ANDROID_JAR" ]] || { echo 'Android API35 android.jar is required.' >&2; exit 1; }
APP_REUSE_DIR="$PWD/app/build/aosp-checks"
rm -rf "$APP_REUSE_DIR"
mkdir -p "$APP_REUSE_DIR/platform" "$APP_REUSE_DIR/production" "$APP_REUSE_DIR/test"
app_reuse_compile(){
 if [[ -n "${ECJ_JAR:-}" ]]; then java -jar "$ECJ_JAR" -8 -proc:none "$@";
 else java com.sun.tools.javac.Main -source 8 -target 8 "$@"; fi
}
mapfile -t APP_REUSE_FIXTURES < <(rg --files tests/aosp-reuse/src -g '*.java')
APP_REUSE_PURE=(app/src/main/java/com/junliang/midibridge/{LiveTouchState,LiveTouchPlan,LiveTouchSlots,TouchReuseScenario,TouchProbeTrace,HoldMotion,GestureDispatchGuard}.java)
app_reuse_compile -d "$APP_REUSE_DIR/platform" "${APP_REUSE_FIXTURES[@]}" "${APP_REUSE_PURE[@]}"
# Compile production stroke classes with the real public SDK, not the fixture's hidden methods.
app_reuse_compile -classpath "$APP_REUSE_ANDROID_JAR:$APP_REUSE_DIR/platform" -d "$APP_REUSE_DIR/production" app/src/main/java/com/junliang/midibridge/{ReusableStroke,LiveGestureBatch}.java
app_reuse_compile -classpath "$APP_REUSE_DIR/platform:$APP_REUSE_DIR/production" -d "$APP_REUSE_DIR/test" tests/aosp-reuse/AospTouchReuseCheck.java
java -cp "$APP_REUSE_DIR/test:$APP_REUSE_DIR/production:$APP_REUSE_DIR/platform" AospTouchReuseCheck
