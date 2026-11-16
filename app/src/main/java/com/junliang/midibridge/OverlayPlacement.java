package com.junliang.midibridge;

/** Screen positions shared by the draggable ball and its temporary panel. */
public final class OverlayPlacement {
 public static final class Position {
  public final int x,y;public Position(int x,int y){this.x=x;this.y=y;}
 }
 public static int clamp(int n,int low,int high){return Math.max(low,Math.min(Math.max(low,high),n));}
 public static Position ball(float nx,float ny,int width,int height,int size,int margin){
  int maxX=Math.max(margin,width-size-margin),maxY=Math.max(margin,height-size-margin);
  if(!Float.isFinite(nx)||!Float.isFinite(ny))throw new IllegalArgumentException("ball position");
  return new Position(clamp(Math.round(nx*Math.max(0,width-size)),margin,maxX),clamp(Math.round(ny*Math.max(0,height-size)),margin,maxY));
 }
 public static Position panel(int ballX,int ballY,int ballSize,int panelWidth,int panelHeight,int width,int height,int margin){
  int x=ballX>=width/2?ballX-panelWidth-margin:ballX+ballSize+margin;
  return new Position(clamp(x,margin,width-panelWidth-margin),clamp(ballY-panelHeight/2,margin,height-panelHeight-margin));
 }
 public static boolean covers(int left,int top,int width,int height,float x,float y,int margin){return x>=left-margin&&x<left+width+margin&&y>=top-margin&&y<top+height+margin;}
 public static Position avoidKeys(Position current,int screenWidth,int screenHeight,int size,int margin,float[] xs,float[] ys){
  if(clear(current,size,margin,xs,ys))return current;
  float[][] candidates={{1,.5f},{0,.5f},{1,0},{0,0},{.5f,0},{1,1},{0,1},{.5f,1}};
  for(float[] c:candidates){Position p=ball(c[0],c[1],screenWidth,screenHeight,size,margin);if(clear(p,size,margin,xs,ys))return p;}
  return null;
 }
 private static boolean clear(Position p,int size,int margin,float[] xs,float[] ys){
  if(xs.length!=ys.length)throw new IllegalArgumentException("keys");
  for(int k=0;k<xs.length;k++)if(covers(p.x,p.y,size,size,xs[k],ys[k],margin))return false;return true;
 }
}
