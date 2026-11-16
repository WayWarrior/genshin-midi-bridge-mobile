package com.junliang.midibridge;
import android.app.*;
import android.content.*;
import android.media.*;
import android.media.midi.*;
import android.os.*;
import java.io.*;
import java.util.Arrays;
public final class BridgeService extends Service {
 public static volatile BridgeService instance;
 public volatile String midiStatus="未连接";
 public volatile long noteCount;
 public volatile String sampleStatus="音色加载中";
 public volatile boolean samplesLoading;
 public final AudioEngine audio=new AudioEngine();
 public MidiFilePlayer filePlayer;
 private MidiManager manager;private MidiDevice device;private MidiOutputPort port;
 private final Handler main=new Handler(Looper.getMainLooper());
 private int requestedId=-1,requestedPort=0,generation=0; private volatile int sampleGeneration;
 private final int[] latched=new int[2048];
 private volatile int transpose;private volatile boolean fold;
 private MidiParser parser;
 private final MidiReceiver receiver=new MidiReceiver() {
  @Override public void onSend(byte[] data,int offset,int count,long timestamp) {parser.feed(data,offset,count);}
 };
 private final MidiManager.DeviceCallback callback=new MidiManager.DeviceCallback(){
  @Override public void onDeviceAdded(MidiDeviceInfo info){if(device==null && info.getType()==MidiDeviceInfo.TYPE_USB)connect(requestedId,requestedPort);}
  @Override public void onDeviceRemoved(MidiDeviceInfo info){if(device!=null && device.getInfo().getId()==info.getId()){disconnect();midiStatus="USB设备已拔出；文件演奏可继续";}}
 };
 @Override public void onCreate() {
  super.onCreate();Config.migrateFileTouch(this);instance=this;Arrays.fill(latched,-1);
  parser=new MidiParser(this::message);
  manager=(MidiManager)getSystemService(MIDI_SERVICE);
  if(manager!=null)manager.registerDeviceCallback(callback,main);
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel("bridge","MIDI 后台演奏",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,BridgeService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  startForeground(7,new Notification.Builder(this,"bridge").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("原琴 MIDI 双路桥运行中").setContentText("点此打开；停止按钮关闭音源及映射").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"停止",stop).build()).build());
  refreshConfig();
  AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
  int rate=48000,burst=256;
  try{rate=Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));}catch(Exception ignored){}
  try{burst=Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));}catch(Exception ignored){}
  try{audio.start(rate,burst);}catch(Exception e){midiStatus="音源初始化失败："+e.getMessage();stopSelf();}
  loadSamples();
  filePlayer=new MidiFilePlayer(this);
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId) {
  if(intent!=null && "stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(intent!=null && "load_midi".equals(intent.getAction())){filePlayer.load();return START_NOT_STICKY;}
  if(intent!=null && "preview_midi".equals(intent.getAction())){filePlayer.start(false);return START_NOT_STICKY;}
  if(intent!=null){requestedId=intent.getIntExtra("device",-1);requestedPort=intent.getIntExtra("port",0);}
  if(manager!=null)connect(requestedId,requestedPort);else midiStatus="系统没有 MIDI 服务";
  return START_NOT_STICKY;
 }
 public synchronized void refreshConfig(){panic();transpose=Config.prefs(this).getInt("transpose",0);fold=Config.prefs(this).getBoolean("fold",false);audio.volume(Config.prefs(this).getFloat("volume",0.65f));audio.muted(Config.prefs(this).getBoolean("local_muted",false));audio.oneShot(Config.prefs(this).getBoolean("one_shot",true));audio.profile(Config.prefs(this).getInt("audio_profile",0));}
 public synchronized void setLocalMuted(boolean muted){
  audio.muted(muted);Config.prefs(this).edit().putBoolean("local_muted",muted).apply();
  main.post(()->{TouchService t=TouchService.instance;if(t!=null)t.refreshLocalMuteControl();});
 }
 public synchronized boolean toggleLocalMute(){boolean muted=!audio.isMuted();setLocalMuted(muted);return muted;}
 private synchronized void message(int status,int a,int b) {
  if(filePlayer!=null&&(filePlayer.running||filePlayer.paused||filePlayer.preparing))return;
  int kind=status&0xf0,ch=status&15,id=ch*128+a;
  if(kind==0x90 && b>0) {
   // channel 10 is normally percussion; do not map it to the lyre
   if(ch==9)return;
   int ix=NoteMap.index(a,transpose,fold);if(ix<0)return;
   if(latched[id]>=0)audio.event(2,id,0);
   latched[id]=NoteMap.NOTES[ix];audio.event(1,id,(latched[id]<<8)|b);noteCount++;
   TouchService t=TouchService.instance;if(t!=null)t.midiDown(id,ix);
  } else if(kind==0x80 || (kind==0x90 && b==0)) {
   if(latched[id]>=0){audio.event(2,id,0);latched[id]=-1;}
   TouchService t=TouchService.instance;if(t!=null)t.midiUp(id);
  } else if(kind==0xb0) {
   if(a==64)audio.event(4,ch*128,b);
   if(a==120 || a==123)panic(); // conservative all-channel emergency release
  }
 }
 public synchronized void silenceAudio(){Arrays.fill(latched,-1);audio.event(3,0,0);}
 public synchronized void panic(){if(filePlayer!=null)filePlayer.stop();silenceAudio();TouchService t=TouchService.instance;if(t!=null)t.clearNotes();}
 public synchronized void testNote(int n,boolean down){int id=15*128+n;audio.event(down?1:2,id,down?((n<<8)|100):0);}
 public synchronized void loadSamples() {
  final int token=++sampleGeneration;samplesLoading=true;
  new Thread(()->{
   WavData[] bank=new WavData[128];long bytes=0;int loaded=0;String error="";
   for(int n:NoteMap.NOTES){
    File f=new File(getFilesDir(),"note_"+n+".wav");
    try(InputStream in=f.exists()?new FileInputStream(f):getAssets().open("samples/note_"+n+".wav")){
     WavData w=WavData.read(in).resample(audio.sampleRate());if(bytes+w.samples.length*4L>64*1024*1024L)throw new IOException("采样库超过64MiB");bytes+=w.samples.length*4L;bank[n]=w;loaded++;
    }catch(IOException e){error="；音符"+n+"："+e.getMessage();}
   }
   if(token==sampleGeneration && instance==this){audio.samples(bank);sampleStatus="内置 HarmonicKey / 自定义 WAV："+loaded+"/21 键"+error;samplesLoading=false;}
  },"SampleLoader").start();
 }
 private void connect(int id,int selectedPort) {
  disconnect();
  MidiDeviceInfo chosen=null;
  for(MidiDeviceInfo info:manager.getDevices()) {
   if(info.getType()!=MidiDeviceInfo.TYPE_USB || info.getOutputPortCount()==0)continue;
   if(id<0 || info.getId()==id){chosen=info;break;}
  }
  if(chosen==null){midiStatus="未发现 USB MIDI：检查数据线、OTG 和键盘供电";return;}
  final MidiDeviceInfo target=chosen;final int token=generation;
  midiStatus="正在连接 USB MIDI…";
  manager.openDevice(chosen,d->{
   if(token!=generation || instance!=this){if(d!=null)try{d.close();}catch(IOException ignored){}return;}
   if(d==null){midiStatus="MIDI 打开失败";return;}
   try {
    MidiOutputPort output=d.openOutputPort(selectedPort);
    if(output==null){d.close();midiStatus="MIDI 端口打开失败：检查端口号";return;}
    device=d;port=output;output.connect(receiver);
    String name=target.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"USB MIDI");midiStatus="已连接："+name+" / 端口 "+selectedPort;
   }catch(Exception e){try{d.close();}catch(IOException ignored){}midiStatus="连接失败："+e.getMessage();}
  },main);
 }
 private void disconnect() {
  generation++;
  if(port!=null){port.disconnect(receiver);try{port.close();}catch(IOException ignored){}port=null;}
  if(device!=null){try{device.close();}catch(IOException ignored){}device=null;}
  if(parser!=null)parser.reset();if(filePlayer==null||(!filePlayer.running&&!filePlayer.paused&&!filePlayer.preparing)){silenceAudio();TouchService t=TouchService.instance;if(t!=null)t.clearNotes();}
 }
 @Override public void onDestroy(){if(filePlayer!=null)filePlayer.close();instance=null;TouchService t=TouchService.instance;if(t!=null)t.setArmed(false);if(manager!=null)manager.unregisterDeviceCallback(callback);disconnect();audio.stop();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
