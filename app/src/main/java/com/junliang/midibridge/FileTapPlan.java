package com.junliang.midibridge;
import java.util.*;

/** Completed-note cursor, independent of callback time; a late callback never discards a tap. */
final class FileTapPlan {
 static final long WINDOW_MS=800,MAX_LATE_MS=120;
 static final class Part {
  final MidiScore.Gate gate;final long offset,duration;
  Part(MidiScore.Gate gate,long first){this.gate=gate;offset=gate.startMs-first;duration=Math.max(1,gate.endMs-gate.startMs);}
 }
 static final class Batch {
  final int first,count;final long from,end,lateMs;final List<Part> parts;
  Batch(int first,long from,long end,long late,List<Part> parts){this.first=first;count=parts.size();this.from=from;this.end=end;lateMs=late;this.parts=parts;}
 }
 private final List<MidiScore.Gate> notes;private int index;
 FileTapPlan(List<MidiScore.Gate> notes,long resume){this.notes=notes;while(index<notes.size()&&notes.get(index).startMs<resume)index++;}
 long nextStart(){return index<notes.size()?notes.get(index).startMs:Long.MAX_VALUE;}
 Batch batch(long now,int max){
  if(max<1)throw new IllegalArgumentException("手势数量上限无效");if(index==notes.size())return null;
  long firstTime=notes.get(index).startMs,late=Math.max(0,now-firstTime);
  if(late>MAX_LATE_MS)throw new IllegalArgumentException("游戏触控迟到 "+late+"ms，已暂停；请降低文件速度或减少音轨");
  long from=Math.max(now,firstTime),end=from;ArrayList<Part> parts=new ArrayList<>();int cursor=index;
  while(cursor<notes.size()){
   long start=notes.get(cursor).startMs;if(start-firstTime>=WINDOW_MS)break;
   int groupEnd=cursor+1;while(groupEnd<notes.size()&&notes.get(groupEnd).startMs==start)groupEnd++;
   int group=groupEnd-cursor;if(group>max)throw new IllegalArgumentException("同时起音超过系统手势上限，请减少音轨");
   if(parts.size()+group>max)break;
   while(cursor<groupEnd){Part part=new Part(notes.get(cursor++),firstTime);parts.add(part);end=Math.max(end,from+part.offset+part.duration);}
  }
  return new Batch(index,from,end,late,parts);
 }
 boolean complete(Batch batch){if(batch==null||batch.first!=index)return false;index+=batch.count;return true;}
}
