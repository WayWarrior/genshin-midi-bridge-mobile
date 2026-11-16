package com.junliang.midibridge;
import android.accessibilityservice.*;
import android.app.AlertDialog;
import android.graphics.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;
public final class TouchService extends AccessibilityService {
 public static volatile TouchService instance;
 public volatile boolean armed;
 public volatile String status="映射关闭";
 public volatile long sent,dropped;
 public volatile long maxFileLateMs;
 public volatile long liveReconnections;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ArrayDeque<Hit> pending=new ArrayDeque<>();
 private boolean busy;private int epoch;
 private WindowManager wm;private View calibration;private FloatingControls controls;private boolean closing;
 private int width,height;private GameMuteController gameMute;
 private String eventPackage="",foreground="",lastDiagnostic="";private int eventWindow=-1;
 private static final class Hit {int ix;long time;Hit(int i){ix=i;time=SystemClock.uptimeMillis();}}
 private final LiveTouchState liveKeys=new LiveTouchState();
 private final HashMap<Integer,LiveGestureBatch.Held> liveHeld=new HashMap<>();
 private final GestureDispatchGuard liveGuard=new GestureDispatchGuard();
 private final LivePointerBudget liveBudget=new LivePointerBudget();
 private final LiveTouchSlots liveSlots=new LiveTouchSlots();private boolean liveReuseSession;
 private TouchReuseTest reuseTest;private boolean reusePreparing;private long reusePrepareDeadline;
 private final Runnable reusePreparation=this::prepareReuseTest;
 private final Runnable liveNext=this::dispatchLive,liveWatchdog=this::checkLiveTimeout;
 private int liveWidth,liveHeight;
 private final TouchRecoveryState recovery=new TouchRecoveryState();
 private boolean fileActive,fileBusy,releaseDeferred,resetRequested;
 private long liveNoticeUntil;private String liveNotice="";private FileTapPlan fileTaps;private long fileOrigin;
 private final GestureDispatchGuard fileGuard=new GestureDispatchGuard();private View releaseOverlay;
 private final Runnable fileWatchdog=this::checkFileTimeout,releaseWatchdog=this::checkReleaseTimeout,recoveryStart=this::startRecovery;
 private int fileWidth,fileHeight;
 public int fileStrokeLimit(){return GestureDescription.getMaxStrokeCount();}
 public int filePointerLimit(){return Math.min(10,fileStrokeLimit());}
 boolean fileSessionValid(){dimensions();return width==fileWidth&&height==fileHeight&&calibration==null&&(gameMute==null||!gameMute.active())&&inGame()&&Config.calibrated(this,width,height);}
 public boolean fileReady(boolean explain){dimensions();String problem=null;
  if(reuseChecking())problem="先完成长按兼容检测";
  else if(calibration!=null||(gameMute!=null&&gameMute.active()))problem="先完成校准或游戏声音操作";
  else if(!recovery.inputReady())problem="触控释放未获确认，请先在悬浮菜单点重置触控";
  else if(liveKeys.hasWork()||liveGuard.busy()||!liveHeld.isEmpty())problem="先松开 MIDI 键盘或关闭实时映射，再播放文件";
  else if(busy||fileBusy||releaseDeferred||releaseOverlay!=null)problem="上一组触控尚在释放，稍后再播放";
  else if(!inGame())problem=detectionHint();else if(!Config.calibrated(this,width,height))problem="先在当前横屏尺寸完成琴键校准";else if(!avoidBallKeys())problem="请先把悬浮球拖离琴键";
  if(problem!=null){status=problem;if(explain)toast(problem);return false;}return true;
 }
 public void startFile(MidiScore score,long origin,long resume){
  armed=false;clearOnMain();if(controls!=null)controls.setActive(true);fileOrigin=origin;fileWidth=width;fileHeight=height;fileTaps=new FileTapPlan(score.taps,resume);maxFileLateMs=0;fileActive=true;status="MIDI琴类点按倒计时";
  main.postAtTime(fileNext,origin+resume);
 }
 public boolean filePlaying(){return fileActive||fileBusy;}
 public void stopFile(){fileActive=false;fileTaps=null;if(controls!=null)controls.setActive(armed);main.removeCallbacks(fileNext);if(fileBusy)cancelTouches();}
 private final Runnable fileNext=this::dispatchFile;
 private void fileFailed(String reason){fileActive=false;status=reason;BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.pauseNow(reason);cancelTouches();}
 private long awaitFileGesture(long duration){long token=fileGuard.begin(SystemClock.uptimeMillis(),duration);fileBusy=true;main.removeCallbacks(fileWatchdog);main.postAtTime(fileWatchdog,fileGuard.deadline());return token;}
 private boolean finishFileGesture(long token){if(!fileGuard.finish(token))return false;main.removeCallbacks(fileWatchdog);fileBusy=false;return true;}
 private void checkFileTimeout(){if(!fileGuard.expire(SystemClock.uptimeMillis()))return;fileBusy=false;
  if(releaseOverlay!=null){checkReleaseTimeout();return;}
  fileFailed("系统未回报触控完成，MIDI 已暂停并请求松键；请使用琴类短点按");
 }
 private Path motionPath(HoldMotion m){Path path=new Path();path.moveTo(m.startX,m.startY);if(m.moved())path.lineTo(m.endX,m.endY);return path;}
 private void dispatchFile(){
  if(!fileActive||fileBusy)return;dimensions();
  if(!inGame()||width!=fileWidth||height!=fileHeight||!Config.calibrated(this,width,height)){fileFailed("离开原神或屏幕变化，MIDI 已暂停");return;}
  final FileTapPlan plan=fileTaps;if(plan==null)return;long now=Math.max(0,SystemClock.uptimeMillis()-fileOrigin),next=plan.nextStart();
  if(next==Long.MAX_VALUE){fileActive=false;return;}if(next>now){main.postAtTime(fileNext,fileOrigin+next);return;}
  maxFileLateMs=Math.max(maxFileLateMs,Math.max(0,now-next));
  final FileTapPlan.Batch batch;try{batch=plan.batch(now,fileStrokeLimit());}catch(Exception e){fileFailed(e.getMessage());return;}
  GestureDescription.Builder builder=new GestureDescription.Builder();
  android.content.SharedPreferences preferences=Config.prefs(this);
  try{for(FileTapPlan.Part p:batch.parts){
   if(keyCovered(p.gate.key,fileWidth,fileHeight))throw new IllegalArgumentException("悬浮球遮住琴键，请拖开");
   Path path=new Path();path.moveTo(preferences.getFloat("x"+p.gate.key,0)*fileWidth,preferences.getFloat("y"+p.gate.key,0)*fileHeight);
   builder.addStroke(new GestureDescription.StrokeDescription(path,p.offset,p.duration,false));
  }}
  catch(Exception e){fileFailed("MIDI 手势生成失败："+e.getMessage());return;}
  final int hits=batch.count;final long token=awaitFileGesture(batch.end-batch.from);
  GestureResultCallback callback=new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription g){if(!finishFileGesture(token))return;if(fileTaps!=plan||!plan.complete(batch))return;sent+=hits;status="MIDI琴类点按运行中";long next=plan.nextStart();if(next==Long.MAX_VALUE)fileActive=false;else if(fileActive)main.postAtTime(fileNext,Math.max(SystemClock.uptimeMillis(),fileOrigin+next));}
   @Override public void onCancelled(GestureDescription g){if(!finishFileGesture(token))return;dropped+=hits;fileFailed("触控被取消，MIDI 已暂停并请求松键");}
  };
  try{if(!dispatchGesture(builder.build(),callback,main)){finishFileGesture(token);fileFailed("系统拒绝 MIDI 触控，已暂停并请求松键");}}catch(Exception e){finishFileGesture(token);fileFailed("触控提交失败："+e.getMessage());}
 }
 /** Cancel retained pointers only with controls closed and a full-screen consuming shield. */
 private void cancelTouches(){
  liveGuard.cancel();liveBudget.clear();liveSlots.clear();main.removeCallbacks(liveNext);main.removeCallbacks(liveWatchdog);liveKeys.clear();liveHeld.clear();
  fileActive=false;fileTaps=null;main.removeCallbacks(fileNext);if(releaseOverlay!=null)return;
  fileGuard.cancel();main.removeCallbacks(fileWatchdog);fileBusy=false;
  main.removeCallbacks(recoveryStart);
  if(closing)return;
  releaseDeferred=true;
  if(controls!=null&&controls.interacting())return;
  // A cancellation callback may precede the real DOWN on the ball. Let it arrive first.
  main.postDelayed(recoveryStart,40);
 }
 private void startRecovery(){
  if(closing||releaseOverlay!=null||(controls!=null&&controls.interacting()))return;
  releaseDeferred=false;final long recoveryToken=recovery.begin(SystemClock.uptimeMillis());
  if(controls!=null)controls.setRecovery(true);
  View shield=new View(this);shield.setBackgroundColor(0x01000000);releaseOverlay=shield;
  // The framework completion is authoritative even if the consuming View misses its UP.
  shield.setOnTouchListener((v,e)->true);
  WindowManager.LayoutParams params=overlayParams(-1,-1);params.flags|=WindowManager.LayoutParams.FLAG_FULLSCREEN;
  try{wm.addView(shield,params);}catch(Exception e){releaseOverlay=null;recovery.failed(recoveryToken);status="无法创建松键恢复层；可在菜单里重置触控";if(controls!=null)controls.setRecovery(false);return;}
  final long token=awaitFileGesture(100);main.removeCallbacks(releaseWatchdog);main.postAtTime(releaseWatchdog,recovery.deadline());
  shield.postOnAnimation(()->submitRelease(shield,token,recoveryToken));
 }
 private void submitRelease(View shield,long token,long recoveryToken){
  if(!fileGuard.matches(token)||releaseOverlay!=shield)return;
  if(shield.getWidth()==0||shield.getHeight()==0){shield.postOnAnimation(()->submitRelease(shield,token,recoveryToken));return;}
  int[] location=new int[2];shield.getLocationOnScreen(location);Path path=new Path();path.moveTo(location[0]+shield.getWidth()/2f,location[1]+shield.getHeight()/2f);
  GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,24,false)).build();
  try{boolean submitted=dispatchGesture(gesture,new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription g){
    if(!finishFileGesture(token)||!recovery.completed(recoveryToken))return;
    if(resetRequested){resetRequested=false;status="触控已重置，请重新开启映射或继续文件演奏";}
    main.postDelayed(()->{if(releaseOverlay==shield)removeReleaseOverlay();},80);
   }
   @Override public void onCancelled(GestureDescription g){if(!finishFileGesture(token))return;recovery.failed(recoveryToken);removeReleaseOverlay();status="松键恢复被取消；菜单可用，请点重置触控";}
  },main);if(!submitted){finishFileGesture(token);recovery.failed(recoveryToken);removeReleaseOverlay();status="系统拒绝松键恢复；菜单可用，请点重置触控";}}
  catch(Exception e){finishFileGesture(token);recovery.failed(recoveryToken);removeReleaseOverlay();status="松键恢复失败；菜单可用，请点重置触控";}
 }
 private void checkReleaseTimeout(){
  if(releaseOverlay==null)return;fileGuard.cancel();fileBusy=false;recovery.expire(SystemClock.uptimeMillis());removeReleaseOverlay();
  if(!recovery.inputReady())status="触控释放未获确认；菜单可用，请点重置触控";
 }
 public void resetTouch(){main.post(()->{
  if(gameMute!=null&&gameMute.active())gameMute.cancel();
  pauseForControls();if(controls!=null)controls.collapse();resetRequested=true;status="正在重置触控，完成后可重新开启映射";cancelTouches();
 });}
 private void removeReleaseOverlay(){
  main.removeCallbacks(releaseWatchdog);main.removeCallbacks(fileWatchdog);
  if(releaseOverlay!=null){try{wm.removeView(releaseOverlay);}catch(Exception ignored){}releaseOverlay=null;}
  if(controls!=null)controls.setRecovery(false);
 }
 @Override protected void onServiceConnected(){instance=this;if(Config.prefs(this).getBoolean("live_reuse_checking",false))Config.finishReuseCheck(this,false,"上次检测中断，请重新检测");wm=(WindowManager)getSystemService(WINDOW_SERVICE);gameMute=new GameMuteController(this,wm);showToolbar();}
 private void dimensions(){DisplayMetrics d=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(d);width=d.widthPixels;height=d.heightPixels;}

 private String packageOf(AccessibilityNodeInfo root) {
  if(root==null)return "";
  try{return root.getPackageName()==null?"":root.getPackageName().toString();}finally{root.recycle();}
 }
 private List<ForegroundResolver.Window> windowEvidence() {
  ArrayList<ForegroundResolver.Window> result=new ArrayList<>();
  for(AccessibilityWindowInfo w:getWindows()){
   try{String pkg=packageOf(w.getRoot());result.add(new ForegroundResolver.Window(w.getId(),w.getType(),w.getLayer(),w.isFocused(),w.isActive(),pkg));}finally{w.recycle();}
  }
  return result;
 }
 private String foregroundPackage() {
  try {
   List<ForegroundResolver.Window> windows=windowEvidence();
   AccessibilityNodeInfo root=getRootInActiveWindow();int rootType=-1;String rootPkg="";
   if(root!=null){AccessibilityWindowInfo w=root.getWindow();if(w!=null){rootType=w.getType();w.recycle();}rootPkg=packageOf(root);}
   foreground=ForegroundResolver.resolve(windows,rootPkg,rootType,eventPackage,eventWindow);
   StringBuilder d=new StringBuilder("检测包名：").append(foreground.isEmpty()?"未能读取":foreground).append("\n事件包名：").append(eventPackage.isEmpty()?"无":eventPackage).append("\n");
   for(ForegroundResolver.Window w:windows)d.append("窗口 ").append(w.id).append(" 类型 ").append(w.type).append(" 焦点 ").append(w.focused).append(" 活动 ").append(w.active).append("：").append(w.pkg).append("\n");
   lastDiagnostic=d.toString();return foreground;
  }catch(Exception e){lastDiagnostic="前台检测失败："+e.getClass().getSimpleName()+" / "+e.getMessage();foreground="";return "";}
 }
 private boolean inGame(){return Config.target(this,foregroundPackage());}
 private String detectionHint(){String pkg=foreground.isEmpty()?"未能读取包名":foreground;return "未识别为原神（"+pkg+"）。在游戏里点“诊断/绑定”";}
 private void showDiagnostics() {
  String pkg=foregroundPackage();
  boolean canBind=!pkg.isEmpty() && !pkg.equals(getPackageName()) && !pkg.startsWith("[") && !pkg.equals("android") && !pkg.contains("systemui") && !pkg.contains("launcher");
  AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("前台诊断 / 绑定原神").setMessage(lastDiagnostic+"\n当前目标："+Config.prefs(this).getString("package","（官服默认）")+"\n\n如果此刻屏幕是原神的21键琴界面，可以绑定检测包名；不要绑定其他应用。这里只检查应用，不识别琴界面。").setNegativeButton("关闭",null);
  if(canBind)builder.setPositiveButton("确认此应用是原神，绑定",(dialog,which)->{
   String now=foregroundPackage();if(!now.equals(pkg)){toast("前台已变化，请回到原神再绑定");return;}
   Config.prefs(this).edit().putString("package",pkg).apply();status="已绑定："+pkg;toast("已绑定。接着点“3点校准”");
  });
  AlertDialog dialog=builder.create();if(dialog.getWindow()!=null)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
 }
 public void setArmed(boolean enabled){main.post(()->{
  if(reuseChecking()){if(enabled)toast("先完成长按兼容检测");return;}
  if(fileActive||fileBusy||releaseDeferred||releaseOverlay!=null){BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.pauseNow("切换实时映射，MIDI 已暂停");stopFile();if(enabled){toast("触控释放后再点开启映射");return;}}
  if(enabled){dimensions();if(!recovery.inputReady()){status="先在悬浮菜单点重置触控；若仍失败，再重新开启无障碍服务";toast(status);return;}if(BridgeService.instance==null){status="先启动 MIDI 音源";toast(status);return;}if(!inGame()){status=detectionHint();toast(status);return;}if(!Config.calibrated(this,width,height)){status="当前屏幕尺寸尚未校准";toast(status);return;}if(!avoidBallKeys()){status="请先把悬浮球拖离琴键";toast(status);return;}}
  boolean reuse=enabled&&Config.prefs(this).getBoolean("live_hold",true)&&Config.prefs(this).getBoolean("live_reuse",true);
  if(reuse&&!Config.reusePassed(this)){status="请先到03 · 调音与声音，点“检测并启用触点复用”";toast(status);return;}
  if(enabled){BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null&&(b.filePlayer.running||b.filePlayer.paused||b.filePlayer.preparing))b.filePlayer.stopNow(true);}
  armed=enabled;clearOnMain();liveReuseSession=reuse;if(controls!=null)controls.setActive(enabled);status=enabled?(Config.prefs(this).getBoolean("live_hold",true)?(reuse?"实时长按已开：触点复用，松开释放":"实时长按已开：兼容重接模式，保持音可能重新起音"):"实时映射已开：短点按"):"映射关闭";
 });}
 public void clearNotes(){main.post(()->{stopFile();clearOnMain();});}
 void stopRealtime(){armed=false;clearOnMain();if(controls!=null)controls.setActive(false);status="实时映射关闭";}
 private void clearOnMain(){pending.clear();liveNoticeUntil=0;epoch++;stopLive(); /* let any short closed gesture finish */}
 public void midiDown(int source,int key){main.post(()->{
  if(!armed||calibration!=null||(gameMute!=null&&gameMute.active())||fileActive||fileBusy||releaseDeferred||releaseOverlay!=null||!recovery.inputReady())return;
  if(!Config.prefs(this).getBoolean("live_hold",true)){note(key);return;}
  dimensions();
  if(!inGame()||!Config.calibrated(this,width,height)){liveFailed("离开原神或屏幕变化，实时映射已关闭");return;}
  if(!liveKeys.hasWork()&&!liveGuard.busy()&&liveHeld.isEmpty()){liveWidth=width;liveHeight=height;}
  if(keyCovered(key,liveWidth,liveHeight)){liveFailed("悬浮球遮住了琴键，请拖开后重新开启映射");return;}
  int changed=liveKeys.down(source,key,filePointerLimit());
  if(changed<0){
   dropped++;liveNotice=liveKeys.pending()>=LiveTouchState.MAX_PENDING?"起音队列已满，忽略本次按键；映射继续":"超过 "+filePointerLimit()+" 个不同琴键，忽略超出的键；松开后重新按即可";
   liveNoticeUntil=SystemClock.uptimeMillis()+1800;status=liveNotice;return;
  }
  if(changed>0)scheduleLive(2);
 });}
 public void midiUp(int source){main.post(()->{if(liveKeys.up(source))scheduleLive(0);});}
 private void scheduleLive(long delay){if(!liveGuard.busy()&&armed){main.removeCallbacks(liveNext);main.postDelayed(liveNext,delay);}}
 private void stopLive(){
  boolean touching=liveGuard.busy()||!liveHeld.isEmpty();liveKeys.clear();main.removeCallbacks(liveNext);main.removeCallbacks(liveWatchdog);
  if(touching)cancelTouches();else{liveGuard.cancel();liveBudget.clear();liveSlots.clear();}
 }
 private void liveFailed(String reason){
  armed=false;status=reason;pending.clear();epoch++;if(controls!=null)controls.setActive(false);cancelTouches();
 }
 private void checkLiveTimeout(){if(liveGuard.expire(SystemClock.uptimeMillis()))liveFailed("实时触控未回报完成，已关闭映射并请求松键");}
 private boolean finishLiveGesture(long token){if(!liveGuard.finish(token))return false;main.removeCallbacks(liveWatchdog);return true;}
 private void dispatchLive(){
  if(liveGuard.busy())return;
  if(!armed){stopLive();return;}
  if(reuseChecking()||fileBusy||fileActive||releaseOverlay!=null||!recovery.inputReady())return;
  if(busy){main.postDelayed(liveNext,LiveTouchPlan.SEGMENT_MS);return;}
  dimensions();
  if(calibration!=null||(gameMute!=null&&gameMute.active())||width!=liveWidth||height!=liveHeight||!inGame()||!Config.calibrated(this,width,height)){liveFailed("离开原神、校准或屏幕变化，实时映射已关闭");return;}
  long[] previous=new long[LiveTouchState.KEYS];for(Map.Entry<Integer,LiveGestureBatch.Held> e:liveHeld.entrySet())previous[e.getKey()]=e.getValue().version;
  final LiveTouchPlan plan;final boolean reconnect;final LiveTouchSlots.Batch allocation;final LiveGestureBatch batch;
  try{
   LiveTouchPlan requested=LiveTouchPlan.create(liveKeys,previous,fileStrokeLimit(),filePointerLimit());
   reconnect=!liveReuseSession&&liveBudget.needsReconnect(requested);plan=reconnect?LiveTouchPlan.releaseAll(previous,liveKeys.revision()):requested;if(plan.parts.isEmpty())return;
   allocation=liveReuseSession?liveSlots.plan(plan):null;
   android.content.SharedPreferences prefs=Config.prefs(this);float[] xs=new float[21],ys=new float[21];
   for(int k=0;k<21;k++){xs[k]=prefs.getFloat("x"+k,0)*liveWidth;ys[k]=prefs.getFloat("y"+k,0)*liveHeight;}
   batch=new LiveGestureBatch(plan,allocation,liveHeld,xs,ys,liveWidth);
  }catch(Exception|LinkageError e){liveFailed("实时触控计划失败："+e.getClass().getSimpleName()+" / "+e.getMessage());return;}
  final int hits=batch.hits;final long started=SystemClock.uptimeMillis(),token=liveGuard.begin(started,plan.duration);
  main.postAtTime(liveWatchdog,liveGuard.deadline());
  GestureResultCallback callback=new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription g){
    if(!finishLiveGesture(token))return;
    if(allocation!=null){if(!batch.serialized()){reuseUnavailable();return;}if(!liveSlots.complete(allocation)){liveFailed("触点状态已变化，请重新开启映射");return;}}
    else liveBudget.completed(plan);
    sent+=hits;plan.acknowledge(liveKeys);liveHeld.clear();liveHeld.putAll(batch.tail);
    if(reconnect){liveReconnections++;liveNotice="多指兼容重接：恢复仍按住的键（可能重新起音） · "+liveReconnections+"次";liveNoticeUntil=SystemClock.uptimeMillis()+1800;}
    status=SystemClock.uptimeMillis()<liveNoticeUntil?liveNotice:(allocation!=null?"触点复用长按：保持 ":"兼容长按：保持 ")+liveKeys.count()+" 键";
    if(!armed){stopLive();return;}
    if(liveKeys.hasWork()||!liveHeld.isEmpty())main.postAtTime(liveNext,liveKeys.revision()!=plan.revision?SystemClock.uptimeMillis():Math.max(SystemClock.uptimeMillis(),started+plan.duration));
   }
   @Override public void onCancelled(GestureDescription g){if(!finishLiveGesture(token))return;dropped+=hits;liveFailed("实时触控被取消，已关闭映射并请求松键");}
  };
  try{if(!dispatchGesture(batch.gesture,callback,main)){finishLiveGesture(token);liveFailed("系统拒绝实时触控，已请求松键");}else if(allocation!=null&&!batch.serialized()){reuseUnavailable();}}
  catch(Exception|LinkageError e){finishLiveGesture(token);liveFailed("实时触控提交失败："+e.getClass().getSimpleName()+" / "+e.getMessage());}
 }
 private void reuseUnavailable(){Config.finishReuseCheck(this,false,"系统未采用复用编号，请重新检测");liveFailed("触点复用不可用，已关闭映射并请求松键；请重新检测");}
 public boolean reuseChecking(){return reusePreparing||reuseTest!=null;}
 public void testTouchReuse(){main.post(()->{
  if(reuseChecking()){toast("长按兼容检测正在进行");return;}
  if(calibration!=null||(gameMute!=null&&gameMute.active())){toast("先结束校准或声音操作");return;}
  stopFile();stopRealtime();hideToolbar();Config.beginReuseCheck(this);reusePreparing=true;reusePrepareDeadline=SystemClock.uptimeMillis()+2000;status="正在准备长按兼容检测…";main.postDelayed(reusePreparation,180);
 });}
 private void prepareReuseTest(){
  if(!reusePreparing||closing)return;
  if(!controlsReleased()||!recovery.inputReady()){
   if(SystemClock.uptimeMillis()<reusePrepareDeadline){main.postDelayed(reusePreparation,80);return;}
   reusePreparing=false;Config.finishReuseCheck(this,false,"上一组触控尚未释放，请先重置触控再检测");status=Config.reuseStatus(this);showToolbar();toast(status);return;
  }
  reusePreparing=false;reuseTest=new TouchReuseTest(this,main,this::finishReuseTest);
  try{WindowManager.LayoutParams p=overlayParams(-1,-1);p.flags|=WindowManager.LayoutParams.FLAG_FULLSCREEN;wm.addView(reuseTest.view,p);status="正在检测长按兼容性，请暂时不要触屏";reuseTest.start();}
  catch(Exception|LinkageError e){TouchReuseTest test=reuseTest;reuseTest=null;test.dispose();try{wm.removeView(test.view);}catch(Exception ignored){}Config.finishReuseCheck(this,false,"无法打开检测页面："+e.getClass().getSimpleName());status=Config.reuseStatus(this);showToolbar();toast(status);}
 }
 private void finishReuseTest(boolean passed,String detail,boolean released){
  TouchReuseTest test=reuseTest;if(test==null)return;reuseTest=null;test.dispose();try{wm.removeView(test.view);}catch(Exception ignored){}
  Config.finishReuseCheck(this,passed,detail);status=Config.reuseStatus(this);if(!released)cancelTouches();showToolbar();toast(status);
 }
 public void cancelReuseTest(){main.post(()->{
  if(reusePreparing){reusePreparing=false;main.removeCallbacks(reusePreparation);Config.finishReuseCheck(this,false,"已取消检测");status=Config.reuseStatus(this);showToolbar();}
  if(reuseTest!=null)reuseTest.abort("已取消检测，可随时重新检测");
 });}
 public void note(int index){main.post(()->{
  if(!armed || calibration!=null || (gameMute!=null && gameMute.active()))return;
  if(fileActive||fileBusy||releaseDeferred||releaseOverlay!=null||liveGuard.busy()||!liveHeld.isEmpty()||!recovery.inputReady())return;
  dimensions();
  if(!inGame()){status="原神不在前台，已忽略点击";clearOnMain();return;}
  if(!Config.calibrated(this,width,height)){setArmed(false);status="屏幕尺寸变化，请重新校准";return;}
  if(pending.size()>=64){pending.removeFirst();dropped++;}
  if(keyCovered(index,width,height)){setArmed(false);status="悬浮球遮住了琴键，请拖开";return;}
  pending.addLast(new Hit(index));
  if(!busy) {busy=true;main.postDelayed(this::dispatchNext,4);}
 });}
 private void dispatchNext() {
  if(!armed || calibration!=null || !inGame()){pending.clear();busy=false;return;}
  long now=SystemClock.uptimeMillis();
  while(!pending.isEmpty() && now-pending.peekFirst().time>150){pending.removeFirst();dropped++;}
  if(pending.isEmpty()){busy=false;return;}
  int token=epoch;ArrayList<Hit> batch=new ArrayList<>();HashSet<Integer> keys=new HashSet<>();
  long start=pending.peekFirst().time;
  int max=filePointerLimit();
  while(!pending.isEmpty() && batch.size()<max && pending.peekFirst().time-start<=4) {
   Hit hit=pending.peekFirst();if(keys.contains(hit.ix))break;pending.removeFirst();keys.add(hit.ix);batch.add(hit);
  }
  GestureDescription.Builder g=new GestureDescription.Builder();android.content.SharedPreferences p=Config.prefs(this);
  for(Hit hit:batch){Path path=new Path();path.moveTo(p.getFloat("x"+hit.ix,0)*width,p.getFloat("y"+hit.ix,0)*height);g.addStroke(new GestureDescription.StrokeDescription(path,0,24));}
  GestureResultCallback done=new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription d){sent+=batch.size();if(token==epoch&&armed)status="映射运行中";finish();}
   @Override public void onCancelled(GestureDescription d){dropped+=batch.size();if(token==epoch&&armed)status="触控被取消：避免同时手动点击";finish();}
   private void finish(){busy=false;if(token!=epoch)pending.clear();if(armed && !pending.isEmpty()){busy=true;main.postDelayed(TouchService.this::dispatchNext,4);}}
  };
  if(!dispatchGesture(g.build(),done,main)){dropped+=batch.size();busy=false;status="系统拒绝触控";if(!pending.isEmpty()){busy=true;main.postDelayed(this::dispatchNext,4);}}
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private WindowManager.LayoutParams overlayParams(int w,int h) {
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.LEFT;if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;
 }
 void hideToolbar(){if(controls!=null)controls.hide();}
 private void showToolbar(){
  if(closing)return;
  if(controls==null)controls=new FloatingControls(this,wm,this::pauseForControls,this::finishControlTouch,this::controlsReady,
   this::controlLabels,
   new Runnable[]{()->setArmed(true),this::showFileControls,this::beginCalibration,this::configureGameSound,()->{if(gameMute!=null)gameMute.muteGame();},()->{if(gameMute!=null)gameMute.soundOnGame();},this::toggleLocalMute,this::showDiagnostics,this::resetTouch,()->startActivity(new android.content.Intent(this,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)),()->{if(controls!=null)controls.collapse();}},6);
  controls.show();
 }
 private String[] controlLabels(){
  BridgeService b=BridgeService.instance;boolean muted=b==null?Config.prefs(this).getBoolean("local_muted",false):b.audio.isMuted();
  return new String[]{"开启映射","MIDI文件","3点校准","声音设置","游戏静音","游戏开声",muted?"恢复本地声音":"本地音源静音","诊断/绑定","重置触控","打开应用","收起"};
 }
 private void toggleLocalMute(){
  BridgeService b=BridgeService.instance;boolean muted;
  if(b!=null)muted=b.toggleLocalMute();else{muted=!Config.prefs(this).getBoolean("local_muted",false);Config.prefs(this).edit().putBoolean("local_muted",muted).apply();}
  toast((muted?"本地音源已静音":"本地声音已恢复")+(b==null?"；启动音源后生效":""));
 }
 void refreshLocalMuteControl(){if(controls!=null)controls.refreshToggleLabel();}
 private void pauseForControls(){
  BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null&&(b.filePlayer.gameMode||b.filePlayer.preparing))b.filePlayer.pauseNow("打开控制菜单，游戏演奏已暂停");
  stopRealtime();
 }
 private void finishControlTouch(){if(releaseDeferred)cancelTouches();}
 private boolean controlsReady(){return !reuseChecking()&&calibration==null&&(gameMute==null||!gameMute.active())&&controlsReleased()&&recovery.menuReady();}
 private boolean avoidBallKeys(){
  if(controls==null)return true;float[] xs=new float[21],ys=new float[21];android.content.SharedPreferences p=Config.prefs(this);
  for(int k=0;k<21;k++){xs[k]=p.getFloat("x"+k,0)*width;ys[k]=p.getFloat("y"+k,0)*height;}
  return controls.avoidKeys(xs,ys);
 }
 private boolean keyCovered(int key,int w,int h){android.content.SharedPreferences p=Config.prefs(this);return controls!=null&&controls.covers(p.getFloat("x"+key,0)*w,p.getFloat("y"+key,0)*h);}

 boolean canOperateGame(){return inGame();}
 void releaseExternalTouch(){cancelTouches();}
 private boolean controlsReleased(){return !busy&&!fileBusy&&!releaseDeferred&&releaseOverlay==null&&!liveGuard.busy()&&liveHeld.isEmpty();}
 boolean gameControlReady(){return !reuseChecking()&&controlsReleased()&&recovery.inputReady();}
 private void showFileControls(){BridgeService b=BridgeService.instance;if(b==null||b.filePlayer==null){toast("先在应用里导入 MIDI 并启动音源");return;}MidiFilePlayer player=b.filePlayer;
  if(player.running)player.pauseNow("打开文件控制，MIDI 已暂停");
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("MIDI 自动演奏").setMessage(player.progress()+"\n播放前确认原神琴界面；3秒倒计时后开始。暂停会释放全部键。").setPositiveButton(player.paused?"继续演奏":"开始演奏",(d,w)->main.postDelayed(()->player.start(true),150)).setNeutralButton("从头停止",(d,w)->player.stop()).setNegativeButton("关闭",null).create();
  if(dialog.getWindow()!=null)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
 }
 int[] screenSize(){dimensions();return new int[]{width,height};}
 void prepareGameControl(){BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.pauseNow("执行游戏声音操作，MIDI 已暂停");stopFile();armed=false;clearOnMain();hideToolbar();}
 void finishGameControl(){showToolbar();}
 public void configureGameSound(){main.post(()->{if(gameMute!=null)gameMute.configure();});}
 private void beginCalibration() {
  if(!inGame()){toast(detectionHint());return;}
  dimensions();if(width<=height){toast("请横屏打开原神再校准");return;}
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择校准方式")
   .setItems(new String[]{"稳健3点：C3 → B3 → C5（推荐）","快速3点：C3 → D3 → C4","5点核对：C3 → D3 → C4 → B3 → C5"},(d,choice)->startCalibration(choice==0?GridCalibration.ROBUST:choice==1?GridCalibration.QUICK:GridCalibration.CHECKED)).setNegativeButton("取消",null).create();
  if(dialog.getWindow()!=null)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
 }
 private void startCalibration(int mode) {
  if(!inGame()){toast(detectionHint());return;}
  BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.pauseNow("开始校准，MIDI 已暂停");stopFile();dimensions();armed=false;clearOnMain();
  if(calibration!=null)return;
  hideToolbar();CalibrationView view=new CalibrationView(mode);calibration=view;
  WindowManager.LayoutParams p=overlayParams(-1,-1);p.flags|=WindowManager.LayoutParams.FLAG_FULLSCREEN;
  try{wm.addView(view,p);}catch(Exception e){calibration=null;showToolbar();toast("校准层失败："+e.getMessage());}
 }
 private void finishCalibration(){if(calibration!=null){wm.removeView(calibration);calibration=null;}showToolbar();}
 private final class CalibrationView extends View {
  int index;final int mode;final int[] anchors;final float[] xs,ys;float[][] points;
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final int originalWidth=width,originalHeight=height;
  CalibrationView(int m){super(TouchService.this);mode=m;anchors=GridCalibration.anchors(mode);xs=new float[anchors.length];ys=new float[anchors.length];}
  @Override protected void onDraw(Canvas c) {
   paint.setColor(0xeeffffff);c.drawRect(0,0,getWidth(),dp(88),paint);paint.setColor(Color.BLACK);paint.setTextSize(dp(18));
   String title=points==null?"第 "+(index+1)+" / "+anchors.length+"：点 "+NoteMap.label(NoteMap.NOTES[anchors[index]])+" 中心":"预览21键：圆圈应与琴键中心重合";
   c.drawText(title,dp(12),dp(28),paint);paint.setTextSize(dp(14));
   c.drawText(points==null?"下排=C3–B3；中排=C4–B4；上排=C5–B5。右上角取消":"右上角：保存 / 重测 / 取消；确认位置后保存",dp(12),dp(55),paint);
   if(points!=null){c.drawText("保存",getWidth()-dp(180),dp(28),paint);c.drawText("重测",getWidth()-dp(115),dp(28),paint);}c.drawText("×",getWidth()-dp(35),dp(28),paint);
   int[] loc=new int[2];getLocationOnScreen(loc);paint.setStrokeWidth(dp(2));
   if(points!=null){for(int i=0;i<21;i++){float x=points[i][0]-loc[0],y=points[i][1]-loc[1];paint.setColor(0xff00b36b);paint.setStyle(Paint.Style.STROKE);c.drawCircle(x,y,dp(9),paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(11));c.drawText(NoteMap.label(NoteMap.NOTES[i]).split(" ")[0],x-dp(10),y-dp(14),paint);}}
   else {paint.setColor(0xff00cc88);for(int i=0;i<index;i++){float x=xs[i]-loc[0],y=ys[i]-loc[1];c.drawLine(x-10,y,x+10,y,paint);c.drawLine(x,y-10,x,y+10,paint);}}
  }
  @Override public boolean onTouchEvent(MotionEvent e) {
   if(e.getAction()!=MotionEvent.ACTION_UP)return true;
   if(e.getY()<dp(88)){
    if(e.getX()>getWidth()-dp(70)){finishCalibration();return true;}
    if(points!=null && e.getX()>getWidth()-dp(140)){index=0;points=null;invalidate();return true;}
    if(points!=null && e.getX()>getWidth()-dp(210)){
     android.content.SharedPreferences.Editor ed=Config.prefs(TouchService.this).edit().putInt("width",width).putInt("height",height);
     for(int i=0;i<21;i++){ed.putFloat("x"+i,points[i][0]/width);ed.putFloat("y"+i,points[i][1]/height);}ed.apply();finishCalibration();toast("21键位置已保存；点开启映射后演奏");
    }return true;
   }
   if(points!=null)return true;
   dimensions();if(width!=originalWidth||height!=originalHeight){finishCalibration();toast("屏幕方向变化，校准已取消");return true;}
   xs[index]=e.getRawX();ys[index]=e.getRawY();index++;
   if(index==anchors.length){try{points=GridCalibration.calculate(xs,ys,mode,width,height);}catch(IllegalArgumentException ex){index=0;toast(ex.getMessage());}}invalidate();return true;
  }
 }
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){
  if(controls!=null)controls.refreshSize();
  if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName()!=null){
   String pkg=e.getPackageName().toString();boolean appEvent=false,overlay=false;
   try{for(AccessibilityWindowInfo w:getWindows())if(w.getId()==e.getWindowId()){overlay=w.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY;appEvent=w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION || w.getType()==AccessibilityWindowInfo.TYPE_SYSTEM;}}catch(Exception ignored){}
   String cls=e.getClassName()==null?"":e.getClassName().toString();
   if(!overlay && (appEvent || cls.endsWith("Activity") || Config.target(this,pkg))){eventPackage=pkg;eventWindow=e.getWindowId();}
  }
  if(e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED && (e.getWindowChanges()&AccessibilityEvent.WINDOWS_CHANGE_REMOVED)!=0 && e.getWindowId()==eventWindow){eventPackage="";eventWindow=-1;}
  if(!inGame()){
   if(fileActive){fileFailed("已切离原神，MIDI 自动暂停");}
   if(!pending.isEmpty())clearOnMain();
   if(armed){armed=false;clearOnMain();if(controls!=null)controls.setActive(false);status="已切离原神，映射自动关闭";}
  }
 }
 @Override public void onInterrupt(){cancelReuseTest();BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.pauseNow("无障碍中断，MIDI 已暂停");setArmed(false);}
 @Override public void onDestroy(){closing=true;reusePreparing=false;main.removeCallbacks(reusePreparation);if(reuseTest!=null){reuseTest.dispose();try{wm.removeView(reuseTest.view);}catch(Exception ignored){}reuseTest=null;}liveSlots.clear();BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.pauseNow("无障碍关闭，MIDI 已暂停");stopFile();fileGuard.cancel();liveGuard.cancel();liveKeys.clear();liveHeld.clear();removeReleaseOverlay();instance=null;armed=false;pending.clear();if(gameMute!=null)gameMute.close();main.removeCallbacksAndMessages(null);if(calibration!=null)wm.removeView(calibration);if(controls!=null)controls.close();super.onDestroy();}
}
