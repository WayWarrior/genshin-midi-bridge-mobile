package com.junliang.midibridge;

import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.graphics.Path;

/** Experimental virtual identity getters; no reflection or hidden superclass calls. Do not rename. */
public final class ReusableStroke extends StrokeDescription {
 private final int slot;private final boolean continuation;
 private int identityReads,continuationReads;
 public ReusableStroke(Path path,long start,long duration,boolean keeps,int slot){this(path,start,duration,keeps,slot,false);}
 private ReusableStroke(Path path,long start,long duration,boolean keeps,int slot,boolean continuation){
  super(path,start,duration,keeps);
  if(slot<0||slot>=LiveTouchSlots.LIMIT)throw new IllegalArgumentException("touch slot");
  this.slot=slot;this.continuation=continuation;
 }
 // Absent from the public SDK. AOSP's gesture serializer invokes these virtual methods.
 public int getId(){identityReads++;return 0x18000000+slot;}
 public int getContinuedStrokeId(){continuationReads++;return continuation?0x18000000+slot:-1;}
 @Override public StrokeDescription continueStroke(Path path,long start,long duration,boolean keeps){
  if(!willContinue())throw new IllegalStateException("touch cannot continue");
  return new ReusableStroke(path,start,duration,keeps,slot,true);
 }
 public int slot(){return slot;}
 public boolean serialized(){return identityReads>0&&continuationReads>0;}
}
