package com.junliang.midibridge;

/** AOSP reserves released pointer IDs until the entire continued gesture lifts. */
public final class LivePointerBudget {
 public static final int LIMIT=10;
 private int allocated;
 private static int starts(LiveTouchPlan plan){int n=0;for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.START)n++;return n;}
 private static boolean keeps(LiveTouchPlan plan){for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.KEEP)return true;return false;}
 public boolean needsReconnect(LiveTouchPlan plan){return keeps(plan)&&allocated+starts(plan)>LIMIT;}
 /** Called only after this particular gesture's completion callback. */
 public void completed(LiveTouchPlan plan){
  allocated=(keeps(plan)?allocated:0)+starts(plan);
  boolean retained=false;for(LiveTouchPlan.Part p:plan.parts)if(p.continues){retained=true;break;}
  if(!retained)allocated=0;
 }
 public int allocated(){return allocated;}
 public void clear(){allocated=0;}
}
