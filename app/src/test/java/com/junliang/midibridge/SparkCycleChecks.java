package com.junliang.midibridge;

/** Temporal behavior checks, not Android rendering or device frame-rate measurements. */
public final class SparkCycleChecks {
 private static int checks;
 private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 public static void run(){
  checks=0;
  for(int cycle=0;cycle<6;cycle++){
   check(SparkCycle.brightness(SparkCycle.phase(cycle*4+1.2))>.99f,"both kinds of particle reach full brightness at the middle of their light interval");
   for(double t:new double[]{0,2.4,3.2,4})check(SparkCycle.brightness(SparkCycle.phase(cycle*4+t))==0,"gaps and cycle boundaries are fully dark");
  }
  for(float offset:new float[]{0,.7f,2.1f,4.8f,7.6f}){
   check(SparkCycle.brightness(SparkCycle.phase(1.2+offset))==SparkCycle.brightness(SparkCycle.phase(17.2+offset)),"per-particle phase offsets repeat without changing envelope");
  }
  float before=0;
  for(int i=0;i<=12;i++){
   float brightness=SparkCycle.brightness(SparkCycle.phase(i*.1));check(brightness>=before,"light fades in gradually");before=brightness;
  }
  for(int i=13;i<=24;i++){
   float brightness=SparkCycle.brightness(SparkCycle.phase(i*.1));check(brightness<=before,"light fades out gradually");before=brightness;
  }
  for(int i=0;i<800;i++){
   float p=SparkCycle.phase(i*.01),brightness=SparkCycle.brightness(p);
   check(Float.isFinite(brightness)&&brightness>=0&&brightness<=1,"brightness remains in its valid range");
   if(p>=2.4f)check(brightness==0,"dark windows have no residual sprite or glow");
  }
  check(SparkCycle.phase(-.5)==3.5f&&SparkCycle.phase(86401.2)==SparkCycle.phase(1.2),"phase normalization handles long elapsed time");
  System.out.println("Spark glow-cycle checks passed: "+checks);
 }
 public static void main(String[] args){run();}
}
