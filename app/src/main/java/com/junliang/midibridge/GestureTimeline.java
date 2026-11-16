package com.junliang.midibridge;
import java.util.*;

/** Pure gesture segmentation; includes continued fingers in each stroke budget. */
public final class GestureTimeline {
 public static final class Part {
  public final MidiScore.Gate gate;public final long offset,duration;public final boolean continues;
  Part(MidiScore.Gate g,long start,long end,long from,long to){gate=g;offset=start-from;duration=Math.max(1,end-start);continues=g.endMs>to;}
  Part(MidiScore.Gate g,long start,long end,long from){gate=g;offset=start-from;duration=Math.max(1,end-start);continues=false;}
 }
 public static final class Slice {
  public final long end;public final List<Part> parts;
  Slice(long e,List<Part> p){end=e;parts=p;}
 }
 private final List<MidiScore.Gate> gates;private int first;
 public GestureTimeline(List<MidiScore.Gate> notes){gates=notes;}
 public long nextStart(long from){for(int i=first;i<gates.size();i++){MidiScore.Gate g=gates.get(i);if(g.endMs>from)return Math.max(from,g.startMs);}return Long.MAX_VALUE;}
 public Slice slice(long from,long requestedEnd,int max) {
  while(first<gates.size()&&gates.get(first).endMs<=from)first++;
  long end=Math.max(from+1,requestedEnd);int count=0;
  for(int i=first;i<gates.size();i++){MidiScore.Gate g=gates.get(i);if(g.startMs>=end)break;if(g.endMs<=from)continue;if(++count>max){if(g.startMs<=from)throw new IllegalArgumentException("同时按键超过系统手势数量上限");end=g.startMs;break;}}
  ArrayList<Part> parts=new ArrayList<>();for(int i=first;i<gates.size();i++){MidiScore.Gate g=gates.get(i);if(g.startMs>=end)break;if(g.endMs<=from)continue;parts.add(new Part(g,Math.max(from,g.startMs),Math.min(end,g.endMs),from,end));}
  // A gesture finishes at its final stroke end; omit any trailing silence.
  long actual=from;for(Part p:parts)actual=Math.max(actual,from+p.offset+p.duration);
  return new Slice(parts.isEmpty()?end:actual,parts);
 }
 /** Tap batches always contain the complete release, even just beyond the batching window. */
 public Slice closedSlice(long from,long requestedEnd,int max) {
  if(max<1)throw new IllegalArgumentException("stroke limit");
  while(first<gates.size()&&gates.get(first).endMs<=from)first++;
  long limit=Math.max(from+1,requestedEnd),actual=from;ArrayList<Part> parts=new ArrayList<>();
  for(int i=first;i<gates.size();i++){MidiScore.Gate g=gates.get(i);if(g.startMs>=limit||parts.size()==max)break;if(g.endMs<=from)continue;
   parts.add(new Part(g,Math.max(from,g.startMs),g.endMs,from));actual=Math.max(actual,g.endMs);limit=Math.max(limit,actual);
  }
  return new Slice(parts.isEmpty()?limit:actual,parts);
 }
}
