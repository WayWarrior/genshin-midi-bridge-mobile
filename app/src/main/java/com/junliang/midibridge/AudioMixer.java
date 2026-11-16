package com.junliang.midibridge;
import java.util.Arrays;

/** Pure Java DSP with all voices preallocated; independent of Android for testing. */
final class AudioMixer implements FileAudioSequence.Sink {
 static final int VOICES=48;
 private static final int TABLE_SIZE=4096;
 private static final float[] WAVE=new float[TABLE_SIZE];
 static {for(int i=0;i<TABLE_SIZE;i++){double p=2*Math.PI*i/TABLE_SIZE;WAVE[i]=(float)(Math.sin(p)+0.3*Math.sin(2*p)+0.12*Math.sin(3*p));}}
 private static final class Voice {
  boolean active,held;int id,channel;long age;
  double pos,phase,phaseStep,sampleStep;
  float env,gain,decay;WavData wav;
 }
 private final Voice[] voices=new Voice[VOICES];
 private final boolean[] pedals=new boolean[16];
 private final AudioCommandQueue queue;
 private final int rate;
 private final float wavAttack,synthAttack,release,synthDecay;
 private long serial;
 private volatile WavData[] samples=new WavData[128];
 private volatile float volume=0.65f;
 private volatile boolean oneShot=true,muted;
 private volatile FileAudioSequence fileSequence;private float[] renderingBlock;
 volatile int activeVoices;
 AudioMixer(int sampleRate,AudioCommandQueue commands) {
  rate=sampleRate;queue=commands;
  wavAttack=1f/Math.max(1,rate*0.0005f);synthAttack=1f/(rate*0.003f);
  release=(float)Math.exp(-1.0/(rate*0.035));synthDecay=(float)Math.exp(-1.0/(rate*1.7));
  for(int i=0;i<VOICES;i++)voices[i]=new Voice();
 }
 void samples(WavData[] bank){samples=bank;}
 void volume(float value){volume=Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
 void muted(boolean enabled){muted=enabled;}
 void oneShot(boolean enabled){oneShot=enabled;}
 void fileSequence(FileAudioSequence sequence){fileSequence=sequence;}
 private void deactivate(Voice voice){voice.active=false;voice.wav=null;}
 private void commands() {
  for(int count=0;count<512;count++) {
   long event=queue.poll();if(event<0)break;
   command((int)(event>>>32),(int)((event>>>16)&65535),(int)(event&65535));
  }
 }
 private void command(int kind,int id,int value){
   int channel=id/128;
   if(kind==3){for(Voice v:voices)deactivate(v);Arrays.fill(pedals,false);return;}
   if(channel>=16)return;
   if(kind==4){pedals[channel]=value>=64;return;}
   if(kind==2){for(Voice v:voices)if(v.active && v.id==id)v.held=false;return;}
   if(kind!=1)return;
   int note=value>>>8,velocity=value&255;if(note>=128 || velocity<1 || velocity>127)return;
   Voice slot=voices[0];
   for(Voice v:voices){if(!v.active){slot=v;break;}if(v.age<slot.age)slot=v;}
   slot.id=id;slot.channel=channel;slot.held=true;slot.active=true;slot.age=++serial;
   slot.wav=samples[note];slot.pos=slot.phase=0;slot.env=0;slot.decay=1;
   slot.sampleStep=slot.wav==null?0:(double)slot.wav.sampleRate/rate;
   slot.phaseStep=TABLE_SIZE*440*Math.pow(2,(note-69)/12.0)/rate;
   slot.gain=velocity/127f*(slot.wav==null?0.23f:1f);
 }
 void mix(float[] block) {
  mix(block,0);
 }
 void mix(float[] block,long now){
  // Muting gates output only: note releases, pedals and sample time continue advancing.
  commands();Arrays.fill(block,0);renderingBlock=block;FileAudioSequence sequence=fileSequence;
  if(sequence==null||sequence.cancelled)render(0,block.length);else sequence.render(now,block.length,this);
  int active=0;for(Voice voice:voices)if(voice.active)active++;
  for(int k=0;k<block.length;k++)block[k]=Math.max(-0.98f,Math.min(0.98f,block[k]));activeVoices=active;renderingBlock=null;
 }
 @Override public void note(int key,int velocity,boolean down){command(down?1:2,key,down?((NoteMap.NOTES[key]<<8)|velocity):0);}
 @Override public void render(int offset,int frames){
  float[] block=renderingBlock;float master=muted?0:volume;boolean natural=oneShot;
  for(Voice v:voices) {
   if(!v.active)continue;
   WavData wav=v.wav;boolean sounding=(wav!=null && natural) || v.held || pedals[v.channel];
   float attack=wav==null?synthAttack:wavAttack,gain=v.gain*master;
   for(int k=offset;k<offset+frames;k++) {
    float signal;
    if(wav!=null) {
     int p=(int)v.pos;if(p>=wav.samples.length){deactivate(v);break;}
     if(v.sampleStep==1)signal=wav.samples[p];
     else {int p2=Math.min(p+1,wav.samples.length-1);float f=(float)(v.pos-p);signal=wav.samples[p]+(wav.samples[p2]-wav.samples[p])*f;}
     v.pos+=v.sampleStep;
    } else {
     int p=(int)v.phase;float f=(float)(v.phase-p);
     signal=(WAVE[p]+(WAVE[(p+1)&(TABLE_SIZE-1)]-WAVE[p])*f)*v.decay;
     v.phase+=v.phaseStep;while(v.phase>=TABLE_SIZE)v.phase-=TABLE_SIZE;v.decay*=synthDecay;
    }
    if(sounding)v.env=Math.min(1,v.env+attack);else v.env*=release;
    block[k]+=signal*v.env*gain;
    if((!sounding && v.env<0.0001f) || (wav==null && v.decay<0.0001f)){deactivate(v);break;}
   }
  }
 }
}
