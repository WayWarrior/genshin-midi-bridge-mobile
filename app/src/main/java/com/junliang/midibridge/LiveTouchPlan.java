package com.junliang.midibridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** One short continuation segment, including releases and queued brief attacks. */
public final class LiveTouchPlan {
 public static final int RELEASE=0, KEEP=1, START=2;
 public static final long SEGMENT_MS=24;
 public static final class Part {
  public final int key,kind;public final long version,offset,duration;public final boolean continues;
  private Part(int key,int kind,long version,long offset,long duration,boolean continues){this.key=key;this.kind=kind;this.version=version;this.offset=offset;this.duration=duration;this.continues=continues;}
 }
 public final List<Part> parts;
 public final long duration,revision;
 private LiveTouchPlan(List<Part> parts,long duration,long revision){this.parts=Collections.unmodifiableList(parts);this.duration=duration;this.revision=revision;}
 public static LiveTouchPlan create(LiveTouchState state,long[] previous,int strokeLimit,int pointerLimit){
  if(previous.length!=LiveTouchState.KEYS||pointerLimit<1||strokeLimit<pointerLimit)throw new IllegalArgumentException("touch limits");
  ArrayList<Part> parts=new ArrayList<>();ArrayList<Part> starts=new ArrayList<>();int keep=0;
  for(int k=0;k<LiveTouchState.KEYS;k++)if(previous[k]!=0){
   boolean same=state.version(k)==previous[k];
   parts.add(new Part(k,same?KEEP:RELEASE,previous[k],0,same?SEGMENT_MS:1,same));
   if(same)keep++;
  }
  if(parts.size()>pointerLimit)throw new IllegalArgumentException("too many retained pointers");
  // AOSP validates the first step against exactly the old retained pointers.
  // A new finger must appear after that step, even when none of the old keys releases.
  for(int k=0;k<LiveTouchState.KEYS;k++){
   long attack=state.nextAttack(k);
   if(attack==0&&previous[k]==0)attack=state.version(k);
   if(attack!=0&&attack!=previous[k])starts.add(new Part(k,START,attack,parts.isEmpty()?0:2,SEGMENT_MS,state.version(k)==attack));
  }
  starts.sort(Comparator.comparingLong(p->p.version));
  int slots=Math.min(pointerLimit-keep,strokeLimit-parts.size());
  for(int i=0;i<Math.min(slots,starts.size());i++)parts.add(starts.get(i));
  long duration=0;for(Part p:parts)duration=Math.max(duration,p.offset+p.duration);
  return new LiveTouchPlan(parts,duration,state.revision());
 }
 public void acknowledge(LiveTouchState state){for(Part p:parts)if(p.kind==START)state.acknowledge(p.key,p.version);}
 /** Close every old pointer before rebuilding; physical MIDI ownership is left intact. */
 public static LiveTouchPlan releaseAll(long[] previous,long revision){
  if(previous.length!=LiveTouchState.KEYS)throw new IllegalArgumentException("touch keys");
  ArrayList<Part> parts=new ArrayList<>();
  for(int k=0;k<previous.length;k++)if(previous[k]!=0)parts.add(new Part(k,RELEASE,previous[k],0,1,false));
  return new LiveTouchPlan(parts,parts.isEmpty()?0:1,revision);
 }
}
