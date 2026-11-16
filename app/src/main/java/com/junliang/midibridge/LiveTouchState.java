package com.junliang.midibridge;

import java.util.ArrayDeque;
import java.util.Arrays;

/** MIDI sources own keys independently; folded sources share a single screen pointer. */
public final class LiveTouchState {
 public static final int KEYS=21, SOURCES=2048, MAX_PENDING=64;
 private final int[] sources=new int[SOURCES], refs=new int[KEYS];
 private final long[] versions=new long[KEYS];
 private final ArrayDeque<Long>[] attacks;
 private int held,pending;
 private long serial,revision;
 @SuppressWarnings("unchecked") public LiveTouchState(){
  Arrays.fill(sources,-1);attacks=(ArrayDeque<Long>[])new ArrayDeque<?>[KEYS];
  for(int k=0;k<KEYS;k++)attacks[k]=new ArrayDeque<>();
 }
 /** -1: bounded capacity exceeded, 0: duplicate Note On, 1: accepted change. */
 public int down(int source,int key,int pointerLimit){
  if(source<0||source>=SOURCES||key<0||key>=KEYS)throw new IllegalArgumentException("MIDI source/key");
  int old=sources[source];if(old==key)return 0;
  int after=held-(old>=0&&refs[old]==1?1:0)+(refs[key]==0?1:0);
  if(after>pointerLimit||(refs[key]==0&&pending>=MAX_PENDING))return -1;
  if(old>=0&&--refs[old]==0)held--;
  sources[source]=key;
  if(refs[key]++==0){held++;versions[key]=++serial;attacks[key].addLast(versions[key]);pending++;}
  revision++;return 1;
 }
 public boolean up(int source){
  if(source<0||source>=SOURCES)throw new IllegalArgumentException("MIDI source");
  int key=sources[source];if(key<0)return false;sources[source]=-1;
  if(--refs[key]==0)held--;revision++;return true;
 }
 public boolean held(int key){return refs[key]>0;}
 public int count(){return held;}
 public int pending(){return pending;}
 public boolean hasWork(){return held!=0||pending!=0;}
 public long version(int key){return held(key)?versions[key]:0;}
 public long nextAttack(int key){return attacks[key].isEmpty()?0:attacks[key].peekFirst();}
 public long revision(){return revision;}
 public void acknowledge(int key,long version){
  if(!attacks[key].isEmpty()&&attacks[key].peekFirst()==version){attacks[key].removeFirst();pending--;}
 }
 public void clear(){
  Arrays.fill(sources,-1);Arrays.fill(refs,0);held=0;pending=0;
  for(ArrayDeque<Long> queue:attacks)queue.clear();revision++;
 }
}
