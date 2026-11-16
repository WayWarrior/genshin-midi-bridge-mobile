package com.junliang.midibridge;
import java.io.*;
public final class SampleChecks {
 public static void main(String[] args)throws Exception{int count=0;long bytes=0;
  for(int n:NoteMap.NOTES)try(InputStream in=new FileInputStream(new File(args[0],"note_"+n+".wav"))){WavData w=WavData.read(in);if(w.sampleRate!=48000||w.samples.length<90000||w.samples.length>250000)throw new AssertionError("unexpected sample "+n);float peak=0;for(float v:w.samples){if(!Float.isFinite(v))throw new AssertionError("nonfinite sample");peak=Math.max(peak,Math.abs(v));}if(peak<0.01||peak>1)throw new AssertionError("silent/out-of-range sample "+n);count++;bytes+=w.samples.length*4L;}
  System.out.println("PASS: "+count+" bundled WAV samples decoded, "+bytes+" bytes PCM float");
 }
}
