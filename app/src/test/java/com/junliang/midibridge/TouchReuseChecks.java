package com.junliang.midibridge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public final class TouchReuseChecks {
 private static int checks;
 private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 private static final class Pointer {final long version;final int slot;Pointer(long v,int s){version=v;slot=s;}}
 private static final class Screen {
  final LiveTouchState state=new LiveTouchState();final LiveTouchSlots slots=new LiveTouchSlots();
  final HashMap<Integer,Pointer> active=new HashMap<>();final HashSet<Long> starts=new HashSet<>();
  final int[] downs=new int[21],ups=new int[21];long[] previous=new long[21];
  void down(int key){check(state.down(key,key,10)>=0,"physical key accepted");}
  void up(int key){state.up(key);}
  void step(){
   LiveTouchPlan plan=LiveTouchPlan.create(state,previous,20,10);LiveTouchSlots.Batch allocation=slots.plan(plan);
   check(active.size()<=10,"bounded screen pointers");
   for(int i=0;i<plan.parts.size();i++){LiveTouchPlan.Part p=plan.parts.get(i);if(p.kind==LiveTouchPlan.START)continue;Pointer old=active.get(p.key);check(old!=null&&old.version==p.version&&old.slot==allocation.slotAt(i),"continued pointer keeps its original slot");}
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.RELEASE){check(active.remove(p.key)!=null,"release existing pointer");ups[p.key]++;}
   for(int i=0;i<plan.parts.size();i++){LiveTouchPlan.Part p=plan.parts.get(i);if(p.kind!=LiveTouchPlan.START)continue;
    int slot=allocation.slotAt(i);for(Pointer old:active.values())check(old.slot!=slot,"a new note never takes an active slot");
    check(!active.containsKey(p.key),"same-key old touch releases before new onset");
    check(starts.add(p.version),"every physical attack delivered once, no compatibility reattack");
    active.put(p.key,new Pointer(p.version,slot));downs[p.key]++;
   }
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.START&&!p.continues){active.remove(p.key);ups[p.key]++;}
   check(slots.complete(allocation),"valid batch commits");check(!slots.complete(allocation),"duplicate completion rejected");plan.acknowledge(state);
   previous=new long[21];for(LiveTouchPlan.Part p:plan.parts)if(p.continues)previous[p.key]=p.version;
   for(int k=0;k<21;k++)check(slots.slot(k)==(active.containsKey(k)?active.get(k).slot:-1),"committed ownership matches delivered pointers");
  }
  void settle(){step();for(int i=0;state.pending()>0&&i<100;i++)step();check(state.pending()==0,"brief attacks not starved");}
  void end(){state.clear();settle();check(active.isEmpty(),"complete release empties pointer pool");for(int k=0;k<21;k++)check(downs[k]==ups[k],"every DOWN has matching UP");}
 }
 private static void users(){
  Screen s=new Screen();int[][] pairs={{1,14},{3,20},{5,18},{6,16},{9,14}};int[] old=new int[0];
  for(int cycle=0;cycle<200;cycle++)for(int[] next:pairs){for(int k:next)s.down(k);s.settle();for(int k:old)if(k!=next[0]&&k!=next[1])s.up(k);s.settle();old=next;}
  check(s.starts.size()==1801,"all user pair-sequence attacks delivered once");s.end();
  s=new Screen();for(int k:new int[]{0,4,7})s.down(k);s.settle();int high=-1;
  for(int i=0;i<2000;i++){int k=i%2==0?16:20;s.down(k);s.settle();if(high>=0)s.up(high);s.settle();high=k;for(int low:new int[]{0,4,7})check(s.downs[low]==1&&s.ups[low]==0,"held low notes never reattack or release during arpeggio");}
  check(s.starts.size()==2003,"bass and every high arpeggio onset delivered");s.end();
  s=new Screen();s.down(0);s.settle();high=-1;
  for(int i=0;i<2000;i++){int k=1+i%20;s.down(k);s.settle();if(high>=0)s.up(high);s.settle();high=k;check(s.downs[0]==1&&s.ups[0]==0,"held root survives full-range melody");}s.end();
  s=new Screen();s.down(0);s.settle();for(int k=1;k<10;k++)s.down(k);s.settle();
  for(int i=0;i<100;i++){int from=i%2==0?1:10,to=i%2==0?10:1;for(int k=0;k<9;k++)s.up(from+k);for(int k=0;k<9;k++)s.down(to+k);s.settle();check(s.active.size()==10&&s.downs[0]==1&&s.ups[0]==0,"nine slots recycle while the tenth remains held");}s.end();
 }
 private static void ownership(){
  Screen s=new Screen();s.down(0);s.settle();for(int i=0;i<500;i++){s.down(20);s.up(20);s.settle();}check(s.downs[20]==500&&s.ups[20]==500&&s.ups[0]==0,"quick taps close independently of held bass");s.end();
  s=new Screen();check(s.state.down(48,0,10)>0,"first folded owner");s.settle();check(s.state.down(176,0,10)>0,"second folded owner");s.settle();s.state.up(48);s.settle();check(s.active.size()==1&&s.downs[0]==1&&s.ups[0]==0,"folded owners share one retained slot");s.state.up(176);s.settle();check(s.active.isEmpty()&&s.ups[0]==1,"last folded owner releases pointer");
  for(int i=0;i<500;i++){s.down(0);s.settle();s.up(0);s.down(0);s.settle();s.up(0);s.settle();}s.end();
  s=new Screen();s.down(0);s.settle();for(int k=1;k<10;k++)s.down(k);s.settle();check(s.state.down(20,20,10)<0,"eleventh distinct key ignored without resetting held keys");s.settle();check(s.active.size()==10&&s.downs[0]==1,"existing ten keys remain owned");s.end();
  LiveTouchSlots pool=new LiveTouchSlots();LiveTouchState state=new LiveTouchState();state.down(0,0,10);LiveTouchPlan plan=LiveTouchPlan.create(state,new long[21],20,10);LiveTouchSlots.Batch batch=pool.plan(plan);
  pool.clear();check(!pool.complete(batch)&&pool.slot(0)==-1,"cancelled batch cannot restore a touch slot");
  LiveTouchSlots other=new LiveTouchSlots();batch=pool.plan(plan);check(!other.complete(batch),"batch from another session cannot commit");
  state.up(0);check(pool.complete(batch),"in-flight accepted DOWN completes after physical release");plan.acknowledge(state);long[] previous=new long[21];previous[0]=plan.parts.get(0).version;plan=LiveTouchPlan.create(state,previous,20,10);check(plan.parts.get(0).kind==LiveTouchPlan.RELEASE,"next frame releases the now-unowned note");check(pool.complete(pool.plan(plan))&&pool.slot(0)==-1,"release drains in-flight note");
  GestureDispatchGuard guard=new GestureDispatchGuard();long token=guard.begin(0,24);guard.cancel();pool.clear();check(!guard.finish(token),"cancelled callback cannot acknowledge old attacks");
 }
 private static void receiver(){
  TouchProbeTrace trace=new TouchProbeTrace();check(!trace.accept(0,0,new int[]{0,0},new int[]{0,1}),"receiver rejects duplicated IDs");
  trace=new TouchProbeTrace();check(trace.accept(0,0,new int[]{3},new int[]{0}),"root down accepted");check(!trace.accept(2,0,new int[]{4},new int[]{0}),"receiver catches changed pointer ID");
  trace=new TouchProbeTrace();trace.accept(0,0,new int[]{0},new int[]{0});check(!trace.accept(2,0,new int[]{0},new int[]{1}),"receiver catches held touch moving to another key");
  trace=new TouchProbeTrace();trace.accept(0,0,new int[]{0},new int[]{0});check(!trace.accept(3,0,new int[]{0},new int[]{0}),"receiver rejects system cancellation");
  trace=new TouchProbeTrace();trace.accept(0,0,new int[]{0},new int[]{0});trace.accept(1,0,new int[]{0},new int[]{0});check(!trace.accept(0,0,new int[]{0},new int[]{0}),"receiver catches held bass reattack");
  trace=new TouchProbeTrace();trace.accept(0,0,new int[]{0},new int[]{0});int[] down=new int[21],up=new int[21];down[0]=1;
  for(int k=1;k<10;k++){int[] ids=new int[k+1],keys=new int[k+1];for(int i=0;i<=k;i++)ids[i]=keys[i]=i;check(trace.accept(5,k,ids,keys),"receiver accepts ten unique pointers");down[k]++;}
  for(int k=9;k>=0;k--){int[] ids=new int[k+1],keys=new int[k+1];for(int i=0;i<=k;i++)ids[i]=keys[i]=i;check(trace.accept(k==0?1:6,k,ids,keys),"receiver accepts independent releases");up[k]++;}
  check(trace.complete(down,up),"received lifetime counts match expectations");down[20]=1;check(!trace.complete(down,up),"missing note never passes compatibility test");
  TouchReuseScenario scenario=new TouchReuseScenario();Screen s=new Screen();while(scenario.next()){ // use the identical app probe input while independently committing slots
   long[] previous=s.previous;LiveTouchPlan plan=LiveTouchPlan.create(scenario.state,previous,20,10);LiveTouchSlots.Batch allocation=s.slots.plan(plan);check(s.slots.complete(allocation),"probe scenario stays within slot capacity");plan.acknowledge(scenario.state);s.previous=new long[21];for(LiveTouchPlan.Part p:plan.parts)if(p.continues)s.previous[p.key]=p.version;
  }
  check(scenario.index()==scenario.size()&&scenario.downs[0]==1&&scenario.ups[0]==1,"probe root remains a single lifetime");check(Arrays.equals(scenario.downs,scenario.ups)&&!scenario.state.hasWork(),"probe leaves all keys released");for(int k=0;k<21;k++)check(scenario.downs[k]>0,"probe visits every keyboard key");
 }
 public static void run(){checks=0;users();ownership();receiver();System.out.println("PASS: "+checks+" touch reuse/slot ownership/probe assertions");}
 public static void main(String[] args){run();}
}
