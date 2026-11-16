package com.junliang.midibridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;

/** Runs only on a full-screen consuming test surface. No touches are sent to the game. */
final class TouchReuseTest {
 interface Listener {void finished(boolean passed,String detail,boolean released);}
 final TestView view;
 private final TouchService service;private final Handler main;private final Listener listener;
 private final TouchReuseScenario scenario=new TouchReuseScenario();private final TouchProbeTrace trace=new TouchProbeTrace();
 private final LiveTouchSlots slots=new LiveTouchSlots();private final GestureDispatchGuard guard=new GestureDispatchGuard();
 private final HashMap<Integer,LiveGestureBatch.Held> held=new HashMap<>();
 private final float[] xs=new float[21],ys=new float[21];private final int[] origin=new int[2];
 private int width,height;private boolean done,ending,started;private String failure="";private long drainDeadline;
 private final Runnable next=this::step,watchdog=this::timeout,hardTimeout=()->abort("检测超时，请松手后重试"),drain=this::checkReceived;
 TouchReuseTest(TouchService service,Handler main,Listener listener){this.service=service;this.main=main;this.listener=listener;view=new TestView();}
 void start(){main.postDelayed(next,350);main.postDelayed(hardTimeout,10000);}
 private void step(){
  if(done||ending||guard.busy())return;
  if(view.getWidth()==0||view.getHeight()==0){main.postDelayed(next,30);return;}
  int[] now=new int[2];view.getLocationOnScreen(now);
  if(!started){width=view.getWidth();height=view.getHeight();origin[0]=now[0];origin[1]=now[1];
   if(width<service.getResources().getDisplayMetrics().density*180||height<service.getResources().getDisplayMetrics().density*160){abort("检测区域太小，请关闭分屏后重试");return;}
   for(int k=0;k<21;k++){xs[k]=origin[0]+(k%7+0.5f)*width/7;ys[k]=origin[1]+(0.4f+(k/7)*0.18f)*height;}started=true;
  }else if(width!=view.getWidth()||height!=view.getHeight()||now[0]!=origin[0]||now[1]!=origin[1]){abort("检测期间屏幕发生变化，请重新检测");return;}
  if(!trace.error().isEmpty()){abort(trace.error());return;}
  final LiveTouchPlan plan;final LiveTouchSlots.Batch allocation;final LiveGestureBatch batch;
  try{
   if(!scenario.next()){drainDeadline=SystemClock.uptimeMillis()+250;checkReceived();return;}
   long[] previous=new long[21];for(Integer key:held.keySet())previous[key]=held.get(key).version;
   plan=LiveTouchPlan.create(scenario.state,previous,service.fileStrokeLimit(),service.filePointerLimit());
   allocation=slots.plan(plan);batch=new LiveGestureBatch(plan,allocation,held,xs,ys,origin[0]+width);
  }catch(Exception|LinkageError e){abort("此系统无法创建复用触控："+e.getClass().getSimpleName());return;}
  view.invalidate();final long token=guard.begin(SystemClock.uptimeMillis(),plan.duration);main.postAtTime(watchdog,guard.deadline());
  try{
   boolean accepted=service.dispatchGesture(batch.gesture,new AccessibilityService.GestureResultCallback(){
    @Override public void onCompleted(GestureDescription g){
     if(!guard.finish(token)||done||ending)return;main.removeCallbacks(watchdog);
     if(!batch.serialized()){abort("此系统未采用复用触点编号");return;}
     if(!slots.complete(allocation)){abort("检测触控状态已经变化，请重试");return;}
     plan.acknowledge(scenario.state);held.clear();held.putAll(batch.tail);main.postDelayed(next,8);
    }
    @Override public void onCancelled(GestureDescription g){if(!guard.finish(token)||done||ending)return;main.removeCallbacks(watchdog);abort("检测被系统或手动触屏打断，请松手后重试");}
   },main);
   if(!accepted){guard.finish(token);abort("系统拒绝长按检测，请确认无障碍已开启");}
   else if(!batch.serialized()){guard.cancel();abort("此系统未采用复用触点编号");}
  }catch(Exception|LinkageError e){guard.cancel();abort("系统无法执行复用触控："+e.getClass().getSimpleName());}
 }
 private void checkReceived(){
  if(done||ending)return;
  if(trace.complete(scenario.downs,scenario.ups)){finish(true,"检测通过：保持音未重新起音，21键轮换与10键换组正常",true);return;}
  if(!trace.error().isEmpty()){abort(trace.error());return;}
  if(SystemClock.uptimeMillis()>=drainDeadline){abort("未收到完整触控序列，请松手后重试");return;}
  main.postDelayed(drain,20);
 }
 private void timeout(){if(!guard.expire(SystemClock.uptimeMillis()))return;if(ending)finish(false,failure+"；触控释放未获确认，请点重置触控",false);else abort("系统未回报检测完成，请重试");}
 void abort(String reason){
  if(done||ending)return;ending=true;failure=reason;clearTimers();guard.cancel();slots.clear();held.clear();view.invalidate();
  if(!started){finish(false,failure,true);return;}
  // Cancel the retained chain with a closed ordinary stroke, still inside the consuming test view.
  Path path=new Path();path.moveTo(origin[0]+Math.max(1,view.getWidth())/2f,origin[1]+Math.max(1,view.getHeight())/2f);
  final long token=guard.begin(SystemClock.uptimeMillis(),24);main.postAtTime(watchdog,guard.deadline());
  try{
   GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,24,false)).build();
   if(!service.dispatchGesture(gesture,new AccessibilityService.GestureResultCallback(){
    @Override public void onCompleted(GestureDescription g){if(!guard.finish(token)||done)return;main.removeCallbacks(watchdog);main.postDelayed(()->finish(false,failure,true),80);}
    @Override public void onCancelled(GestureDescription g){if(guard.finish(token)&&!done)finish(false,failure,false);}
   },main)){guard.finish(token);finish(false,failure,false);}
  }catch(Exception|LinkageError e){guard.cancel();finish(false,failure,false);}
 }
 private void clearTimers(){main.removeCallbacks(next);main.removeCallbacks(watchdog);main.removeCallbacks(hardTimeout);main.removeCallbacks(drain);}
 private void finish(boolean passed,String detail,boolean released){if(done)return;done=true;clearTimers();guard.cancel();listener.finished(passed,detail,released);}
 void dispose(){done=true;clearTimers();guard.cancel();slots.clear();held.clear();}
 final class TestView extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  TestView(){super(service);setBackgroundColor(GardenStyle.BACKGROUND);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
  private int dp(int v){return GardenStyle.dp(service,v);}
  @Override protected void onDraw(Canvas c){
   paint.setColor(GardenStyle.INK);paint.setTextSize(dp(19));c.drawText("长按兼容检测",dp(18),dp(30),paint);
   paint.setTextSize(dp(13));paint.setColor(GardenStyle.MUTED);c.drawText("请暂时不要触摸屏幕，约3秒后自动返回",dp(18),dp(55),paint);
   c.drawText(ending?"正在结束检测并松键…":"检测进度："+scenario.index()+" / "+scenario.size(),dp(18),dp(79),paint);
   if(started)for(int k=0;k<21;k++){paint.setColor(scenario.state.held(k)?GardenStyle.GREEN:0xffdfe9d8);c.drawCircle(xs[k]-origin[0],ys[k]-origin[1],Math.min(dp(16),getWidth()/24f),paint);}
   paint.setColor(GardenStyle.GREEN);paint.setTextSize(dp(14));c.drawText("取消",getWidth()-dp(48),dp(30),paint);
  }
  @Override public boolean onTouchEvent(MotionEvent e){
   if(done||ending)return true;
   if(e.getActionMasked()==MotionEvent.ACTION_UP&&e.getY()<dp(40)&&e.getX()>getWidth()-dp(80)){abort("已取消检测，可随时重新检测");return true;}
   if(!started)return true;
   int n=e.getPointerCount();int[] ids=new int[n],keys=new int[n];
   for(int i=0;i<n;i++){
    ids[i]=e.getPointerId(i);float x=e.getX(i)+origin[0],y=e.getY(i)+origin[1];keys[i]=-1;
    for(int k=0;k<21;k++)if(Math.abs(x-xs[k])<=dp(12)&&Math.abs(y-ys[k])<=dp(12)){keys[i]=k;break;}
   }
   if(!trace.accept(e.getActionMasked(),e.getActionIndex(),ids,keys)){main.post(()->abort(trace.error()));}
   else if(trace.rootReleased()&&scenario.index()<scenario.size())main.post(()->abort("保持中的低音被提前松开"));
   return true;
  }
 }
}
