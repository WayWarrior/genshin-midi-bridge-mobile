package com.junliang.midibridge;
import java.util.Locale;

/** File-only musical options. Both output routes use the score built from this snapshot. */
public final class MidiFileOptions {
 public static final float DEFAULT_BPM=120,MIN_BPM=20,MAX_BPM=400;
 private static final String[] KEYS={
  "C 大调 / A 小调","C♯ / D♭ 大调 / A♯ / B♭ 小调","D 大调 / B 小调",
  "D♯ / E♭ 大调 / C 小调","E 大调 / C♯ 小调","F 大调 / D 小调",
  "F♯ / G♭ 大调 / D♯ / E♭ 小调","G 大调 / E 小调","G♯ / A♭ 大调 / F 小调",
  "A 大调 / F♯ 小调","A♯ / B♭ 大调 / G 小调","B 大调 / G♯ 小调"
 };
 public final int speedPercent,sourceMajor;
 public final boolean overrideTempo,convertToC;
 public final double targetBpm;
 public MidiFileOptions(int speed,boolean override,double bpm,boolean convert,int key){
  if(!override&&(speed<25||speed>200))throw new IllegalArgumentException("文件速度百分比请填25–200的整数");
  if(override)validateBpm(bpm);
  if(convert&&(key<0||key>=KEYS.length))throw new IllegalArgumentException("请选择 MIDI 原调");
  speedPercent=speed;overrideTempo=override;targetBpm=bpm;convertToC=convert;sourceMajor=key;
 }
 public static void validateBpm(double bpm){
  if(!Double.isFinite(bpm)||bpm<MIN_BPM||bpm>MAX_BPM)throw new IllegalArgumentException("目标速度请填20–400 BPM，可用小数");
 }
 public static String[] keyChoices(){return KEYS.clone();}
 /** Choose the nearest C, preserving the register as far as octave folding allows. */
 public static int shiftToC(int major){
  if(major<0||major>=KEYS.length)throw new IllegalArgumentException("MIDI 原调无效");
  return major<=6?-major:12-major;
 }
 public int effectiveTranspose(int shared){return shared+(convertToC?shiftToC(sourceMajor):0);}
 public MidiScore build(SmfFile file,boolean[] tracks,int sharedTranspose,int accidental,boolean fold){
  return MidiScore.build(file,tracks,effectiveTranspose(sharedTranspose),accidental,fold,overrideTempo?100:speedPercent,overrideTempo?targetBpm:0);
 }
 public String summary(){
  String timing=overrideTempo?"固定速度 "+String.format(Locale.ROOT,"%.2f",targetBpm).replaceAll("0+$","").replaceAll("\\.$","")+" BPM":"原速度 × "+speedPercent+"%";
  return timing+" · "+(convertToC?KEYS[sourceMajor]+" → C 调":"原调按 C 调处理");
 }
}
