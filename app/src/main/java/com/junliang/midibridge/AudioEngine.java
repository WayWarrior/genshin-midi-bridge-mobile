package com.junliang.midibridge;
import android.media.*;
import android.os.*;
import java.util.Locale;

public final class AudioEngine {
 private final AudioCommandQueue queue=new AudioCommandQueue(512);
 private volatile WavData[] samples=new WavData[128];
 private volatile AudioMixer mixer;
 private volatile FileAudioSequence fileSequence;
 private volatile boolean running,oneShot=true,muted;
 private volatile float volume=0.65f;
 private volatile int profile;
 private volatile long resetSerial;
 private Thread thread;private volatile AudioTrack track;
 private volatile int bufferFrames,underrunCount,expansionCount,appliedProfile;
 private int rate=48000,burst=256;
 public volatile String status="未启动";
 public void samples(WavData[] bank){samples=bank;AudioMixer m=mixer;if(m!=null)m.samples(bank);}
 public void volume(float value){volume=value;AudioMixer m=mixer;if(m!=null)m.volume(value);}
 public void muted(boolean enabled){muted=enabled;AudioMixer m=mixer;if(m!=null)m.muted(enabled);}
 public boolean isMuted(){return muted;}
 public void oneShot(boolean enabled){oneShot=enabled;AudioMixer m=mixer;if(m!=null)m.oneShot(enabled);}
 public void profile(int selected){profile=selected>=0 && selected<=2?selected:0;}
 public synchronized void resetBuffer(){resetSerial++;}
 public int sampleRate(){return rate;}
 public boolean isRunning(){return running;}
 public void event(int kind,int id,int value){if(kind==3)stopFileAudio();if(running)queue.offer(kind,id,value);}
 FileAudioSequence playFile(MidiScore score,long origin,long resume){
  stopFileAudio();FileAudioSequence sequence=new FileAudioSequence(score,rate,origin+resume,resume);fileSequence=sequence;
  AudioMixer renderer=mixer;if(renderer!=null)renderer.fileSequence(sequence);return sequence;
 }
 void stopFileAudio(){FileAudioSequence previous=fileSequence;if(previous!=null)previous.cancel();fileSequence=null;AudioMixer renderer=mixer;if(renderer!=null)renderer.fileSequence(null);}

 public synchronized void start(int preferredRate,int preferredBurst) {
  if(running)return;if(track!=null)stop();
  AudioBufferPlan plan=new AudioBufferPlan(preferredRate,preferredBurst,profile);rate=plan.rate;burst=plan.burst;
  int minimum=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
  if(minimum<=0)throw new IllegalStateException("设备不支持原生采样率 PCM16 输出");
  AudioTrack output=new AudioTrack.Builder()
   .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
   .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
   .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(plan.capacityFrames(minimum)*2)
   .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
  if(output.getState()!=AudioTrack.STATE_INITIALIZED){output.release();throw new IllegalStateException("AudioTrack 初始化失败");}
  output.setBufferSizeInFrames(Math.min(plan.requestedFrames(),output.getBufferCapacityInFrames()));
  if(Build.VERSION.SDK_INT>=31)try{output.setStartThresholdInFrames(Math.min(burst,output.getBufferSizeInFrames()));}catch(RuntimeException ignored){}
  AudioMixer renderer=new AudioMixer(rate,queue);renderer.samples(samples);renderer.volume(volume);renderer.oneShot(oneShot);renderer.muted(muted);queue.clear();
  mixer=renderer;track=output;running=true;
  thread=new Thread(()->render(output,renderer,plan),"MidiAudio");thread.start();
 }

 private void render(AudioTrack output,AudioMixer renderer,AudioBufferPlan initialPlan) {
  android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
  AudioBufferPlan plan=initialPlan;
  float[] block=new float[plan.blockFrames()];short[] pcm=new short[block.length];
  long appliedReset=resetSerial,started=System.nanoTime(),nextCheck=started+250000000L;
  int lastUnderruns=output.getUnderrunCount(),expansions=0;
  try {
   // Prime only the effective buffer, without blocking; keep output running between notes.
   short[] silence=new short[output.getBufferSizeInFrames()];
   output.write(silence,0,silence.length,AudioTrack.WRITE_NON_BLOCKING);
   output.play();status="运行中";updateStats(output,plan,expansions);
   while(running) {
    renderer.mix(block,SystemClock.uptimeMillis());
    for(int k=0;k<block.length;k++)pcm[k]=(short)Math.round(block[k]*32767);
    int position=0;
    while(running && position<pcm.length) {
     int count=output.write(pcm,position,pcm.length-position,AudioTrack.WRITE_BLOCKING);
     if(count<0)throw new IllegalStateException("音频写入错误 "+count);
     if(count==0)Thread.yield();else position+=count;
    }
    long now=System.nanoTime();
    if(now>=nextCheck) {
     int underruns=output.getUnderrunCount();
     if(profile!=plan.profile || appliedReset!=resetSerial) {
      plan=new AudioBufferPlan(rate,burst,profile);appliedReset=resetSerial;
      output.setBufferSizeInFrames(Math.min(plan.requestedFrames(),output.getBufferCapacityInFrames()));expansions=0;started=now;
     } else if(now-started>=500000000L && underruns>lastUnderruns) {
      int current=output.getBufferSizeInFrames(),target=plan.grow(current,output.getBufferCapacityInFrames());
      if(target>current && output.setBufferSizeInFrames(target)>current)expansions++;
     }
     lastUnderruns=underruns;updateStats(output,plan,expansions);nextCheck=now+250000000L;
    }
   }
  }catch(Exception error){if(running)status="音源停止："+error.getMessage();running=false;}
 }

 private void updateStats(AudioTrack output,AudioBufferPlan plan,int expansions) {
  bufferFrames=output.getBufferSizeInFrames();underrunCount=output.getUnderrunCount();expansionCount=expansions;appliedProfile=plan.profile;
 }
 /** Format diagnostics on the UI thread, not the real-time rendering thread. */
 public String diagnostics() {
  if(!running)return status;
  AudioTrack output=track;AudioMixer renderer=mixer;boolean fast=false;AudioDeviceInfo route=null;
  if(output!=null)try{fast=output.getPerformanceMode()==AudioTrack.PERFORMANCE_MODE_LOW_LATENCY;route=output.getRoutedDevice();}catch(RuntimeException ignored){}
  String destination="默认输出";
  if(route!=null) {
   int type=route.getType();
   if(type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)destination="扬声器";
   else if(type==AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type==AudioDeviceInfo.TYPE_WIRED_HEADSET)destination="有线耳机";
   else if(type==AudioDeviceInfo.TYPE_USB_DEVICE || type==AudioDeviceInfo.TYPE_USB_HEADSET)destination="USB 音频";
   else if(type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)destination="蓝牙音频";
  }
  return (muted?"本地已静音":"本地未静音")+" / "+rate+" Hz / PCM16 / "+destination+" / "+(fast?"系统低延迟模式":"系统普通模式")
   +"\n"+AudioBufferPlan.label(appliedProfile)+"：缓冲 "+bufferFrames+" 帧 ≈ "+String.format(Locale.ROOT,"%.1f",bufferFrames*1000.0/rate)+" ms（缓冲时长，非实测延迟）"
   +"\n缓冲不足次数："+underrunCount+" / 自动增大："+expansionCount+" / 发声音符："+(renderer==null?0:renderer.activeVoices)+" / 消息溢出："+queue.overflowCount();
 }

 public synchronized void stop() {
  stopFileAudio();
  running=false;
  if(track!=null)try{track.pause();track.flush();}catch(Exception ignored){}
  if(thread!=null)try{thread.join(1000);}catch(InterruptedException error){Thread.currentThread().interrupt();}
  if(track!=null){track.release();track=null;}thread=null;mixer=null;queue.clear();status="已停止";
 }
}
