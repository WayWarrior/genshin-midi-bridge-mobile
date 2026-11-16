package com.junliang.midibridge;
import java.util.*;

/** Shared mapped timeline for audio and touch. One playable physical key per overlapping pitch. */
public final class MidiScore {
 public static final class Gate {
  public final int id,key,velocity;public final long startMs,endMs;
  Gate(int i,int k,int v,long start,long end){id=i;key=k;velocity=v;startMs=start;endMs=end;}
 }
 public static final class Event {
  public final long ms;public final int key,velocity;public final boolean down;
  Event(long t,int k,int v,boolean d){ms=t;key=k;velocity=v;down=d;}
 }
 private static final class Span {int key,v;long start,end;Span(int k,int velocity,long s,long e){key=k;v=velocity;start=s;end=e;}}
 public final List<Gate> holds,taps;public final List<Event> events;
 public final long durationMs;public final int skipped,corrected,folded,merged,notes;
 private MidiScore(List<Gate> h,List<Gate> t,List<Event> e,long duration,int skip,int correction,int fold,int merge,int count){holds=Collections.unmodifiableList(h);taps=Collections.unmodifiableList(t);events=Collections.unmodifiableList(e);durationMs=duration;skipped=skip;corrected=correction;folded=fold;merged=merge;notes=count;}
 public static MidiScore build(SmfFile file,boolean[] tracks,int transpose,int accidental,boolean fold,int speed) {
  return build(file,tracks,transpose,accidental,fold,speed,0);
 }
 /** bpm=0 follows the original tempo map; a positive BPM replaces it for PPQ files. */
 public static MidiScore build(SmfFile file,boolean[] tracks,int transpose,int accidental,boolean fold,int speed,double bpm) {
  if(speed<25||speed>200)throw new IllegalArgumentException("播放速度应为25%–200%");
  if(bpm!=0){
   MidiFileOptions.validateBpm(bpm);
   if((file.division&0x8000)!=0)throw new IllegalArgumentException("此 MIDI 使用 SMPTE 时间基准，不能按 BPM 覆盖。请关闭覆盖原速，或导出为普通 PPQ MIDI。");
   if(file.durationTicks*60000.0/(file.division*bpm)>SmfFile.MAX_US/1000)throw new IllegalArgumentException("目标 BPM 下的演奏时长超过6小时，请提高速度");
  }
  ArrayList<Span> spans=new ArrayList<>();int skip=0,correction=0,folded=0,merge=0,count=0;
  for(SmfFile.Note n:file.notes){if(tracks!=null&&(n.track>=tracks.length||!tracks[n.track]))continue;if(n.channel==9){skip++;continue;}
   int raw=n.pitch+transpose,ix=NoteMap.index(raw,0,fold,accidental);if(ix<0){skip++;continue;}if(NoteMap.index(raw,0,true)<0)correction++;if(raw<48||raw>83)folded++;
   long start=millis(file,n.startUs,n.startTick,speed,bpm),end=Math.max(start+1,millis(file,n.endUs,n.endTick,speed,bpm));spans.add(new Span(ix,n.velocity,start,end));count++;
  }
  Collections.sort(spans,(a,b)->a.start==b.start?Integer.compare(a.key,b.key):Long.compare(a.start,b.start));
  Span[] last=new Span[21];ArrayList<Span> normalized=new ArrayList<>();
  for(Span n:spans){Span p=last[n.key];if(p!=null&&n.start<=p.start+1){p.end=Math.max(p.end,n.end);p.v=Math.max(p.v,n.v);merge++;continue;}
   if(p!=null&&p.end>=n.start){long carry=p.end;p.end=n.start-1;n.end=Math.max(n.end,carry);merge++;}normalized.add(n);last[n.key]=n;
  }
  ArrayList<Gate> holds=new ArrayList<>(),taps=new ArrayList<>();ArrayList<Event> events=new ArrayList<>();long duration=millis(file,file.durationUs,file.durationTicks,speed,bpm);
  long[] nextStart=new long[normalized.size()];Arrays.fill(nextStart,Long.MAX_VALUE);int[] next=new int[21];Arrays.fill(next,-1);
  for(int i=normalized.size()-1;i>=0;i--){Span n=normalized.get(i);if(next[n.key]>=0)nextStart[i]=normalized.get(next[n.key]).start;next[n.key]=i;}
  for(int i=0;i<normalized.size();i++){Span n=normalized.get(i);holds.add(new Gate(i,n.key,n.v,n.start,n.end));long tapEnd=Math.min(n.start+24,nextStart[i]-1);tapEnd=Math.max(n.start+1,tapEnd);taps.add(new Gate(i,n.key,n.v,n.start,tapEnd));events.add(new Event(n.start,n.key,n.v,true));events.add(new Event(n.end,n.key,0,false));duration=Math.max(duration,Math.max(n.end,tapEnd));}
  Collections.sort(events,(a,b)->a.ms==b.ms?Boolean.compare(a.down,b.down):Long.compare(a.ms,b.ms));
  return new MidiScore(holds,taps,events,duration,skip,correction,folded,merge,count);
 }
 private static long millis(SmfFile file,long us,long tick,int speed,double bpm){return Math.round(bpm==0?us/(10.0*speed):tick*60000.0/(file.division*bpm));}
 public static int maxPolyphony(List<Gate> gates){ArrayList<long[]> edges=new ArrayList<>();for(Gate g:gates){edges.add(new long[]{g.startMs,1});edges.add(new long[]{g.endMs+1,-1});}Collections.sort(edges,(a,b)->a[0]==b[0]?Long.compare(a[1],b[1]):Long.compare(a[0],b[0]));int live=0,max=0;for(long[] e:edges){live+=(int)e[1];max=Math.max(max,live);}return max;}
 public String summary(){return "可弹 "+holds.size()+" 音；半音修正 "+corrected+"，八度折回 "+folded+"，跳过 "+skipped+"（含鼓轨/未映射音），同键合并/重触发 "+merged;}
}
