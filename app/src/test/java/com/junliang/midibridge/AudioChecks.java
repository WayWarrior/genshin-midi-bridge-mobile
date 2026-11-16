package com.junliang.midibridge;
import java.util.Arrays;

/** Offline signal tests; these do not measure device output or end-to-end latency. */
public final class AudioChecks {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private static float peak(float[] audio){float result=0;for(float x:audio)result=Math.max(result,Math.abs(x));return result;}
 private static WavData constant(int frames){float[] pcm=new float[frames];Arrays.fill(pcm,0.2f);return new WavData(pcm,48000);}
 private static AudioMixer mixer(AudioCommandQueue queue,WavData sample){AudioMixer m=new AudioMixer(48000,queue);WavData[] bank=new WavData[128];bank[60]=sample;bank[64]=sample;m.samples(bank);m.volume(1);return m;}
 private static void on(AudioCommandQueue q,int channel,int note,int velocity){q.offer(1,channel*128+note,(note<<8)|velocity);}
 public static void main(String[] args)throws Exception{run();}
 public static void run()throws Exception {
  checks=0;float[] block=new float[64];AudioCommandQueue q=new AudioCommandQueue(512);AudioMixer m=mixer(q,constant(256));
  on(q,0,60,127);m.mix(block);check(block[0]>0 && Math.abs(block[32]-0.2f)<0.0001,"WAV onset and gain preserved");
  q.offer(2,60,0);m.mix(block);check(Math.abs(peak(block)-0.2f)<0.0001,"natural decay does not cut off on release");
  m.mix(block);m.mix(block);m.mix(block);check(peak(block)==0 && m.activeVoices==0,"sample ends at its actual length");
  q=new AudioCommandQueue(512);m=mixer(q,constant(100000));m.oneShot(false);on(q,0,60,127);m.mix(block);q.offer(2,60,0);
  for(int i=0;i<400;i++)m.mix(block);check(m.activeVoices==0 && peak(block)==0,"released gated sample decays to silence");
  q=new AudioCommandQueue(512);m=mixer(q,constant(100000));m.oneShot(false);q.offer(4,128,127);on(q,1,60,127);m.mix(block);q.offer(2,188,0);
  for(int i=0;i<400;i++)m.mix(block);check(m.activeVoices==1 && peak(block)>0.19f,"sustain holds released note on its MIDI channel");
  q.offer(4,0,0);m.mix(block);check(m.activeVoices==1,"other channel pedal cannot release held note");
  q.offer(4,128,0);for(int i=0;i<400;i++)m.mix(block);check(m.activeVoices==0,"pedal up releases the note");
  q=new AudioCommandQueue(512);m=mixer(q,constant(100000));on(q,0,60,64);m.mix(block);check(Math.abs(block[40]-0.2f*64/127)<0.0001,"MIDI velocity preserved");
  m.volume(0.5f);m.mix(block);check(Math.abs(block[40]-0.1f*64/127)<0.0001,"local volume applied independently of velocity");
  m.volume(0);m.mix(block);check(peak(block)==0,"local mute yields silence");
  q=new AudioCommandQueue(512);m=mixer(q,constant(100000));on(q,0,60,127);on(q,0,64,127);m.mix(block);check(Math.abs(block[40]-0.4f)<0.0001 && m.activeVoices==2,"chord notes mix in the same block");
  for(int i=0;i<60;i++)on(q,0,60,127);m.mix(block);check(m.activeVoices==48 && peak(block)<=0.98f,"voice stealing bounds polyphony and clipping");
  q.offer(3,0,0);m.mix(block);check(peak(block)==0 && m.activeVoices==0,"panic clears all active voices");
  q=new AudioCommandQueue(4);m=mixer(q,constant(100000));on(q,0,60,127);m.mix(block);
  for(int i=0;i<5;i++)q.offer(2,60,0);m.mix(block);check(q.overflowCount()==1 && peak(block)==0,"overflow keeps an emergency stop rather than a stuck note");
  WavData source=new WavData(new float[]{0,0.5f,1,0.5f},24000),converted=source.resample(48000);
  check(converted.sampleRate==48000 && converted.samples.length==8,"offline conversion preserves duration");
  check(converted.samples[1]==0.25f && converted.samples[3]==0.75f,"offline interpolation preserves waveform positions");
  check(converted.resample(48000)==converted,"native-rate WAV does not copy or resample");
  check(constant(48000).resample(44100).samples.length==44100,"48k to 44.1k duration");
  q=new AudioCommandQueue(512);m=new AudioMixer(48000,q);m.volume(1);on(q,0,69,127);
  float[] signal=new float[4800];m.mix(signal);int crossings=0;for(int i=145;i<signal.length;i++)if(signal[i-1]<=0 && signal[i]>0)crossings++;
  check(crossings>=41 && crossings<=44,"test synthesizer retains A4 pitch after lookup optimization");
  for(float x:signal)check(Float.isFinite(x),"finite synth output");
  AudioBufferPlan plan=new AudioBufferPlan(48000,192,0);check(plan.requestedFrames()==384 && plan.blockFrames()==64,"native burst determines low latency request");
  check(plan.capacityFrames(4096)==2048,"device minimum is honored when allocating track capacity");
  int current=384;for(int i=0;i<20;i++)current=plan.grow(current,2048);check(current==1536,"adaptation grows but stops at eight native bursts");
  check(plan.grow(400,420)==420,"adaptation honors platform capacity clamp");
  check(new AudioBufferPlan(44100,240,1).requestedFrames()==720 && new AudioBufferPlan(44100,240,2).requestedFrames()==960,"balanced and stable requests");
  plan=new AudioBufferPlan(-1,0,9);check(plan.rate==48000 && plan.burst==256 && plan.profile==0,"missing device properties use valid defaults");
  muteContinuity();queueConcurrency();System.out.println("PASS: "+checks+" audio assertions (onset, duration, polyphony, pedals, mute continuity, resampling, bounded buffers, concurrent FIFO)");
 }
 private static void muteContinuity(){
  AudioCommandQueue q=new AudioCommandQueue(512),referenceQueue=new AudioCommandQueue(512);
  float[] wave=new float[8192];for(int i=0;i<wave.length;i++)wave[i]=.1f+.1f*i/wave.length;
  WavData sample=new WavData(wave,48000);AudioMixer m=mixer(q,sample),reference=mixer(referenceQueue,sample);
  m.oneShot(false);reference.oneShot(false);m.volume(.37f);reference.volume(.37f);
  float[] actual=new float[64],expected=new float[64];on(q,0,60,127);on(referenceQueue,0,60,127);
  m.mix(actual);reference.mix(expected);check(Arrays.equals(actual,expected),"unmuted output matches the reference");
  m.muted(true);q.offer(4,0,127);referenceQueue.offer(4,0,127);q.offer(2,60,0);referenceQueue.offer(2,60,0);
  for(int i=0;i<20;i++){
   if(i==8){on(q,0,64,100);on(referenceQueue,0,64,100);}
   Arrays.fill(actual,1);m.mix(actual);reference.mix(expected);
   check(peak(actual)==0,"muted block is silent with active notes and pedal");
   check(m.activeVoices==reference.activeVoices,"notes remain synchronized while muted");
  }
  m.muted(false);m.mix(actual);reference.mix(expected);
  check(peak(actual)>0&&Arrays.equals(actual,expected),"unmute resumes at current sample positions and previous gain");
  m.muted(true);m.volume(.24f);reference.volume(.24f);q.offer(2,64,0);referenceQueue.offer(2,64,0);q.offer(4,0,0);referenceQueue.offer(4,0,0);
  for(int i=0;i<5;i++){m.mix(actual);reference.mix(expected);check(peak(actual)==0,"changing volume while muted stays silent");}
  m.muted(false);m.mix(actual);reference.mix(expected);
  check(Arrays.equals(actual,expected),"volume changed during mute is used on unmute without restarting envelopes");
  q=new AudioCommandQueue(512);m=mixer(q,constant(256));m.volume(.5f);m.muted(true);on(q,0,60,127);
  for(int i=0;i<8;i++)m.mix(actual);
  check(m.activeVoices==0&&peak(actual)==0,"sample triggered while muted still reaches its end");
  m.muted(false);m.mix(actual);check(peak(actual)==0,"unmute does not replay notes that already ended");
  on(q,0,60,127);m.mix(actual);check(Math.abs(actual[40]-.1f)<.0001f,"new note after unmute uses the saved volume");
  q=new AudioCommandQueue(512);m=mixer(q,constant(100000));m.oneShot(false);on(q,0,60,127);m.mix(actual);m.muted(true);q.offer(2,60,0);
  for(int i=0;i<400;i++)m.mix(actual);
  m.muted(false);m.mix(actual);check(m.activeVoices==0&&peak(actual)==0,"Note Off while muted releases the voice without a stuck note");
  m.volume(0);m.muted(true);m.muted(false);on(q,0,60,127);m.mix(actual);check(peak(actual)==0,"unmute preserves an explicitly zero volume");
 }
 private static void queueConcurrency()throws Exception {
  final AudioCommandQueue queue=new AudioCommandQueue(40000);
  Thread producer=new Thread(()->{for(int i=0;i<20000;i++)queue.offer(1,i%2048,i);});producer.start();
  int received=0;long deadline=System.nanoTime()+5000000000L;
  while(received<20000 && System.nanoTime()<deadline){long event=queue.poll();if(event<0){Thread.yield();continue;}check((event&65535)==received,"concurrent queue preserves order");received++;}
  producer.join(1000);check(received==20000 && queue.overflowCount()==0,"no concurrent MIDI events lost");
 }
}
