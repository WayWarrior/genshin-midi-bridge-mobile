package com.junliang.midibridge;
import java.util.*;

/** Models AOSP's historical pointer-ID reservation, not merely simultaneous key count. */
public final class LivePointerChecks {
 private static int checks;
 private static void check(boolean yes,String detail){checks++;if(!yes)throw new AssertionError(detail);}
 private static final class Pointer {final long version;final int id;Pointer(long version,int id){this.version=version;this.id=id;}}
 private static final class Injector {
  final Map<Integer,Pointer> active=new LinkedHashMap<>();final boolean[] reserved=new boolean[11];
  int invalid,maxActive;
  private void lift(int key){check(active.remove(key)!=null,"release existing system pointer");if(active.isEmpty())Arrays.fill(reserved,false);}
  void apply(LiveTouchPlan plan){
   int old=active.size();for(LiveTouchPlan.Part p:plan.parts)if(p.kind!=LiveTouchPlan.START){Pointer pointer=active.get(p.key);check(pointer!=null&&pointer.version==p.version,"continued key identity retained");}
   int continued=0;for(LiveTouchPlan.Part p:plan.parts)if(p.kind!=LiveTouchPlan.START)continued++;check(old==continued,"all previous system pointers represented");
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.RELEASE)lift(p.key);
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.START){
    check(!active.containsKey(p.key),"old key releases before its new down");
    int id=0;while(id<10&&reserved[id])id++; // AOSP returns 10 when IDs 0..9 are all reserved.
    for(Pointer pointer:active.values())if(pointer.id==id)invalid++;
    reserved[id]=true;active.put(p.key,new Pointer(p.version,id));maxActive=Math.max(maxActive,active.size());
   }
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.START&&!p.continues)lift(p.key);
  }
  int reservedCount(){int count=0;for(boolean used:reserved)if(used)count++;return count;}
 }
 private static final class Screen {
  final LiveTouchState state=new LiveTouchState();final LivePointerBudget budget=new LivePointerBudget();final Injector injector=new Injector();
  final Set<Long> delivered=new HashSet<>();long[] previous=new long[21];int reconnects,restores,steps;
  final boolean compatible;
  Screen(boolean compatible){this.compatible=compatible;}
  void down(int key){down(NoteMap.NOTES[key],key);}
  void down(int source,int key){check(state.down(source,key,10)>=0,"input accepted within pointer and queue limits");}
  void up(int key){state.up(NoteMap.NOTES[key]);}
  LiveTouchPlan step(){
   LiveTouchPlan plan=LiveTouchPlan.create(state,previous,20,10);
   if(compatible&&budget.needsReconnect(plan)){int pending=state.pending();plan=LiveTouchPlan.releaseAll(previous,state.revision());reconnects++;for(LiveTouchPlan.Part p:plan.parts)check(p.kind==LiveTouchPlan.RELEASE&&!p.continues,"checkpoint contains only closed old pointers");plan.acknowledge(state);check(state.pending()==pending,"checkpoint does not discard queued attacks");}
   injector.apply(plan);
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.START){if(state.nextAttack(p.key)==p.version)check(delivered.add(p.version),"each physical attack sent once");else{restores++;check(p.continues&&state.version(p.key)==p.version,"restore only a still physically held key");}}
   plan.acknowledge(state);if(compatible){budget.completed(plan);check(budget.allocated()==injector.reservedCount(),"budget tracks system historical reservation");check(budget.allocated()<=10&&injector.invalid==0,"no duplicated or overflowed system pointer ID");}
   previous=new long[21];for(LiveTouchPlan.Part p:plan.parts)if(p.continues)previous[p.key]=p.version;
   steps++;return plan;
  }
  void settle(){step();for(int i=0;state.pending()>0&&i<100;i++)step();step();check(state.pending()==0,"new onsets never starve behind held chord");for(int k=0;k<21;k++)check(previous[k]==state.version(k),"screen matches all physical holds after callback");}
  void release(){state.clear();settle();check(injector.active.isEmpty()&&budget.allocated()==0,"all fingers lift and clear reservation");}
 }
 private static final int[][] PAIRS={{1,14},{3,20},{5,18},{6,16},{9,14}};
 private static Screen sequence(boolean compatible,int cycles){
  Screen s=new Screen(compatible);int[] previous=new int[0];
  for(int i=0;i<cycles*PAIRS.length;i++){int[] next=PAIRS[i%PAIRS.length];for(int key:next)s.down(key);s.settle();for(int key:previous)if(key!=next[0]&&key!=next[1])s.up(key);s.settle();previous=next;}
  return s;
 }
 private static Screen arpeggio(boolean compatible,int cycles){
  Screen s=new Screen(compatible);for(int key:new int[]{0,4,7})s.down(key);s.settle();
  int old=-1;for(int i=0;i<cycles*2;i++){int key=i%2==0?16:20;s.down(key);s.settle();if(old>=0){s.up(old);s.settle();}old=key;for(int held:new int[]{0,4,7})check(s.state.held(held),"bass chord remains physically held through arpeggio");}
  return s;
 }
 public static void run(){
  checks=0;
  Screen legacy=sequence(false,8);check(legacy.injector.invalid>0,"old manual sequence can duplicate system IDs");check(legacy.injector.maxActive<=4,"old failure occurs below simultaneous ten-key limit");
  Screen fixed=sequence(true,100);check(fixed.injector.invalid==0&&fixed.reconnects>0,"reported pair sequence remains valid with reconnects");check(fixed.delivered.size()==901,"all pair attacks delivered; shared high 1 stays held across cycle boundary");fixed.release();
  legacy=arpeggio(false,20);check(legacy.injector.invalid>0&&legacy.injector.maxActive<=5,"old held bass plus high arpeggio reproduces ID collision");
  fixed=arpeggio(true,150);check(fixed.delivered.size()==303,"three bass attacks and every high 3/high 7 delivered");check(fixed.reconnects>0&&fixed.restores>0,"held bass restored after complete lift");fixed.release();
  // High 2 combined with every other key; a low source keeps the old chain alive.
  fixed=new Screen(true);fixed.down(128+48,0);fixed.settle();
  for(int repeat=0;repeat<8;repeat++)for(int key=0;key<21;key++)if(key!=15){fixed.down(15);fixed.down(key);fixed.settle();fixed.up(15);fixed.up(key);fixed.settle();check(fixed.state.held(0),"folded/aliased low source survives high 2 combination");}
  check(fixed.delivered.size()==313,"every distinct high 2 combination onset delivered");check(fixed.reconnects>0,"high 2 combinations reset before exhaustion");fixed.release();
  // Continuations reserve no additional pointer ID until a new physical attack joins.
  fixed=new Screen(true);fixed.down(0);fixed.down(4);fixed.settle();for(int i=0;i<1200;i++)fixed.step();check(fixed.reconnects==0&&fixed.budget.allocated()==2,"static chord is not needlessly retriggered");fixed.up(0);fixed.up(4);fixed.settle();check(fixed.budget.allocated()==0,"natural all-up resets budget");fixed.down(20);fixed.settle();check(fixed.budget.allocated()==1,"next standalone high 7 has fresh ID");fixed.release();
  // All old keys naturally lift before new keys join: no artificial checkpoint needed.
  fixed=new Screen(true);for(int cycle=0;cycle<10;cycle++){for(int key=0;key<10;key++)fixed.down(key);fixed.settle();for(int key=0;key<10;key++)fixed.up(key);for(int key=10;key<20;key++)fixed.down(key);fixed.settle();for(int key=10;key<20;key++)fixed.up(key);fixed.settle();}
  check(fixed.reconnects==0&&fixed.delivered.size()==200,"natural closed ten-key transitions reset before new downs");
  // Source releases arriving while the all-up gesture is pending must not be restored.
  LiveTouchState state=new LiveTouchState();state.down(48,0,10);state.down(55,4,10);long[] held=new long[21];held[0]=state.version(0);held[4]=state.version(4);state.acknowledge(0,held[0]);state.acknowledge(4,held[4]);
  LiveTouchPlan pending=LiveTouchPlan.releaseAll(held,state.revision());state.up(48);state.down(83,20,10);state.up(83);int queued=state.pending();pending.acknowledge(state);check(state.pending()==queued&&!state.held(0),"in-flight checkpoint preserves updated ownership and brief attack");
  LiveTouchPlan rebuilt=LiveTouchPlan.create(state,new long[21],20,10);boolean restoredEnded=false,highPlayed=false;for(LiveTouchPlan.Part p:rebuilt.parts){if(p.key==0)restoredEnded=p.continues;if(p.key==20)highPlayed=p.kind==LiveTouchPlan.START&&!p.continues;}check(!restoredEnded&&highPlayed,"released bass not held again and queued high tap retained");
  // A stale callback cannot mutate the current generation's pointer accounting.
  GestureDispatchGuard guard=new GestureDispatchGuard();long token=guard.begin(100,24);LivePointerBudget budget=new LivePointerBudget();guard.cancel();budget.clear();if(guard.finish(token))budget.completed(rebuilt);check(budget.allocated()==0,"cancelled callback cannot restore stale ID budget");
  System.out.println("PASS: "+checks+" live pointer reservation assertions");
 }
 public static void main(String[] args){run();}
}
