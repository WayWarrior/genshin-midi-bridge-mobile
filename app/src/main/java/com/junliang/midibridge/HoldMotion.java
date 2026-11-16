package com.junliang.midibridge;

/** A stationary continuation can generate zero MotionEvents in AOSP. Move inside the same key. */
public final class HoldMotion {
 public final float startX,startY,endX,endY;
 private HoldMotion(float x,float y,float end){startX=x;startY=y;endX=end;endY=y;}
 public static HoldMotion create(float centerX,float centerY,float previousX,float previousY,boolean continuing,boolean willContinue,int width){
  float x=continuing?previousX:centerX,y=continuing?previousY:centerY,end=x;
  if(willContinue){float other=centerX+(centerX+1<width?1:-1);end=Math.round(x)==Math.round(centerX)?other:centerX;}
  return new HoldMotion(x,y,end);
 }
 public boolean moved(){return Math.round(startX)!=Math.round(endX)||Math.round(startY)!=Math.round(endY);}
}
