package com.junliang.midibridge;
import java.io.*;
/** WAV PCM16 / IEEE float32, mono or stereo. Bounded import, no third party decoder. */
public final class WavData {
 public final float[] samples; public final int sampleRate;
 public WavData(float[] x,int rate) { samples=x; sampleRate=rate; }
 /** Resample on the loading thread rather than during live polyphonic playback. */
 public WavData resample(int targetRate) {
  if(targetRate<8000 || targetRate>192000)throw new IllegalArgumentException("sample rate");
  if(targetRate==sampleRate)return this;
  int frames=(int)Math.ceil(samples.length*(double)targetRate/sampleRate);
  float[] converted=new float[frames];double step=(double)sampleRate/targetRate;
  for(int i=0;i<frames;i++) {
   double position=i*step;int p=Math.min((int)position,samples.length-1),p2=Math.min(p+1,samples.length-1);
   float f=(float)(position-p);converted[i]=samples[p]+(samples[p2]-samples[p])*f;
  }
  return new WavData(converted,targetRate);
 }
 private static int u16(byte[] b,int p) { return (b[p]&255)|((b[p+1]&255)<<8); }
 private static int i32(byte[] b,int p) { return u16(b,p)|(u16(b,p+2)<<16); }
 private static boolean tag(byte[] b,int p,String t) { if(p+4>b.length)return false; for(int i=0;i<4;i++)if(b[p+i]!=(byte)t.charAt(i))return false; return true; }
 public static WavData read(InputStream in) throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n;
  while((n=in.read(buf))!=-1) { if(out.size()+n>16*1024*1024)throw new IOException("每个 WAV 最大 16 MiB"); out.write(buf,0,n); }
  byte[] b=out.toByteArray();
  if(b.length<12 || !tag(b,0,"RIFF") || !tag(b,8,"WAVE"))throw new IOException("不是 RIFF WAV 文件");
  int fmt=0,ch=0,rate=0,bits=0,align=0,data=-1,len=0;
  for(int p=12;p+8<=b.length;) {
   int size=i32(b,p+4),start=p+8;
   if(size<0 || size>b.length-start)throw new IOException("WAV 数据不完整");
   if(tag(b,p,"fmt ")) {
    if(size<16)throw new IOException("WAV 格式块损坏");
    fmt=u16(b,start); ch=u16(b,start+2); rate=i32(b,start+4); align=u16(b,start+12); bits=u16(b,start+14);
   }
   if(tag(b,p,"data") && data<0) {data=start;len=size;}
   long next=(long)start+size+(size&1); if(next>b.length)break; p=(int)next;
  }
  if((fmt!=1 || bits!=16) && (fmt!=3 || bits!=32))throw new IOException("请转为 PCM 16-bit 或 float 32-bit WAV");
  if(ch<1 || ch>2 || rate<8000 || rate>192000 || align!=ch*(bits/8) || data<0 || len<align || len%align!=0)throw new IOException("仅支持有效的单/双声道 WAV");
  int frames=len/align; if(frames>(long)rate*30)throw new IOException("每个采样最长 30 秒");
  float[] samples=new float[frames];
  for(int i=0;i<frames;i++) {
   float sum=0;
   for(int c=0;c<ch;c++) {int p=data+i*align+c*(bits/8); float v=fmt==1?(short)u16(b,p)/32768f:Float.intBitsToFloat(i32(b,p)); if(!Float.isFinite(v))v=0; sum+=Math.max(-1,Math.min(1,v));}
   samples[i]=sum/ch;
  }
  return new WavData(samples,rate);
 }
}
