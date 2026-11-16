package com.junliang.midibridge;
/** Stateful MIDI 1.0 byte parser. USB packets may split messages arbitrarily. */
public final class MidiParser {
 public interface Listener { void message(int status, int a, int b); }
 private final Listener listener;
 private int status, count, first;
 private boolean sysex;
 public MidiParser(Listener l) { listener=l; }
 public synchronized void reset() { status=0; count=0; sysex=false; }
 public synchronized void feed(byte[] data,int offset,int length) {
  if(offset<0 || length<0 || offset>data.length-length) throw new IllegalArgumentException();
  for(int i=offset;i<offset+length;i++) {
   int v=data[i]&255;
   if(v>=0xf8) { listener.message(v,0,0); continue; } // realtime never breaks running status
   if(v>=0x80) {
    count=0;
    if(v==0xf0) { sysex=true; status=0; continue; }
    if(v==0xf7) { sysex=false; status=0; continue; }
    sysex=false; status=v;
    if(size(v)==0) { listener.message(v,0,0); status=0; }
    continue;
   }
   if(sysex || status==0) continue;
   if(count==0) first=v;
   count++;
   if(count==size(status)) {
    listener.message(status,first,count==2?v:0);
    count=0;
    if(status>=0xf0) status=0; // system common cancels running status
   }
  }
 }
 private static int size(int s) {
  if(s<0xf0) return (s&0xf0)==0xc0 || (s&0xf0)==0xd0?1:2;
  return s==0xf2?2:(s==0xf1 || s==0xf3?1:0);
 }
}
