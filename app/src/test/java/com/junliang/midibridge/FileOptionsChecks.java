package com.junliang.midibridge;
import java.io.*;

/** Musical fixtures exercise the actual parser/options/score pipeline for both outputs. */
public final class FileOptionsChecks {
 private static int checks;
 private static void eq(long got,long want,String label){checks++;if(got!=want)throw new AssertionError(label+": "+got+" != "+want);}
 private static void ok(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
 private static void rejects(Runnable action,String message){checks++;try{action.run();}catch(IllegalArgumentException e){if(e.getMessage().contains(message))return;throw new AssertionError(e);}throw new AssertionError("accepted invalid option: "+message);}
 private static void event(ByteArrayOutputStream out,int delta,int... bytes){int v=delta&127;while((delta>>>=7)>0)v=(v<<8)|(delta&127)|128;while(true){out.write(v&255);if((v&128)==0)break;v>>>=8;}for(int b:bytes)out.write(b);}
 private static SmfFile song(int division,ByteArrayOutputStream... tracks)throws IOException{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4d546864);out.writeInt(6);out.writeShort(tracks.length==1?0:1);out.writeShort(tracks.length);out.writeShort(division);
  for(ByteArrayOutputStream tr:tracks){out.writeInt(0x4d54726b);out.writeInt(tr.size());out.write(tr.toByteArray());}return SmfFile.read(bytes.toByteArray());
 }
 private static MidiFileOptions options(int speed,boolean override,double bpm,boolean convert,int key){return new MidiFileOptions(speed,override,bpm,convert,key);}
 private static MidiScore fixed(SmfFile file,double bpm){return options(100,true,bpm,false,0).build(file,null,0,0,true);}
 private static void sameScore(MidiScore a,MidiScore b){eq(a.durationMs,b.durationMs,"unchanged duration with switches off");eq(a.events.size(),b.events.size(),"unchanged event count");for(int i=0;i<a.events.size();i++){MidiScore.Event x=a.events.get(i),y=b.events.get(i);eq(x.ms,y.ms,"unchanged timing");eq(x.key,y.key,"unchanged key");eq(x.velocity,y.velocity,"unchanged velocity");ok(x.down==y.down,"unchanged press/release");}}
 public static void main(String[] args)throws Exception{run();}
 public static void run()throws Exception{
  checks=0;ByteArrayOutputStream tr=new ByteArrayOutputStream();
  event(tr,0,0xff,0x51,3,7,0xa1,0x20);event(tr,17,0x90,60,100);event(tr,463,0x80,60,0);
  event(tr,0,0xff,0x51,3,0x0f,0x42,0x40);event(tr,240,0x90,67,110);
  event(tr,240,0xff,0x51,3,3,0xd0,0x90);event(tr,240,0x80,67,0);event(tr,480,0xff,0x2f,0);
  SmfFile variable=song(480,tr);eq(variable.notes.get(0).startTick,17,"off-grid tick retained");eq(variable.notes.get(1).startTick,720,"onset tick survives tempo changes");eq(variable.notes.get(1).endTick,1200,"release tick survives tempo changes");eq(variable.durationTicks,1680,"trailing rest has a musical end tick");
  eq(variable.notes.get(1).startUs,1000000,"source retains its tempo map");eq(variable.notes.get(1).endUs,1625000,"source retains changing tempo during a note");
  MidiScore original=MidiScore.build(variable,null,0,0,true,100),retimed=fixed(variable,120);
  eq(retimed.holds.get(0).startMs,18,"fixed tempo preserves off-grid onset");eq(retimed.holds.get(0).endMs,500,"fixed quarter note duration");eq(retimed.holds.get(1).startMs,750,"original slow section is replaced");eq(retimed.holds.get(1).endMs,1250,"original fast section is replaced");eq(retimed.durationMs,1750,"trailing rest follows target BPM");
  sameScore(retimed,options(25,true,120,false,0).build(variable,null,0,0,true));sameScore(retimed,options(200,true,120,false,0).build(variable,null,0,0,true));
  sameScore(original,options(100,false,Double.NaN,false,2).build(variable,null,0,0,true));sameScore(MidiScore.build(variable,null,0,0,true,50),options(50,false,400,false,9).build(variable,null,0,0,true));
  eq(variable.notes.get(1).startUs,1000000,"retiming does not mutate the loaded file");
  MidiScore slow=fixed(variable,60);eq(slow.holds.get(1).startMs,1500,"half target BPM doubles onset");eq(slow.holds.get(1).endMs,2500,"half target BPM doubles release");eq(slow.durationMs,3500,"half target BPM doubles song/rest duration");
  MidiScore decimal=fixed(variable,87.5);eq(decimal.holds.get(1).startMs,1029,"decimal BPM onset");eq(decimal.holds.get(1).endMs,1714,"decimal BPM release");eq(decimal.durationMs,2400,"decimal BPM song duration");
  eq(fixed(variable,20).durationMs,10500,"minimum target BPM");eq(fixed(variable,400).durationMs,525,"maximum target BPM");
  for(double bad:new double[]{0,19.99,400.01,-1,Double.NaN,Double.POSITIVE_INFINITY})rejects(()->options(100,true,bad,false,0),"20–400");
  rejects(()->options(24,false,120,false,0),"25–200");rejects(()->options(201,false,120,false,0),"25–200");sameScore(retimed,options(0,true,120,false,0).build(variable,null,0,0,true));
  rejects(()->options(100,false,120,true,-1),"原调");rejects(()->options(100,false,120,true,12),"原调");
  // Pedal up, rather than physical key up, determines the retimed release.
  tr.reset();event(tr,17,0x90,60,100);event(tr,0,0x90,64,90);event(tr,103,0xb0,64,127);event(tr,120,0x80,60,0);event(tr,0,0x80,64,0);event(tr,240,0xff,0x51,3,0x0f,0x42,0x40);event(tr,240,0xb0,64,0);event(tr,240,0xff,0x2f,0);
  SmfFile pedal=song(480,tr);MidiScore chord=fixed(pedal,60);eq(chord.holds.size(),2,"chord retained");eq(chord.holds.get(0).startMs,35,"pedal chord off-grid onset");eq(chord.holds.get(0).endMs,1500,"pedal release retimed");eq(chord.holds.get(1).endMs,1500,"all chord notes share pedal release");eq(chord.durationMs,2000,"rest after pedal up retimed");eq(MidiScore.maxPolyphony(chord.holds),2,"two-finger score preserved");eq(chord.events.get(2).ms,1500,"audio release follows hold release");eq(chord.taps.get(0).endMs,59,"game short tap remains a complete 24ms press");
  for(int control:new int[]{120,121,123}){
   tr.reset();event(tr,0,0x90,60,100);if(control==121){event(tr,0,0xb0,64,127);event(tr,60,0x80,60,0);event(tr,60,0xb0,control,0);}else event(tr,120,0xb0,control,0);event(tr,120,0xff,0x2f,0);
   eq(fixed(song(480,tr),60).holds.get(0).endMs,250,"controller release tick retained: "+control);
  }
  tr.reset();event(tr,17,0x90,60,100);event(tr,943,0xff,0x2f,0);SmfFile missing=song(480,tr);eq(missing.unclosedNotes,1,"missing note off remains reported");eq(fixed(missing,90).holds.get(0).endMs,1333,"missing note off retimed to song end");
  tr.reset();event(tr,0,0x90,60,100);event(tr,0,0x80,60,0);event(tr,0,0xff,0x2f,0);MidiScore zero=fixed(song(480,tr),120);eq(zero.holds.get(0).endMs,1,"zero-tick note retains minimum positive gate");eq(zero.events.get(1).ms,1,"zero-tick audio gate closes");
  ByteArrayOutputStream conductor=new ByteArrayOutputStream(),voice=new ByteArrayOutputStream();event(conductor,240,0xff,0x51,3,0x0f,0x42,0x40);event(conductor,720,0xff,0x2f,0);event(voice,0,0x90,62,100);event(voice,480,0x80,62,0);event(voice,0,0xff,0x2f,0);
  MidiScore multi=options(100,true,120,true,2).build(song(480,conductor,voice),new boolean[]{false,true},0,0,true);eq(multi.holds.get(0).endMs,500,"conductor tempo is ignored under override");eq(multi.durationMs,1000,"latest track end/rest retained");eq(multi.holds.get(0).key,7,"selected D major track becomes C");
  // All 12 major keys and their natural-minor relatives become C-major white notes.
  int[] majorScale={0,2,4,5,7,9,11},minorScale={0,2,3,5,7,8,10};
  for(int key=0;key<12;key++)for(boolean minor:new boolean[]{false,true}){
   tr.reset();int tonic=minor?(key+9)%12:key;int[] scale=minor?minorScale:majorScale;
   for(int interval:scale){event(tr,0,0x90,48+tonic+interval,100);event(tr,120,0x80,48+tonic+interval,0);}event(tr,0,0xff,0x2f,0);
   MidiScore mapped=options(100,true,120,true,key).build(song(480,tr),null,0,0,true);eq(mapped.holds.size(),7,"all scale notes map for key "+key+" minor "+minor);eq(mapped.corrected,0,"diatonic notes need no accidental correction");eq(mapped.skipped,0,"diatonic notes are not skipped");
   for(int i=0;i<7;i++)eq(NoteMap.NOTES[mapped.holds.get(i).key]%12,(minor?9+scale[i]:scale[i])%12,"relative major conversion preserves degree "+i);
  }
  tr.reset();event(tr,0,0x90,69,100);event(tr,480,0x80,69,0);event(tr,0,0xff,0x2f,0);MidiScore aMinor=options(100,false,120,true,0).build(song(480,tr),null,0,0,true);eq(aMinor.holds.get(0).key,12,"A minor is treated as C major, not shifted from A to C");
  tr.reset();event(tr,0,0x90,63,100);event(tr,480,0x80,63,0);event(tr,0,0xff,0x2f,0);SmfFile chromatic=song(480,tr);
  eq(options(100,false,120,true,2).build(chromatic,null,0,0,true).holds.size(),0,"accidental is assessed after D-to-C conversion");eq(options(100,false,120,true,2).build(chromatic,null,0,1,true).holds.get(0).key,8,"converted C-sharp corrected upward to D");eq(options(100,false,120,true,2).build(chromatic,null,0,2,true).holds.get(0).key,7,"converted C-sharp corrected downward to C");
  eq(options(100,false,120,true,2).build(chromatic,null,-1,0,true).holds.get(0).key,7,"shared transpose remains additive");
  tr.reset();event(tr,0,0x90,38,100);event(tr,480,0x80,38,0);event(tr,0,0xff,0x2f,0);SmfFile bass=song(480,tr);eq(options(100,false,120,true,2).build(bass,null,0,0,true).holds.get(0).key,0,"out-of-range D converts and folds to C3");eq(options(100,false,120,true,2).build(bass,null,0,0,false).holds.size(),0,"octave folding remains optional");
  tr.reset();event(tr,0,0x99,62,100);event(tr,480,0x89,62,0);event(tr,0,0xff,0x2f,0);eq(options(100,true,120,true,2).build(song(480,tr),null,0,0,true).holds.size(),0,"drum channel is not converted to melodic keys");
  tr.reset();event(tr,0,0x90,62,100);event(tr,1000,0x80,62,0);event(tr,0,0xff,0x2f,0);SmfFile smpte=song((-25&255)<<8|40,tr);eq(options(100,false,120,true,2).build(smpte,null,0,0,true).holds.get(0).endMs,1000,"SMPTE original timing/key conversion remain available");rejects(()->fixed(smpte,120),"SMPTE");
  tr.reset();event(tr,0,0x90,60,100);event(tr,43200,0x80,60,0);event(tr,0,0xff,0x2f,0);SmfFile longFile=song(1,tr);eq(fixed(longFile,120).durationMs,21600000,"six-hour target boundary accepted");rejects(()->fixed(longFile,20),"6小时");
  System.out.println("PASS: "+checks+" file tempo/key option assertions");
 }
}
