package com.junliang.midibridge;

import java.util.Arrays;

/** Transactional slots: releases precede reuse, and only a confirmed callback commits ownership. */
public final class LiveTouchSlots {
 public static final int LIMIT=10;
 private final int[] held=new int[LiveTouchState.KEYS];
 private final long[] versions=new long[LiveTouchState.KEYS];
 private long generation;
 public LiveTouchSlots(){Arrays.fill(held,-1);}
 public static final class Batch {
  private final LiveTouchSlots owner;private final long generation;
  private final int[] slots,tail;private final long[] versions;
  private Batch(LiveTouchSlots o,long g,int[] s,int[] t,long[] v){owner=o;generation=g;slots=s;tail=t;versions=v;}
  public int slotAt(int part){return slots[part];}
 }
 public Batch plan(LiveTouchPlan plan){
  int[] slots=new int[plan.parts.size()],tail=new int[held.length];long[] next=new long[held.length];
  boolean[] represented=new boolean[held.length],occupied=new boolean[LIMIT];long[] freedAfter=new long[LIMIT];
  Arrays.fill(tail,-1);Arrays.fill(freedAfter,-1);
  for(int i=0;i<plan.parts.size();i++){
   LiveTouchPlan.Part p=plan.parts.get(i);if(p.kind==LiveTouchPlan.START)continue;
   if(held[p.key]<0||versions[p.key]!=p.version||represented[p.key])throw new IllegalStateException("retained touch identity");
   represented[p.key]=true;int slot=held[p.key];slots[i]=slot;
   if(p.kind==LiveTouchPlan.KEEP){occupied[slot]=true;tail[p.key]=slot;next[p.key]=p.version;}
   else freedAfter[slot]=p.offset+p.duration;
  }
  for(int k=0;k<held.length;k++)if(held[k]>=0&&!represented[k])throw new IllegalStateException("missing retained touch");
  for(int i=0;i<plan.parts.size();i++){
   LiveTouchPlan.Part p=plan.parts.get(i);if(p.kind!=LiveTouchPlan.START)continue;
   if(tail[p.key]>=0)throw new IllegalStateException("duplicate key start");
   int slot=0;while(slot<LIMIT&&(occupied[slot]||freedAfter[slot]>=p.offset))slot++;
   if(slot==LIMIT)throw new IllegalStateException("no released touch slot");
   slots[i]=slot;occupied[slot]=true;
   if(p.continues){tail[p.key]=slot;next[p.key]=p.version;}
  }
  return new Batch(this,generation,slots,tail,next);
 }
 public boolean complete(Batch batch){
  if(batch==null||batch.owner!=this||batch.generation!=generation)return false;
  System.arraycopy(batch.tail,0,held,0,held.length);System.arraycopy(batch.versions,0,versions,0,versions.length);generation++;return true;
 }
 public void clear(){Arrays.fill(held,-1);Arrays.fill(versions,0);generation++;}
 public int slot(int key){return held[key];}
}
