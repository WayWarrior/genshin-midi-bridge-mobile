package com.junliang.midibridge;

/** Absolute screen coordinates normalized once; no mirroring or toolbar-relative offset. */
public final class RecordedGesture {
 public final float x1,y1,x2,y2;public final int duration,delay;
 public RecordedGesture(float x1,float y1,float x2,float y2,int duration,int delay){
  if(!valid(x1)||!valid(y1)||!valid(x2)||!valid(y2)||duration<24||duration>1000||delay<400||delay>2000)throw new IllegalArgumentException("操作数据无效");
  this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.duration=duration;this.delay=delay;
 }
 private static boolean valid(float n){return Float.isFinite(n)&&n>=0&&n<=1;}
 public RecordedGesture at(float screenX,float screenY,int width,int height){
  if(width<=0||height<=0)throw new IllegalArgumentException("屏幕尺寸无效");
  return new RecordedGesture(screenX/width,screenY/height,screenX/width,screenY/height,duration,delay);
 }
 public String position(){return (x1<.5f?"左":"右")+(y1<.5f?"上":"下")+"；距左 "+Math.round(x1*100)+"%，距顶 "+Math.round(y1*100)+"%";}
 public static final class Capture {
  private boolean down;private int pointer;private float x,y;private long time;
  public void down(float screenX,float screenY,int id,long now){down=true;x=screenX;y=screenY;pointer=id;time=now;}
  public void cancel(){down=false;}
  public RecordedGesture up(float screenX,float screenY,int id,long now,long lastStep,int width,int height,float dragThreshold){
   boolean valid=down&&pointer==id&&now>=time;down=false;if(!valid)return null;
   if(width<=0||height<=0)throw new IllegalArgumentException("屏幕尺寸无效");
   boolean drag=Math.hypot(screenX-x,screenY-y)>dragThreshold;
   int duration=drag?(int)Math.max(200,Math.min(1000,now-time)):24;
   int delay=(int)Math.max(400,Math.min(2000,time-lastStep));
   return new RecordedGesture(x/width,y/height,(drag?screenX:x)/width,(drag?screenY:y)/height,duration,delay);
  }
 }
}
