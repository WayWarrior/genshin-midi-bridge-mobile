# AOSP touch-reuse source integration check

Runs unmodified Android 12 GestureDescription and MotionEventInjector Java sources with simplified platform stubs. The production ReusableStroke and LiveGestureBatch are compiled against the actual Android API35 android.jar, then executed on this JVM source fixture. No hidden SDK method is called from the app classes. Tests exercise the real production planner, slot owner and event receiver.

Run ANDROID_SDK_ROOT=... ECJ_JAR=... bash scripts/check-aosp-reuse.sh (or use a JDK without ECJ_JAR). This is not Android ART, Binder/InputDispatcher, native MotionEvent validation, a device, game or latency test. Stubs capture event properties instead of invoking Android JNI. It cannot establish HarmonyOS compatibility; the APK includes an on-device test for that purpose.

AOSP sources retain their Apache 2.0 license headers. Source tag: android-12.0.0_r1. MotionEventInjector SHA256: 0303d4aff79dd9e4971dfc4bc7968f0b9b083311b7f83dbf06eecd1aa1567834. GestureDescription SHA256: 95cb50646d43b9778d21b111fcbe992f3c36d10c3842c3d8fc0854a56e5ad097. Simplified stubs are research scaffolding.
