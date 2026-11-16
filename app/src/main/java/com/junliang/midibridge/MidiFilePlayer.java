package com.junliang.midibridge;
import android.content.SharedPreferences;
import android.os.*;
import android.widget.Toast;
import java.io.*;
import java.util.*;

/** Main-thread transport; file note edges are rendered by the audio thread. */
public final class MidiFilePlayer {
 private final BridgeService bridge;private final Handler main=new Handler(Looper.getMainLooper());
 public volatile SmfFile song;public volatile String status="尚未导入 MIDI",name="";
 public volatile boolean running,paused,gameMode,preparing;
 public volatile long positionMs,durationMs,maxLateMs;
 private MidiScore score;private FileAudioSequence audioSequence;private long origin,startAt,countedNotes;private int generation,loadGeneration;
 private long checkedGame;private String mappingSummary="";
 private boolean loading,pendingPreview;
 private final Runnable tick=this::tick;
 MidiFilePlayer(BridgeService b){bridge=b;load();}
 private void toast(String s){Toast.makeText(bridge,s,Toast.LENGTH_LONG).show();}
 public void load(){main.post(()->{
  stopNow(false);song=null;final int token=++loadGeneration;File f=new File(bridge.getFilesDir(),"selected.mid");if(!f.exists()){loading=false;song=null;status="尚未导入 MIDI";return;}loading=true;
  status="正在解析 MIDI…";name=Config.prefs(bridge).getString("midi_name","selected.mid");
  new Thread(()->{try(InputStream in=new FileInputStream(f)){SmfFile parsed=SmfFile.read(in);main.post(()->{if(token!=loadGeneration||BridgeService.instance!=bridge)return;loading=false;song=parsed;durationMs=parsed.durationUs/1000;status=info();if(pendingPreview){pendingPreview=false;start(false);}});}catch(Exception e){main.post(()->{if(token==loadGeneration){loading=pendingPreview=false;song=null;status="MIDI 读取失败："+e.getMessage();}});}},"MidiFileLoader").start();
 });}
 public String info(){SmfFile f=song;if(f==null)return status;return name+" · "+f.trackNames.length+"轨 / "+f.notes.size()+"音 / "+time(f.durationUs/1000)+(f.unclosedNotes>0?"；缺失松键 "+f.unclosedNotes+"，已在曲尾释放":"");}
 public static String time(long ms){return String.format(Locale.ROOT,"%02d:%02d",Math.max(0,ms)/60000,(Math.max(0,ms)/1000)%60);}
 public String progress(){TouchService t=TouchService.instance;return status+(running||paused?"\n"+time(positionMs)+" / "+time(durationMs)+" · 音源起播迟到 "+maxLateMs+"ms"+(gameMode&&t!=null?" / 触控最大迟到 "+t.maxFileLateMs+"ms":""):"");}
 public void start(boolean game){main.post(()->{
  if(preparing)return;if(song==null){if(loading&&!game){pendingPreview=true;toast("解析完成后开始试听");}else toast(loading?"正在解析 MIDI，请稍候":"先导入 MIDI 文件");return;}
  TouchService t=TouchService.instance;if(game&&(t==null||!t.fileReady(true)))return;
  if(running){toast("正在播放，请先暂停或停止");return;}
  if(!game&&t!=null)t.stopRealtime();
  if(paused&&score!=null&&gameMode==game){begin(game,positionMs);return;}
  stopNow(false);final int token=++generation;preparing=true;status="正在准备演奏…";
  SmfFile source=song;SharedPreferences p=Config.prefs(bridge);boolean[] tracks=selectedTracks(source,p.getString("midi_tracks",""));int shift=p.getInt("transpose",0),accidental=p.getInt("midi_accidental",0),speed=p.getInt("midi_speed",100);boolean fold=p.getBoolean("midi_fold",true);
  boolean override=p.getBoolean("midi_tempo_override",false),convert=p.getBoolean("midi_convert_c",false);double bpm=p.getFloat("midi_bpm",MidiFileOptions.DEFAULT_BPM);int sourceKey=p.getInt("midi_source_key",0);
  new Thread(()->{try{MidiFileOptions options=new MidiFileOptions(speed,override,bpm,convert,sourceKey);MidiScore built=options.build(source,tracks,shift,accidental,fold);main.post(()->{
    if(token!=generation||BridgeService.instance!=bridge)return;preparing=false;if(built.holds.isEmpty()){status="所选音轨没有可映射音符";toast(status);return;}score=built;mappingSummary=options.summary()+(shift==0?"":" · 共同移调 "+(shift>0?"+":"")+shift+"半音")+"\n"+built.summary();durationMs=built.durationMs;begin(game,0);
   });}catch(Exception e){main.post(()->{if(token==generation){preparing=false;status="准备失败："+e.getMessage();toast(status);}});}},"MidiScoreBuilder").start();
 });}
 private void begin(boolean game,long resume) {
  if(bridge.samplesLoading){preparing=true;status="等待音色加载完成…";final int token=generation;main.postDelayed(()->{if(token==generation&&BridgeService.instance==bridge)begin(game,resume);},50);return;}
  preparing=false;if(!bridge.audio.isRunning()){status="本地音源未运行，请重新启动音源";toast(status);return;}
  TouchService t=TouchService.instance;
  if(game){if(t==null||!t.fileReady(true))return;int voices=MidiScore.maxPolyphony(score.taps);if(voices>t.filePointerLimit()){status="同时 "+voices+" 键超过兼容上限 "+t.filePointerLimit()+"，请减少音轨";toast(status);return;}}
  if(!game)bridge.setLocalMuted(false);
  bridge.silenceAudio();bridge.audio.oneShot(Config.prefs(bridge).getBoolean("midi_natural",false));
  gameMode=game;paused=false;running=true;positionMs=resume;maxLateMs=checkedGame=countedNotes=0;startAt=SystemClock.uptimeMillis()+(game?3000:200);origin=startAt-resume;
  audioSequence=bridge.audio.playFile(score,origin,resume);if(game)t.startFile(score,origin,resume);
  main.removeCallbacks(tick);main.post(tick);
 }
 private void tick() {
  if(!running)return;long now=SystemClock.uptimeMillis();
  if(gameMode&&now-checkedGame>=250){checkedGame=now;TouchService t=TouchService.instance;if(t==null||!t.fileSessionValid()){pauseNow("离开原神、无障碍关闭或屏幕变化，已暂停");return;}}
  FileAudioSequence sequence=audioSequence;if(sequence==null)return;
  if(!bridge.audio.isRunning()){pauseNow("音源已停止，请重新启动");return;}
  if(now<startAt){status=(gameMode?"游戏演奏":"本地试听")+" · 倒计时 "+Math.max(1,(startAt-now+999)/1000)+"秒";main.postDelayed(tick,Math.min(100,startAt-now));return;}
  long ons=sequence.noteOns;bridge.noteCount+=Math.max(0,ons-countedNotes);countedNotes=ons;maxLateMs=sequence.startLateMs;
  positionMs=sequence.positionMs;status=(gameMode?"游戏自动演奏":"本地试听")+" · "+name+"\n"+mappingSummary;
  if(sequence.finished){TouchService touch=TouchService.instance;if(gameMode&&touch!=null&&touch.filePlaying()){main.postDelayed(tick,100);return;}stopNow(false);positionMs=durationMs;status="播放完毕 · "+name;return;}
  main.postDelayed(tick,100);
 }
 public void pause(){main.post(()->pauseNow("已暂停"));}
 void pauseNow(String reason){if(preparing){stopNow(false);status="准备已取消 · "+name;return;}if(!running){pendingPreview=false;return;}FileAudioSequence sequence=audioSequence;if(sequence!=null){sequence.cancel();positionMs=sequence.positionMs;}running=false;paused=true;main.removeCallbacks(tick);bridge.silenceAudio();audioSequence=null;bridge.audio.oneShot(Config.prefs(bridge).getBoolean("one_shot",true));TouchService t=TouchService.instance;if(t!=null)t.stopFile();status=reason+" · "+name;}
 public void stop(){main.post(()->stopNow(true));}
 void stopNow(boolean update){generation++;preparing=pendingPreview=false;running=paused=false;main.removeCallbacks(tick);bridge.silenceAudio();audioSequence=null;bridge.audio.oneShot(Config.prefs(bridge).getBoolean("one_shot",true));TouchService t=TouchService.instance;if(t!=null)t.stopFile();score=null;positionMs=0;if(update)status=song==null?"尚未导入 MIDI":"已停止 · "+name;}
 public void close(){loadGeneration++;stopNow(false);}
 public static boolean[] selectedTracks(SmfFile song,String encoded){boolean[] tracks=new boolean[song.trackNames.length];if(encoded.isEmpty())Arrays.fill(tracks,true);else for(String s:encoded.split(","))try{int i=Integer.parseInt(s);if(i>=0&&i<tracks.length)tracks[i]=true;}catch(NumberFormatException ignored){}return tracks;}
}
