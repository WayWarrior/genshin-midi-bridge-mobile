package com.junliang.midibridge;
import java.io.*;
import java.util.*;
public final class CoreChecks {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{run();}
 public static void run()throws Exception {
  checks=0;
  List<String> events=new ArrayList<>();MidiParser p=new MidiParser((s,a,b)->events.add(s+":"+a+":"+b));
  p.feed(new byte[]{(byte)0x90,60},0,2);check(events.isEmpty(),"split message should wait");
  p.feed(new byte[]{100,62,110},0,3);check(events.equals(Arrays.asList("144:60:100","144:62:110")),"running status across packets");
  events.clear();p.reset();p.feed(new byte[]{99,(byte)0x90,64,(byte)0xf8,120},0,5);check(events.equals(Arrays.asList("248:0:0","144:64:120")),"realtime interleave and orphan data");
  events.clear();p.reset();p.feed(new byte[]{(byte)0xf0,1,2,3,(byte)0xf8,(byte)0xf7,60,90,(byte)0x90,67,0},0,11);check(events.equals(Arrays.asList("248:0:0","144:67:0")),"SysEx ignored and velocity-zero note retained");
  events.clear();p.reset();p.feed(new byte[]{(byte)0xc0,4,5,(byte)0xf2,1,2,60,90},0,8);check(events.equals(Arrays.asList("192:4:0","192:5:0","242:1:2")),"one-byte messages and system common cancels running status");
  events.clear();p.reset();p.feed(new byte[]{7,(byte)0x91,60,100,61,101},1,5);check(events.equals(Arrays.asList("145:60:100","145:61:101")),"offset bounds");
  boolean threw=false;try{p.feed(new byte[2],1,2);}catch(IllegalArgumentException e){threw=true;}check(threw,"invalid offset rejected");
  // Same byte stream must produce exactly the same messages for every packet split.
  byte[] stream={(byte)0x90,48,100,50,0,(byte)0xf8,52,127,(byte)0xb0,64,127,(byte)0x80,52,12};
  List<String> expected=new ArrayList<>();new MidiParser((s,a,b)->expected.add(s+":"+a+":"+b)).feed(stream,0,stream.length);
  for(int split=0;split<=stream.length;split++){List<String> actual=new ArrayList<>();MidiParser q=new MidiParser((s,a,b)->actual.add(s+":"+a+":"+b));q.feed(stream,0,split);q.feed(stream,split,stream.length-split);check(actual.equals(expected),"split invariance "+split);}
  for(int n=0;n<128;n++)for(int shift=-24;shift<=24;shift++){
   int ix=NoteMap.index(n,shift,false);if(ix>=0)check(NoteMap.NOTES[ix]==n+shift,"exact pitch mapping");
   int f=NoteMap.index(n,shift,true);int pc=Math.floorMod(n+shift,12);boolean white=pc==0||pc==2||pc==4||pc==5||pc==7||pc==9||pc==11;
   check((f>=0)==white,"fold preserves natural/accidental");if(f>=0)check(Math.floorMod(NoteMap.NOTES[f]-(n+shift),12)==0,"fold only octaves");
  }
  WavData w=WavData.read(new ByteArrayInputStream(wav(1,16,2,48000,new byte[]{0,64,0,(byte)0xc0,(byte)0xff,127,(byte)0xff,127},false)));
  check(w.samples.length==2 && Math.abs(w.samples[0])<0.0001 && w.samples[1]>0.99,"stereo averaging PCM16");
  WavData odd=WavData.read(new ByteArrayInputStream(wav(1,16,1,44100,new byte[]{0,64},true)));check(odd.sampleRate==44100&&odd.samples[0]==0.5f,"odd-sized unknown chunk padding");
  byte[] floats=new byte[8];intBits(floats,0,Float.floatToIntBits(Float.NaN));intBits(floats,4,Float.floatToIntBits(2f));
  WavData f=WavData.read(new ByteArrayInputStream(wav(3,32,1,48000,floats,false)));check(f.samples[0]==0 && f.samples[1]==1,"nonfinite and over-range float samples");
  reject(new byte[4],"invalid header");reject(wav(1,8,1,48000,new byte[]{0},false),"unsupported format");
  byte[] valid=wav(1,16,1,48000,new byte[]{0,0},false);reject(Arrays.copyOf(valid,valid.length-1),"truncated data");reject(wav(1,16,1,48000,new byte[]{0},false),"partial frame");
  System.out.println("PASS: "+checks+" assertions (MIDI packet boundaries, pitch mapping, WAV decoding)");
 }
 private static void reject(byte[] b,String reason)throws Exception{boolean bad=false;try{WavData.read(new ByteArrayInputStream(b));}catch(IOException e){bad=true;}check(bad,reason);}
 private static void intBits(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>(8*i));}
 private static void le(ByteArrayOutputStream b,int v,int bytes){for(int i=0;i<bytes;i++)b.write(v>>(8*i));}
 private static void tag(ByteArrayOutputStream b,String t)throws IOException{b.write(t.getBytes("US-ASCII"));}
 private static byte[] wav(int fmt,int bits,int ch,int rate,byte[] samples,boolean odd)throws IOException{
  ByteArrayOutputStream chunks=new ByteArrayOutputStream();if(odd){tag(chunks,"JUNK");le(chunks,1,4);chunks.write(42);chunks.write(0);}
  tag(chunks,"fmt ");le(chunks,16,4);le(chunks,fmt,2);le(chunks,ch,2);le(chunks,rate,4);le(chunks,rate*ch*bits/8,4);le(chunks,ch*bits/8,2);le(chunks,bits,2);
  tag(chunks,"data");le(chunks,samples.length,4);chunks.write(samples);ByteArrayOutputStream out=new ByteArrayOutputStream();tag(out,"RIFF");le(out,chunks.size()+4,4);tag(out,"WAVE");out.write(chunks.toByteArray());return out.toByteArray();
 }
}
