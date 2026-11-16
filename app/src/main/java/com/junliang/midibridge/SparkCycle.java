package com.junliang.midibridge;

/** Each sprite fades in/out and goes fully dark, with independent phase offsets. */
final class SparkCycle {
 static final float PERIOD=4,LIT_TIME=2.4f;
 private SparkCycle(){}
 static float phase(double seconds){double p=seconds%PERIOD;return (float)(p<0?p+PERIOD:p);}
 static float brightness(float phase){
  float p=phase%PERIOD;if(p<=0||p>=LIT_TIME)return 0;
  float wave=(float)Math.sin(Math.PI*p/LIT_TIME);return wave*wave;
 }
}
