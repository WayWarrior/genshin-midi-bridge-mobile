package com.junliang.midibridge;
import android.accessibilityservice.*;
import android.app.AlertDialog;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
/** Independently recorded mute and sound-on gestures in absolute screen coordinates. */
public final class GameMuteController {
 private enum SoundAction {
  MUTE("game_mute_","游戏静音","把游戏总音量调到0","总音量调到0，返回琴界面后保存","确认游戏无声"),
  SOUND_ON("game_sound_on_","游戏开声","把游戏总音量调到你想要的大小","调到想要的音量，返回琴界面后保存","确认游戏音量合适");
  final String prefix,title,instruction,recordHint,completeHint;
  SoundAction(String prefix,String title,String instruction,String recordHint,String completeHint){this.prefix=prefix;this.title=title;this.instruction=instruction;this.recordHint=recordHint;this.completeHint=completeHint;}
  String key(String name){return prefix+name;}
 }
 private final TouchService service;private final WindowManager wm;private final Handler handler=new Handler(Looper.getMainLooper());
 private final GestureDispatchGuard guard=new GestureDispatchGuard();
 private SoundAction soundAction=SoundAction.MUTE;private AlertDialog dialog;
 private View overlay;private int width,height,generation;private boolean active,closed,firstOnly;
 private final ArrayList<RecordedGesture> recording=new ArrayList<>();private ArrayList<RecordedGesture> correction;
 private long lastStep;
 public GameMuteController(TouchService s,WindowManager manager){service=s;wm=manager;}
 public boolean active(){return active;}
 private int dp(int n){return Math.round(n*service.getResources().getDisplayMetrics().density);}
 private void toast(String s){Toast.makeText(service,s,Toast.LENGTH_LONG).show();}
 private WindowManager.LayoutParams params(int w,int h){
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;
 }
 private void removeOverlay(){if(overlay!=null){try{wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}}
 private void showDialog(AlertDialog d){
  if(dialog!=null)dialog.dismiss();dialog=d;d.setOnDismissListener(v->{if(dialog==d)dialog=null;});
  if(d.getWindow()!=null)d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.show();
 }
 private static JSONObject json(RecordedGesture a)throws JSONException{
  JSONObject j=new JSONObject();j.put("x1",a.x1);j.put("y1",a.y1);j.put("x2",a.x2);j.put("y2",a.y2);j.put("duration",a.duration);j.put("delay",a.delay);return j;
 }
 private ArrayList<RecordedGesture> saved(SoundAction action)throws JSONException{
  JSONArray array=new JSONArray(Config.prefs(service).getString(action.key("steps"),"[]"));if(array.length()==0||array.length()>40)throw new JSONException("步骤数无效");
  ArrayList<RecordedGesture> result=new ArrayList<>();
  for(int i=0;i<array.length();i++){JSONObject j=array.getJSONObject(i);result.add(new RecordedGesture((float)j.getDouble("x1"),(float)j.getDouble("y1"),(float)j.getDouble("x2"),(float)j.getDouble("y2"),j.getInt("duration"),j.getInt("delay")));}
  return result;
 }
 private void save(List<RecordedGesture> steps)throws JSONException{
  JSONArray array=new JSONArray();for(RecordedGesture a:steps)array.put(json(a));
  Config.prefs(service).edit().putString(soundAction.key("steps"),array.toString()).putInt(soundAction.key("width"),width).putInt(soundAction.key("height"),height).putInt(soundAction.key("recording_version"),2).apply();
 }
 public void configure(){
  if(active||closed)return;if(!service.canOperateGame()){toast("先回到原神琴界面，再设置游戏声音操作");return;}
  showDialog(new AlertDialog.Builder(service).setTitle("声音设置")
   .setItems(new String[]{"游戏静音 · 录制 / 校正","游戏开声 · 录制 / 校正"},(d,w)->configure(w==0?SoundAction.MUTE:SoundAction.SOUND_ON))
   .setNegativeButton("关闭",null).create());
 }
 private void configure(SoundAction action){
  if(active||closed)return;if(!service.canOperateGame()){toast("先回到原神琴界面，再设置"+action.title);return;}
  String raw=Config.prefs(service).getString(action.key("steps"),"");
  if(!raw.isEmpty())try{
   ArrayList<RecordedGesture> steps=saved(action);
   showDialog(new AlertDialog.Builder(service).setTitle(action.title+"：已录 "+steps.size()+" 步")
    .setMessage("第1步位置："+steps.get(0).position()+"\n\n如果第1步点错，可以只重新选它的位置，其余步骤保留。选择后只保存位置，不会立即点击游戏。")
    .setPositiveButton("仅校正第一步",(d,w)->beginCorrection(action,steps)).setNeutralButton("全部重新录制",(d,w)->recordingDialog(action)).setNegativeButton("关闭",null).create());return;
  }catch(Exception ignored){}
  recordingDialog(action);
 }
 private void recordingDialog(SoundAction action){
  if(active||closed)return;
  showDialog(new AlertDialog.Builder(service).setTitle("录制"+action.title+"操作")
   .setMessage("从琴界面开始：退出琴界面→设置→声音→"+action.instruction+"→返回琴界面。\n\n静音和开声分别保存，不会覆盖另一套操作。等录制层显示“点游戏的第一步”后再操作。只记录完整按下和松开，不会把启动对话框的松手记成第1步。")
   .setPositiveButton("开始录制",(d,w)->beginRecording(action)).setNegativeButton("取消",null).create());
 }
 private void prepare(SoundAction action){soundAction=action;int[] size=service.screenSize();width=size[0];height=size[1];active=true;generation++;service.prepareGameControl();}
 private boolean sameSavedSize(SoundAction action){
  int[] size=service.screenSize();return size[0]==Config.prefs(service).getInt(action.key("width"),0)&&size[1]==Config.prefs(service).getInt(action.key("height"),0);
 }
 private void beginCorrection(SoundAction action,ArrayList<RecordedGesture> steps){
  if(active||closed)return;if(!service.canOperateGame()){toast("请先回到原神");return;}if(!sameSavedSize(action)){toast("屏幕尺寸已经变化，请全部重新录制");return;}
  firstOnly=true;correction=new ArrayList<>(steps);prepare(action);afterRelease(generation,SystemClock.uptimeMillis(),()->handler.postDelayed(this::addRecorder,200));
 }
 private void beginRecording(SoundAction action){
  if(active||closed)return;if(!service.canOperateGame()){toast("请先回到原神");return;}firstOnly=false;correction=null;recording.clear();prepare(action);
  afterRelease(generation,SystemClock.uptimeMillis(),()->handler.postDelayed(()->{lastStep=SystemClock.uptimeMillis();addRecorder();},200));
 }
 private void afterRelease(int token,long started,Runnable ready){
  if(token!=generation||closed||!active)return;
  if(!service.canOperateGame()){end("已切离原神，"+soundAction.title+"操作取消");return;}
  if(service.gameControlReady()){ready.run();return;}
  if(SystemClock.uptimeMillis()-started>=1100){end("触控尚未成功释放，请重新开启无障碍服务");return;}
  handler.postDelayed(()->afterRelease(token,started,ready),16);
 }
 private void addRecorder(){
  if(!active||closed)return;Recorder view=new Recorder();overlay=view;WindowManager.LayoutParams p=params(-1,-1);p.flags|=WindowManager.LayoutParams.FLAG_FULLSCREEN;
  try{wm.addView(view,p);}catch(Exception e){end("无法显示录制层："+e.getMessage());}
 }
 private final class Recorder extends View {
  private final RecordedGesture.Capture capture=new RecordedGesture.Capture();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final long readyAt=SystemClock.uptimeMillis()+150;private boolean completeDown;
  Recorder(){super(service);postDelayed(this::invalidate,160);}
  private float left(){return Math.max(0,(getWidth()-dp(460))/2f);}
  private float[] point(MotionEvent e){int[] origin=new int[2];getLocationOnScreen(origin);return new float[]{e.getX()+origin[0],e.getY()+origin[1]};}
  @Override protected void onDraw(Canvas c){
   float l=left(),right=Math.min(getWidth(),l+dp(460));paint.setColor(0xeeffffff);c.drawRect(l,0,right,dp(80),paint);paint.setColor(Color.BLACK);paint.setTextSize(dp(15));
   String title=SystemClock.uptimeMillis()<readyAt?"正在准备，请稍候":firstOnly?"校正第1步：点游戏的正确位置":recording.isEmpty()?"点游戏的第一步（完整按下、松开）":soundAction.title+"录制：已记 "+recording.size()+" 步";
   c.drawText(title,l+dp(8),dp(24),paint);paint.setTextSize(dp(12));
   c.drawText(firstOnly?"只保存位置，后续步骤保留，不执行点击":soundAction.recordHint,l+dp(8),dp(50),paint);
   if(!firstOnly)c.drawText("保存",right-dp(110),dp(24),paint);c.drawText("取消",right-dp(52),dp(24),paint);
   if(firstOnly&&correction!=null){RecordedGesture a=correction.get(0);int[] origin=new int[2];getLocationOnScreen(origin);paint.setColor(0xffe67720);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));c.drawCircle(a.x1*width-origin[0],a.y1*height-origin[1],dp(12),paint);paint.setStyle(Paint.Style.FILL);}
  }
  @Override public boolean onTouchEvent(MotionEvent e){
   int action=e.getActionMasked();
   if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN){capture.cancel();completeDown=false;return true;}
   if(SystemClock.uptimeMillis()<readyAt){capture.cancel();completeDown=false;return true;}
   float[] xy=point(e);
   if(action==MotionEvent.ACTION_DOWN){capture.down(xy[0],xy[1],e.getPointerId(0),SystemClock.uptimeMillis());completeDown=true;return true;}
   if(action!=MotionEvent.ACTION_UP)return true;if(!completeDown){capture.cancel();return true;}completeDown=false;
   float l=left(),right=Math.min(getWidth(),l+dp(460));
   if(e.getY()<dp(80)&&e.getX()>=l&&e.getX()<=right){
    capture.cancel();if(e.getX()>right-dp(65)){end(firstOnly?"第一步校正已取消":"已取消录制");return true;}
    if(!firstOnly&&e.getX()>right-dp(125)){saveRecording();return true;}return true;
   }
   int[] size=service.screenSize();if(size[0]!=width||size[1]!=height){end("屏幕尺寸变化，请重新录制");return true;}
   final RecordedGesture a;
   try{a=capture.up(xy[0],xy[1],e.getPointerId(0),SystemClock.uptimeMillis(),lastStep,width,height,dp(6));}catch(Exception ex){toast("点击不在当前屏幕范围内，请重试");return true;}
   if(a==null)return true;
   if(firstOnly){
    if(a.x1!=a.x2||a.y1!=a.y2){toast("请点一下第一步的位置，不要拖动");return true;}
    try{correction.set(0,correction.get(0).at(a.x1*width,a.y1*height,width,height));save(correction);end(soundAction.title+"第1步已更新："+correction.get(0).position()+"；其余步骤保留");}catch(Exception ex){end("保存第一步失败："+ex.getMessage());}return true;
   }
   if(recording.size()>=40){toast("最多40步，请保存或取消");return true;}
   int token=generation;perform(a,()->{recording.add(a);lastStep=SystemClock.uptimeMillis();handler.postDelayed(()->{if(token==generation)addRecorder();},400);},()->end("转发失败，录制已停止；检查前台与无障碍服务"));return true;
  }
 }
 private void saveRecording(){
  if(recording.isEmpty()){toast("还没有录制操作");return;}
  try{save(recording);end(soundAction.title+"步骤已保存；第1步："+recording.get(0).position());}catch(Exception e){end("保存失败："+e.getMessage());}
 }
 public void muteGame(){execute(SoundAction.MUTE);}
 public void soundOnGame(){execute(SoundAction.SOUND_ON);}
 private void execute(SoundAction action){
  if(active||closed)return;if(!service.canOperateGame()){toast("先回到原神琴界面");return;}
  if(Config.prefs(service).getString(action.key("steps"),"").isEmpty()){configure(action);return;}
  if(!sameSavedSize(action)){toast("屏幕尺寸变化，请在声音设置中重新录制"+action.title);return;}
  try{
   ArrayList<RecordedGesture> steps=saved(action);firstOnly=false;prepare(action);
   afterRelease(generation,SystemClock.uptimeMillis(),()->{showReplayControls();replay(steps,0,generation);});
  }catch(Exception e){end(action.title+"操作无效："+e.getMessage());}
 }
 private void showReplayControls(){
  if(!active||closed||overlay!=null)return;
  LinearLayout row=new LinearLayout(service);row.setPadding(dp(8),0,0,0);row.setBackgroundColor(0xeeffffff);
  TextView title=new TextView(service);title.setText("执行"+soundAction.title+"；本地音源继续");title.setTextSize(12);row.addView(title,new LinearLayout.LayoutParams(0,-1,1));
  Button cancel=new Button(service);cancel.setText("取消");cancel.setOnClickListener(v->end("已取消；请手动返回琴界面"));row.addView(cancel);overlay=row;
  try{wm.addView(row,params(dp(380),dp(48)));}catch(Exception e){end("声音控制显示失败："+e.getMessage());}
 }
 private void replay(List<RecordedGesture> steps,int index,int token){
  if(token!=generation||closed||!active)return;
  if(index>=steps.size()){end("已执行"+soundAction.title+"步骤；"+soundAction.completeHint+"、回到琴界面后开启映射");return;}
  RecordedGesture a=steps.get(index);handler.postDelayed(()->{
   if(token!=generation||closed)return;perform(a,()->{showReplayControls();replay(steps,index+1,token);},()->end(soundAction.title+"中断，请检查当前界面"));
  },index==0?250:a.delay);
 }
 private void perform(RecordedGesture a,Runnable success,Runnable failure){
  // Controls are absent during every dispatched step, including the very first one.
  removeOverlay();int token=generation;
  handler.postDelayed(()->{
   if(token!=generation||closed||!active)return;int[] size=service.screenSize();
   if(!service.canOperateGame()||size[0]!=width||size[1]!=height){failure.run();return;}
   Path path=new Path();path.moveTo(a.x1*width,a.y1*height);if(a.x1!=a.x2||a.y1!=a.y2)path.lineTo(a.x2*width,a.y2*height);
   long request=guard.begin(SystemClock.uptimeMillis(),a.duration);
   Runnable timeout=()->{if(guard.expire(SystemClock.uptimeMillis())){service.releaseExternalTouch();failure.run();}};handler.postAtTime(timeout,guard.deadline());
   try{
    boolean accepted=service.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,a.duration)).build(),new AccessibilityService.GestureResultCallback(){
     @Override public void onCompleted(GestureDescription g){if(!guard.finish(request)||token!=generation||closed)return;handler.removeCallbacks(timeout);success.run();}
     @Override public void onCancelled(GestureDescription g){if(!guard.finish(request)||token!=generation||closed)return;handler.removeCallbacks(timeout);failure.run();}
    },handler);
    if(!accepted&&guard.finish(request)){handler.removeCallbacks(timeout);failure.run();}
   }catch(Exception ex){guard.finish(request);handler.removeCallbacks(timeout);failure.run();}
  },120);
 }
 public void cancel(){if(active)end("已取消"+soundAction.title+"操作");}
 private void end(String message){
  boolean touching=guard.busy();guard.cancel();generation++;active=false;handler.removeCallbacksAndMessages(null);removeOverlay();
  if(touching)service.releaseExternalTouch();if(!closed){service.finishGameControl();toast(message);}
 }
 public void close(){closed=true;if(dialog!=null)dialog.dismiss();end("");}
}
