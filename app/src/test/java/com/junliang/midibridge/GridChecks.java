package com.junliang.midibridge;
public final class GridChecks {
 private static int checks;
 private static void equal(float a,float b,String why){checks++;if(Math.abs(a-b)>0.002f)throw new AssertionError(why+": "+a+" vs "+b);}
 private static void rejects(float[] x,float[] y,int mode,int w,int h,String why){checks++;try{GridCalibration.calculate(x,y,mode,w,h);}catch(IllegalArgumentException ok){return;}throw new AssertionError(why);}
 public static void main(String[] args){run();}
 public static void run(){checks=0;
  for(int mode=0;mode<3;mode++){
   int[] anchors=GridCalibration.anchors(mode);float[] x=new float[anchors.length],y=new float[anchors.length];
   for(int i=0;i<anchors.length;i++){int row=anchors[i]/7,col=anchors[i]%7;x[i]=300+col*70-row*4;y[i]=900+col*2-row*160;}
   float[][] points=GridCalibration.calculate(x,y,mode,1200,1200);
   for(int i=0;i<21;i++){int row=i/7,col=i%7;equal(points[i][0],300+col*70-row*4,"grid x mode "+mode);equal(points[i][1],900+col*2-row*160,"grid y mode "+mode);}
  }
  // Endpoints spread measurement error over six columns and two row gaps.
  float[][] robust=GridCalibration.calculate(new float[]{300,723,292},new float[]{900,912,580},1,1200,1200);equal(robust[1][0],370.5f,"endpoint error divided across six columns");equal(robust[7][1],740,"row interpolation");
  rejects(new float[]{300,290,300},new float[]{900,900,600},1,1200,1200,"reversed horizontal direction");
  rejects(new float[]{300,720,300},new float[]{600,600,900},1,1200,1200,"reversed octave rows");
  rejects(new float[]{Float.NaN,720,300},new float[]{900,900,600},1,1200,1200,"NaN");
  rejects(new float[]{300,370,300,720,300},new float[]{900,902,400,912,580},2,1200,1200,"near/far row disagreement");
  rejects(new float[]{1000,1250,1000},new float[]{900,900,600},1,1200,1200,"offscreen right endpoint");
  System.out.println("PASS: "+checks+" grid calibration assertions");
 }
}
