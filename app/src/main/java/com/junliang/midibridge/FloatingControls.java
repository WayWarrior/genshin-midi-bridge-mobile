package com.junliang.midibridge;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Small draggable ball; every expanded-menu action runs after game touches are released. */
final class FloatingControls {
 private final Context context;private final WindowManager wm;private final Handler main=new Handler(Looper.getMainLooper());
 private final Runnable pause,finishTouch;private final BooleanSupplier ready;
 private final Supplier<String[]> labelSource;private final Runnable[] actions;
 private final int toggleIndex;
 private Ball ball;private View panel;private Button toggleButton;private WindowManager.LayoutParams ballParams;
 private int width,height;private boolean interacting,recovering,hidden,closed,opening,active;
 private long openStarted;private final Runnable waitOpen=this::waitForOpen;
 FloatingControls(Context c,WindowManager manager,Runnable pause,Runnable finishTouch,BooleanSupplier ready,Supplier<String[]> labels,Runnable[] actions,int toggleIndex){
  context=c;wm=manager;this.pause=pause;this.finishTouch=finishTouch;this.ready=ready;this.labelSource=labels;this.actions=actions;this.toggleIndex=toggleIndex;
 }
 private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 private void size(){android.util.DisplayMetrics d=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(d);width=d.widthPixels;height=d.heightPixels;}
 private WindowManager.LayoutParams params(int w,int h){
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.LEFT;
  if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
  return p;
 }
 void show(){
  if(closed)return;hidden=false;if(ball!=null)return;size();int side=dp(36);
  android.content.SharedPreferences p=Config.prefs(context);OverlayPlacement.Position pos=OverlayPlacement.ball(p.getFloat("ball_x",1),p.getFloat("ball_y",.45f),width,height,side,dp(8));
  ball=new Ball();ballParams=params(side,side);ballParams.x=pos.x;ballParams.y=pos.y;
  try{wm.addView(ball,ballParams);}catch(Exception e){ball=null;toast("悬浮球显示失败："+e.getMessage());}
 }
 void hide(){hidden=true;collapse();if(ball!=null){ball.cancelPress();try{wm.removeView(ball);}catch(Exception ignored){}ball=null;}}
 void close(){closed=true;hide();main.removeCallbacksAndMessages(null);}
 void refreshSize(){
  int oldWidth=width,oldHeight=height;size();if(ball==null||(oldWidth==width&&oldHeight==height))return;
  collapse();ball.cancelPress();android.content.SharedPreferences prefs=Config.prefs(context);
  OverlayPlacement.Position pos=OverlayPlacement.ball(prefs.getFloat("ball_x",1),prefs.getFloat("ball_y",.45f),width,height,ballParams.width,dp(8));ballParams.x=pos.x;ballParams.y=pos.y;
  try{wm.updateViewLayout(ball,ballParams);}catch(Exception e){hide();show();}
 }
 boolean interacting(){return interacting;}
 void setRecovery(boolean value){recovering=value;if(value&&panel!=null)collapse();if(ball!=null)ball.invalidate();}
 void setActive(boolean value){active=value;collapse();if(ball!=null)ball.invalidate();}
 void collapse(){opening=false;main.removeCallbacks(waitOpen);toggleButton=null;if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;}}
 void refreshToggleLabel(){if(toggleButton!=null)toggleButton.setText(labelSource.get()[toggleIndex]);}
 boolean covers(float x,float y){
  if(ball==null)return false;int[] origin=new int[2];ball.getLocationOnScreen(origin);
  return OverlayPlacement.covers(origin[0],origin[1],ball.getWidth(),ball.getHeight(),x,y,dp(6));
 }
 boolean avoidKeys(float[] xs,float[] ys){
  if(ball==null)return true;size();OverlayPlacement.Position pos=OverlayPlacement.avoidKeys(new OverlayPlacement.Position(ballParams.x,ballParams.y),width,height,ballParams.width,dp(8),xs,ys);
  if(pos==null)return false;if(pos.x!=ballParams.x||pos.y!=ballParams.y){ballParams.x=pos.x;ballParams.y=pos.y;wm.updateViewLayout(ball,ballParams);}return true;
 }
 private void savePosition(){Config.prefs(context).edit().putFloat("ball_x",ballParams.x/(float)Math.max(1,width-ballParams.width)).putFloat("ball_y",ballParams.y/(float)Math.max(1,height-ballParams.height)).apply();}
 private void open(){
  if(closed||hidden)return;if(panel!=null){collapse();return;}
  pause.run();opening=true;openStarted=SystemClock.uptimeMillis();main.post(waitOpen);
 }
 private void waitForOpen(){
  if(!opening||closed||hidden)return;
  if(recovering||!ready.getAsBoolean()){
   if(SystemClock.uptimeMillis()-openStarted>=1100){opening=false;toast("触控尚未成功释放，请检查琴键状态或重新开启无障碍服务");return;}
   main.postDelayed(waitOpen,16);return;
  }
  opening=false;showPanel();
 }
 private void showPanel(){
  String[] labels=labelSource.get();
  if(ball==null||panel!=null)return;size();LinearLayout list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(8),dp(8),dp(8),dp(8));
  list.setBackground(GardenStyle.shape(context,0xfff9fcf5,0xffd7e6cd,18));
  TextView title=new TextView(context);title.setText("✿  原琴小花园");title.setTextSize(13);title.setTextColor(GardenStyle.GREEN);title.setGravity(Gravity.CENTER_VERTICAL);list.addView(title,new LinearLayout.LayoutParams(-1,dp(28)));
  for(int i=0;i<labels.length;i+=2){LinearLayout row=new LinearLayout(context);
   for(int j=i;j<Math.min(labels.length,i+2);j++){final int index=j;Button b=new Button(context);b.setText(labels[j]);if(index==toggleIndex)toggleButton=b;GardenStyle.button(b,index==0?1:0);b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(2),0,dp(2),0);b.setOnClickListener(v->{if(recovering)return;if(index==toggleIndex){actions[index].run();b.setText(labelSource.get()[index]);return;}collapse();main.postDelayed(actions[index],80);});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(42),1);bp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(b,bp);}
   list.addView(row);
  }
  ScrollView scroll=new ScrollView(context);scroll.addView(list);panel=scroll;
  int panelWidth=Math.min(dp(272),width-dp(16)),panelHeight=Math.min(dp(44+46*((labels.length+1)/2)),height-dp(16));
  OverlayPlacement.Position pos=OverlayPlacement.panel(ballParams.x,ballParams.y,ballParams.width,panelWidth,panelHeight,width,height,dp(8));WindowManager.LayoutParams p=params(panelWidth,panelHeight);p.x=pos.x;p.y=pos.y;
  try{wm.addView(panel,p);}catch(Exception e){panel=null;toast("菜单显示失败："+e.getMessage());}
 }
 private void toast(String text){Toast.makeText(context,text,Toast.LENGTH_LONG).show();}
 private final class Ball extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private float downX,downY;private int initialX,initialY;private boolean dragged,wasOpen;
  Ball(){super(context);setContentDescription("原琴控制：拖动移动，点按展开");}
  void cancelPress(){if(interacting){interacting=false;finishTouch.run();}}
  @Override protected void onDraw(Canvas c){
   float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(1);paint.setColor(recovering?0xddc9cfbf:active?0xee6e995e:0xeef4dce3);c.drawCircle(cx,cy,r,paint);
   paint.setColor(active?Color.WHITE:GardenStyle.GREEN);paint.setTextSize(dp(23));paint.setTextAlign(Paint.Align.CENTER);c.drawText("✿",cx,cy-(paint.ascent()+paint.descent())/2,paint);
  }
  @Override public boolean performClick(){super.performClick();open();return true;}
  @Override public boolean onTouchEvent(MotionEvent e){
   if(recovering)return true;int action=e.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){
    interacting=true;dragged=false;wasOpen=panel!=null||opening;downX=e.getRawX();downY=e.getRawY();initialX=ballParams.x;initialY=ballParams.y;collapse();pause.run();return true;
   }
   if(action==MotionEvent.ACTION_MOVE&&interacting){
    float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(Math.hypot(dx,dy)>ViewConfiguration.get(context).getScaledTouchSlop())dragged=true;
    if(dragged){size();ballParams.x=OverlayPlacement.clamp(initialX+Math.round(dx),dp(8),width-getWidth()-dp(8));ballParams.y=OverlayPlacement.clamp(initialY+Math.round(dy),dp(8),height-getHeight()-dp(8));wm.updateViewLayout(this,ballParams);}return true;
   }
   if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&interacting){
    boolean click=action==MotionEvent.ACTION_UP&&!dragged&&!wasOpen;interacting=false;if(dragged)savePosition();finishTouch.run();if(click)performClick();return true;
   }
   return true;
  }
 }
}
