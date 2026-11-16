package com.junliang.midibridge;

import java.util.*;
public final class OverlayChecks {
 private static int checks;
 private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static void close(float got,float expected,String why){check(Math.abs(got-expected)<.0001f,why);}
 public static void run(){
  checks=0;
  int[][] screens={{2560,1600},{1600,2560},{1920,1080},{1280,800},{800,480},{320,240}};
  for(int[] s:screens)for(float nx:new float[]{-1,0,.25f,.5f,1,2})for(float ny:new float[]{-1,0,.5f,1,2}){
   int width=s[0],height=s[1];OverlayPlacement.Position ball=OverlayPlacement.ball(nx,ny,width,height,36,8);
   check(ball.x>=8&&ball.x+36<=width-8&&ball.y>=8&&ball.y+36<=height-8,"drag stays on screen");
   float savedX=ball.x/(float)(width-36),savedY=ball.y/(float)(height-36);OverlayPlacement.Position restore=OverlayPlacement.ball(savedX,savedY,width,height,36,8);
   check(ball.x==restore.x&&ball.y==restore.y,"remember position without mirroring");
   OverlayPlacement.Position panel=OverlayPlacement.panel(ball.x,ball.y,36,Math.min(272,width-16),Math.min(320,height-16),width,height,8);
   check(panel.x>=8&&panel.y>=8&&panel.x+Math.min(272,width-16)<=width-8&&panel.y+Math.min(320,height-16)<=height-8,"eleven-action panel inside screen");
  }
  float[] xs={70,120,170,220,270,320,370},ys={420,420,420,420,420,420,420};
  OverlayPlacement.Position initial=new OverlayPlacement.Position(60,410);OverlayPlacement.Position avoid=OverlayPlacement.avoidKeys(initial,800,480,36,8,xs,ys);
  check(avoid!=null&&avoid!=initial,"ball moves away from calibrated notes");
  for(int k=0;k<xs.length;k++)check(!OverlayPlacement.covers(avoid.x,avoid.y,36,36,xs[k],ys[k],8),"new ball position misses every note");
  check(OverlayPlacement.avoidKeys(avoid,800,480,36,8,xs,ys)==avoid,"clear user position is retained");

  RecordedGesture.Capture capture=new RecordedGesture.Capture();
  check(capture.up(1900,900,0,100,0,2560,1600,6)==null,"launch dialog's orphan up cannot become first action");
  capture.down(40,48,3,1000);RecordedGesture first=capture.up(41,49,3,1050,500,2560,1600,6);
  close(first.x1,40f/2560,"left top recorded from its down point");close(first.y1,48f/1600,"screen Y normalization");
  check(first.x1==first.x2&&first.y1==first.y2&&first.duration==24,"tap has stationary path");
  check(first.x1*2560<100&&first.position().startsWith("左上"),"replay stays left, never horizontally mirrored");
  // Local points are converted to screen points by adding the view origin, exactly once.
  capture.down(12+30,18+40,0,2000);RecordedGesture offset=capture.up(12+30,18+40,0,2040,1600,800,480,6);
  close(offset.x1*800,42,"view origin applied once in X");close(offset.y1*480,58,"view origin applied once in Y");
  capture.down(80,80,0,3000);capture.cancel();check(capture.up(90,90,0,3100,2600,800,480,6)==null,"cancelled touch discarded");
  capture.down(80,80,2,4000);check(capture.up(90,90,1,4100,3600,800,480,6)==null,"different finger cannot finish recorded touch");
  capture.down(80,80,0,5000);check(capture.up(90,90,0,4900,4500,800,480,6)==null,"invalid reversed time discarded");
  capture.down(80,80,0,6000);RecordedGesture drag=capture.up(250,90,0,6400,5600,800,480,6);
  check(drag.duration==400&&drag.x2>drag.x1&&drag.delay==400,"valid drag and timing retained");
  RecordedGesture[] original={new RecordedGesture(.97f,.04f,.97f,.04f,24,400),new RecordedGesture(.7f,.3f,.7f,.3f,24,1100),new RecordedGesture(.8f,.6f,.2f,.6f,600,1400)};
  RecordedGesture[] corrected=original.clone();corrected[0]=corrected[0].at(30,40,800,480);
  check(corrected[0].x1<.1f&&corrected[0].y1<.1f,"only first point can be repaired to left top");
  check(corrected[1]==original[1]&&corrected[2]==original[2],"all later steps remain identical");
  check(corrected[0].duration==original[0].duration&&corrected[0].delay==original[0].delay,"repair retains first-step timing");
  check(original[0].x1>.9f,"correction does not mutate old data before save");
  try{new RecordedGesture(Float.NaN,0,0,0,24,400);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){checks++;}
  try{new RecordedGesture(1.01f,0,0,0,24,400);throw new AssertionError("offscreen accepted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("Overlay/recording checks passed: "+checks);
 }
 public static void main(String[] args){run();}
}
