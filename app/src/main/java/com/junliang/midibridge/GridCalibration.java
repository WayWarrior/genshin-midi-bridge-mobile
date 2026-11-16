package com.junliang.midibridge;
/** Affine 3x7 grid; row zero is the bottom octave. No Android dependencies. */
public final class GridCalibration {
 public static final int QUICK=0,ROBUST=1,CHECKED=2;
 public static int[] anchors(int mode){return mode==QUICK?new int[]{0,1,7}:mode==ROBUST?new int[]{0,6,14}:new int[]{0,1,7,6,14};}
 public static float[][] calculate(float[] x,float[] y,int mode,int width,int height){
  int needed=anchors(mode).length;if(x.length<needed||y.length<needed||width<=0||height<=0)throw new IllegalArgumentException("校准点不足");
  for(int i=0;i<needed;i++)if(!Float.isFinite(x[i])||!Float.isFinite(y[i]))throw new IllegalArgumentException("坐标无效");
  int colEnd=mode==CHECKED?3:1,rowEnd=mode==CHECKED?4:2;
  float colDiv=mode==QUICK?1:6,rowDiv=mode==QUICK?1:2;
  float dx=(x[colEnd]-x[0])/colDiv,dy=(y[colEnd]-y[0])/colDiv;
  float rx=(x[rowEnd]-x[0])/rowDiv,ry=(y[rowEnd]-y[0])/rowDiv;
  if(dx<=width*0.015f || ry>=-height*0.03f)throw new IllegalArgumentException("点位方向不对：C3在下排左侧，B3在下排右侧，C5在上排左侧");
  if(mode==CHECKED){
   double colError=Math.hypot(x[1]-(x[0]+dx),y[1]-(y[0]+dy));
   double rowError=Math.hypot(x[2]-(x[0]+rx),y[2]-(y[0]+ry));
   double tolerance=Math.max(12,Math.min(Math.hypot(dx,dy),Math.hypot(rx,ry))*0.2);
   if(colError>tolerance || rowError>tolerance)throw new IllegalArgumentException("近距/远距点位不一致，请重新点琴键中心");
  }
  float[][] points=new float[21][2];
  for(int row=0;row<3;row++)for(int col=0;col<7;col++){
   int i=row*7+col;float px=x[0]+col*dx+row*rx,py=y[0]+col*dy+row*ry;
   if(px<0||px>=width||py<0||py>=height)throw new IllegalArgumentException("推导的琴键超出屏幕，请重测");
   points[i][0]=px;points[i][1]=py;
  }
  return points;
 }
}
