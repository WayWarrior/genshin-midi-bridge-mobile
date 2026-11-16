package com.junliang.midibridge;
import java.io.*;
import java.util.*;

/** Reproduces silent batched short notes and missed late taps, using production classes. */
public final class FileTimingChecks {
 private static int checks;
 private static void ok(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 private static void eq(long got,long expected,String message){checks++;if(got!=expected)throw new AssertionError(message+": "+got+" != "+expected);}
 private static void event(ByteArrayOutputStream out,int delta,int... data){int v=delta&127;while((delta>>>=7)>0)v=(v<<8)|(delta&127)|128;while(true){out.write(v&255);if((v&128)==0)break;v>>>=8;}for(int b:data)out.write(b);}
 private static MidiScore score(int duration,int[]... notes)throws Exception{
  ArrayList<int[]> edges=new ArrayList<>();for(int[] n:notes){edges.add(new int[]{n[0],0x90,n[2],100});edges.add(new int[]{n[1],0x80,n[2],0});}edges.sort((a,b)->a[0]!=b[0]?Integer.compare(a[0],b[0]):Integer.compare(a[1],b[1]));
  ByteArrayOutputStream tr=new ByteArrayOutputStream();event(tr,0,0xff,0x51,3,0x0f,0x42,0x40);int time=0;
  for(int[] edge:edges){event(tr,edge[0]-time,edge[1],edge[2],edge[3]);time=edge[0];}event(tr,duration-time,0xff,0x2f,0);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4d546864);out.writeInt(6);out.writeShort(0);out.writeShort(1);out.writeShort(1000);out.writeInt(0x4d54726b);out.writeInt(tr.size());out.write(tr.toByteArray());return MidiScore.build(SmfFile.read(bytes.toByteArray()),null,0,0,true,100);
 }
 private static AudioMixer mixer(AudioCommandQueue q){AudioMixer mixer=new AudioMixer(48000,q);WavData[] bank=new WavData[128];float[] pcm=new float[48000];Arrays.fill(pcm,.2f);for(int note:NoteMap.NOTES)bank[note]=new WavData(pcm,48000);mixer.samples(bank);mixer.volume(1);mixer.oneShot(false);return mixer;}
 private static float peak(float[] data){float peak=0;for(float value:data)peak=Math.max(peak,Math.abs(value));return peak;}
 private static final class Recorder implements FileAudioSequence.Sink {
  long frames;final ArrayList<long[]> edges=new ArrayList<>();
  public void note(int key,int velocity,boolean down){edges.add(new long[]{frames,key,velocity,down?1:0});}
  public void render(int offset,int count){ok(count>0,"render intervals have positive length");frames+=count;}
 }
 public static void main(String[] args)throws Exception{run();}
 public static void run()throws Exception{
  checks=0;AudioCommandQueue queue=new AudioCommandQueue(512);AudioMixer legacy=mixer(queue);queue.offer(1,7,(60<<8)|100);queue.offer(2,7,0);float[] block=new float[64];legacy.mix(block);ok(peak(block)==0,"reproduce old Note On/Off batch producing no audio");
  MidiScore shortNote=score(30,new int[]{0,1,60});AudioMixer actual=mixer(new AudioCommandQueue(512));FileAudioSequence sequence=new FileAudioSequence(shortNote,48000,1000,0);actual.fileSequence(sequence);
  actual.mix(block,2000);ok(peak(block)>.1f,"1ms file note remains audible even after a late wall-clock start");eq(sequence.noteOns,1,"one file onset");eq(sequence.startLateMs,1000,"late start is reported instead of dropping notes");
  for(int i=0;i<1200;i++)actual.mix(block,2001+i);eq(sequence.positionMs,30,"position reaches musical end");ok(sequence.finished,"sequence completes");eq(sequence.noteOns,1,"completed note is not repeated");ok(peak(block)==0,"short note fully releases");
  MidiScore musical=score(100,new int[]{3,7,60},new int[]{3,14,64},new int[]{19,20,67},new int[]{40,80,60});
  for(int rate:new int[]{44100,48000,96000})for(int size:new int[]{32,64,257,512}){
   sequence=new FileAudioSequence(musical,rate,0,0);Recorder recorder=new Recorder();int calls=0;
   while(!sequence.finished&&calls<1000){sequence.render(calls*127L,size,recorder);calls++;}
   ok(sequence.finished,"score completes for rate and block size");eq(recorder.edges.size(),musical.events.size(),"all presses and releases are emitted");
   for(int i=0;i<recorder.edges.size();i++){long[] edge=recorder.edges.get(i);MidiScore.Event expected=musical.events.get(i);eq(edge[0],expected.ms*rate/1000,"note edge has exact sample position");eq(edge[1],expected.key,"mapped key retained");eq(edge[2],expected.velocity,"velocity retained");eq(edge[3],expected.down?1:0,"press/release order retained");}
   eq(sequence.positionMs,100,"song duration preserved under irregular wall-clock updates");eq(sequence.noteOns,4,"chord and short attacks each played once");
  }
  sequence=new FileAudioSequence(shortNote,48000,1000,0);Recorder countdown=new Recorder();sequence.render(999,64,countdown);eq(countdown.edges.get(0)[0],48,"countdown begins inside a block at the correct frame");
  sequence=new FileAudioSequence(musical,48000,5000,50);Recorder resume=new Recorder();sequence.render(5000,64,resume);eq(resume.edges.size(),1,"resume restores just the crossing held note");eq(resume.edges.get(0)[0],0,"resume held note starts at resume");eq(resume.edges.get(0)[1],7,"earlier completed notes are not replayed");while(!sequence.finished)sequence.render(5000,64,resume);eq(resume.edges.size(),2,"crossing note has one final release");eq(resume.edges.get(1)[0],30*48,"remaining duration releases at its exact frame");
  sequence=new FileAudioSequence(musical,48000,0,0);Recorder cancelled=new Recorder();sequence.cancel();sequence.render(1000,512,cancelled);eq(cancelled.edges.size(),0,"cancelled generation cannot issue an old onset");eq(sequence.positionMs,0,"cancellation freezes the file position");
  AudioMixer muted=mixer(new AudioCommandQueue(512)),reference=mixer(new AudioCommandQueue(512));FileAudioSequence mutedSequence=new FileAudioSequence(musical,48000,0,0),referenceSequence=new FileAudioSequence(musical,48000,0,0);muted.fileSequence(mutedSequence);reference.fileSequence(referenceSequence);float[] expected=new float[64];muted.muted(true);
  for(int i=0;i<23;i++){muted.mix(block,i);reference.mix(expected,i);ok(peak(block)==0,"file muting outputs silence while scheduling advances");}
  muted.muted(false);muted.mix(block,24);reference.mix(expected,24);ok(Arrays.equals(block,expected),"file unmute preserves sample time and releases");eq(mutedSequence.positionMs,referenceSequence.positionMs,"muted and audible file clocks match");
  AudioCommandQueue stopQueue=new AudioCommandQueue(512);AudioMixer stopped=mixer(stopQueue);sequence=new FileAudioSequence(musical,48000,0,0);stopped.fileSequence(sequence);for(int i=0;i<5;i++)stopped.mix(block,i);long attacks=sequence.noteOns;sequence.cancel();stopQueue.offer(3,0,0);for(int i=0;i<80;i++)stopped.mix(block,1000+i);eq(sequence.noteOns,attacks,"stop prevents later file attacks");ok(peak(block)==0&&stopped.activeVoices==0,"stop clears file voices and prevents hanging sound");
  // Old clock-based lookup skips an unplayed gate whose full tap interval has expired.
  ArrayList<MidiScore.Gate> taps=new ArrayList<>();taps.add(new MidiScore.Gate(0,0,100,0,24));taps.add(new MidiScore.Gate(1,1,100,110,134));taps.add(new MidiScore.Gate(2,2,100,260,284));
  GestureTimeline old=new GestureTimeline(taps);old.closedSlice(0,100,1);eq(old.nextStart(160),260,"reproduce skipped 110ms tap at late callback");
  FileTapPlan plan=new FileTapPlan(taps,0);FileTapPlan.Batch first=plan.batch(0,1);eq(plan.nextStart(),0,"planning alone does not acknowledge notes");ok(plan.complete(first),"completion advances the actual sent cursor");ok(!plan.complete(first),"duplicate callback does not advance twice");
  FileTapPlan.Batch delayed=plan.batch(160,2);eq(delayed.count,2,"late expired tap and future tap are both retained");eq(delayed.parts.get(0).gate.id,1,"first unplayed note is not skipped");eq(delayed.parts.get(0).duration,24,"late tap keeps a complete positive duration");eq(delayed.parts.get(1).offset,150,"late batch preserves relative spacing");eq(delayed.lateMs,50,"callback lateness exposed");ok(plan.complete(delayed),"delayed batch completion accepted");eq(plan.nextStart(),Long.MAX_VALUE,"all gates acknowledged");
  plan=new FileTapPlan(taps,0);checks++;try{plan.batch(121,10);throw new AssertionError("severe lateness accepted");}catch(IllegalArgumentException expectedError){ok(expectedError.getMessage().contains("暂停"),"overload is explained rather than silently skipping");}eq(plan.nextStart(),0,"late rejection leaves unplayed cursor intact");
  plan=new FileTapPlan(taps,111);eq(plan.nextStart(),260,"resume does not replay earlier short taps");
  taps.clear();for(int i=0;i<3;i++)taps.add(new MidiScore.Gate(i,i,100,0,24));for(int i=3;i<6;i++)taps.add(new MidiScore.Gate(i,i,100,40,64));plan=new FileTapPlan(taps,0);first=plan.batch(0,5);eq(first.count,3,"stroke budget never splits a chord");plan.complete(first);eq(plan.batch(40,5).count,3,"next chord stays simultaneous");
  taps.clear();for(int i=0;i<80;i++)taps.add(new MidiScore.Gate(i,i%7,100,i*50,i*50+24));plan=new FileTapPlan(taps,0);Set<Integer> sent=new HashSet<>();long now=0;int batches=0;
  while(plan.nextStart()!=Long.MAX_VALUE){now=Math.max(now,plan.nextStart());FileTapPlan.Batch batch=plan.batch(now,10);ok(batch.count<=10,"gesture stroke budget honored");for(FileTapPlan.Part part:batch.parts){ok(sent.add(part.gate.id),"each dense input sent only once");eq(part.duration,24,"every tap includes release");}plan.complete(batch);now=batch.end+16;batches++;}
  eq(sent.size(),80,"all notes survive realistic callback delay");ok(batches<20,"preplanned batches reduce callback frequency");
  System.out.println("PASS: "+checks+" file audio/touch timing assertions");
 }
}
