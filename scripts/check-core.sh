#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
APP_TEST_DIR="$PWD/app/build/core-checks"
mkdir -p "$APP_TEST_DIR"
APP_SOURCES=(app/src/main/java/com/junliang/midibridge/{MidiParser,NoteMap,WavData,ForegroundResolver,GridCalibration,AudioCommandQueue,AudioBufferPlan,AudioMixer,SmfFile,MidiScore,MidiFileOptions,FileAudioSequence,FileTapPlan,GestureTimeline,HoldMotion,GestureDispatchGuard,LiveTouchState,LiveTouchPlan,LivePointerBudget,LiveTouchSlots,TouchReuseScenario,TouchProbeTrace,OverlayPlacement,RecordedGesture,TouchRecoveryState,SparkCycle}.java app/src/test/java/com/junliang/midibridge/{CoreChecks,ForegroundChecks,GridChecks,SampleChecks,AudioChecks,MidiFileChecks,FileOptionsChecks,FileTimingChecks,TouchPlaybackChecks,LiveTouchChecks,LivePointerChecks,TouchReuseChecks,OverlayChecks,RecoveryChecks,SparkCycleChecks}.java)
if [[ -n "${ECJ_JAR:-}" ]]; then
 java -jar "$ECJ_JAR" -8 -proc:none -d "$APP_TEST_DIR" "${APP_SOURCES[@]}"
else
 java com.sun.tools.javac.Main -source 8 -target 8 -d "$APP_TEST_DIR" "${APP_SOURCES[@]}"
fi
java -cp "$APP_TEST_DIR" com.junliang.midibridge.CoreChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.ForegroundChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.GridChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.SampleChecks "$PWD/app/src/main/assets/samples"
java -cp "$APP_TEST_DIR" com.junliang.midibridge.AudioChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.MidiFileChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.FileOptionsChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.FileTimingChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.TouchPlaybackChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.LiveTouchChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.LivePointerChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.TouchReuseChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.OverlayChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.RecoveryChecks
java -cp "$APP_TEST_DIR" com.junliang.midibridge.SparkCycleChecks
