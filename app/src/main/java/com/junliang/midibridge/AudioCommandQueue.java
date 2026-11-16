package com.junliang.midibridge;

/** Bounded primitive FIFO; MIDI messages do not allocate temporary arrays. */
final class AudioCommandQueue {
 private final long[] events;
 private int head,tail,size;
 private long overflows;
 AudioCommandQueue(int capacity){if(capacity<2)throw new IllegalArgumentException("queue capacity");events=new long[capacity];}
 synchronized boolean offer(int kind,int id,int value) {
  if(size==events.length){head=tail=size=0;events[tail++]=pack(3,0,0);size=1;overflows++;return false;}
  events[tail]=pack(kind,id,value);tail=(tail+1)%events.length;size++;return true;
 }
 synchronized long poll(){if(size==0)return -1;long event=events[head];head=(head+1)%events.length;size--;return event;}
 synchronized void clear(){head=tail=size=0;}
 synchronized long overflowCount(){return overflows;}
 private static long pack(int kind,int id,int value){return ((long)kind<<32)|((long)(id&65535)<<16)|(value&65535L);}
}
