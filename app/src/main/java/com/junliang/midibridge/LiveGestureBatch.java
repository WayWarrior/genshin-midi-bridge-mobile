package com.junliang.midibridge;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** The game and the on-device probe build the same native strokes. */
public final class LiveGestureBatch {
 public static final class Held {
  public final long version;public final GestureDescription.StrokeDescription stroke;public final float endX,endY;
  public Held(long v,GestureDescription.StrokeDescription s,float x,float y){version=v;stroke=s;endX=x;endY=y;}
 }
 public final GestureDescription gesture;
 public final HashMap<Integer,Held> tail=new HashMap<>();
 public final int hits;private final ArrayList<ReusableStroke> reused=new ArrayList<>();
 public LiveGestureBatch(LiveTouchPlan plan,LiveTouchSlots.Batch slots,Map<Integer,Held> previous,float[] xs,float[] ys,int width){
  GestureDescription.Builder builder=new GestureDescription.Builder();int hits=0;
  for(int i=0;i<plan.parts.size();i++){
   LiveTouchPlan.Part p=plan.parts.get(i);Held old=p.kind==LiveTouchPlan.START?null:previous.get(p.key);
   if(p.kind!=LiveTouchPlan.START&&(old==null||old.version!=p.version))throw new IllegalStateException("touch version");
   HoldMotion m=HoldMotion.create(xs[p.key],ys[p.key],old==null?xs[p.key]:old.endX,old==null?ys[p.key]:old.endY,old!=null,p.continues,width);
   Path path=new Path();path.moveTo(m.startX,m.startY);if(m.moved())path.lineTo(m.endX,m.endY);
   GestureDescription.StrokeDescription stroke=old==null?(slots==null?new GestureDescription.StrokeDescription(path,p.offset,p.duration,p.continues):new ReusableStroke(path,p.offset,p.duration,p.continues,slots.slotAt(i))):old.stroke.continueStroke(path,0,p.duration,p.continues);
   if(slots!=null){
    if(!(stroke instanceof ReusableStroke)||((ReusableStroke)stroke).slot()!=slots.slotAt(i))throw new IllegalStateException("touch slot mismatch");
    reused.add((ReusableStroke)stroke);
   }
   builder.addStroke(stroke);if(p.continues)tail.put(p.key,new Held(p.version,stroke,m.endX,m.endY));if(p.kind==LiveTouchPlan.START)hits++;
  }
  gesture=builder.build();this.hits=hits;
 }
 public boolean serialized(){for(ReusableStroke s:reused)if(!s.serialized())return false;return !reused.isEmpty();}
}
