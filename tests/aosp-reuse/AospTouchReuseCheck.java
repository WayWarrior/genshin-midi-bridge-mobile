import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.IAccessibilityServiceClient;
import android.os.Handler;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import com.android.server.accessibility.BaseEventStreamTransformation;
import com.android.server.accessibility.MotionEventInjector;
import com.junliang.midibridge.*;
import java.util.*;

/** Actual AOSP Java control flow with platform substitutes; not Android runtime/device validation. */
public final class AospTouchReuseCheck {
 static int assertions;
 static void check(boolean v,String m){assertions++;if(!v)throw new AssertionError(m);}
 static final class Screen {
  final Handler handler=new Handler();final MotionEventInjector injector=new MotionEventInjector(handler);
  final TouchProbeTrace trace=new TouchProbeTrace();final LiveTouchState state;
  final LiveTouchSlots slots=new LiveTouchSlots();final HashMap<Integer,LiveGestureBatch.Held> held=new HashMap<>();
  final float[] xs=new float[21],ys=new float[21];final int[] downs=new int[21],ups=new int[21];
  final IAccessibilityServiceClient client=(seq,ok)->{check(ok,"framework callback completed");};
  int sequence,events,maxAliases,maxPointers;boolean traceRequired=true;
  Screen(LiveTouchState state){this.state=state;for(int k=0;k<21;k++){xs[k]=100+k*20;ys[k]=100;}
   injector.setNext(new BaseEventStreamTransformation(){@Override public void onMotionEvent(MotionEvent e,MotionEvent raw,int flags){
    events++;maxPointers=Math.max(maxPointers,e.ids.length);int[] keys=new int[e.ids.length];for(int i=0;i<keys.length;i++)keys[i]=Math.round((e.xs[i]-100)/20);
    boolean accepted=trace.accept(e.getActionMasked(),e.action>>>8,e.ids,keys);if(traceRequired)check(accepted,"received pointer lifetimes: "+trace.error());
    int action=e.getActionMasked(),ix=e.action>>>8;if(action==0||action==5)downs[keys[ix]]++;if(action==1||action==6)ups[keys[ix]]++;
   }});
  }
  void down(int key){check(state.down(key,key,10)>=0,"physical key accepted");}
  void up(int key){state.up(key);}
  void step(boolean reuse)throws Exception{
   long[] previous=new long[21];for(Integer k:held.keySet())previous[k]=held.get(k).version;
   LiveTouchPlan plan=LiveTouchPlan.create(state,previous,20,10);if(plan.parts.isEmpty())return;
   LiveTouchSlots.Batch allocation=reuse?slots.plan(plan):null;LiveGestureBatch batch=new LiveGestureBatch(plan,allocation,held,xs,ys,1000);
   if(reuse)check(!batch.serialized(),"serialization cannot be declared before framework getter calls");
   List<GestureDescription.GestureStep> steps=GestureDescription.MotionEventGenerator.getGestureStepsFromGestureDescription(batch.gesture,16);
   if(reuse)check(batch.serialized(),"AOSP calls production identity getters compiled against public SDK");
   SystemClock.now+=50;injector.injectEvents(steps,client,++sequence,0);while(!handler.pending.isEmpty())injector.handleMessage(handler.pending.remove(0));
   java.lang.reflect.Field field=MotionEventInjector.class.getDeclaredField("mStrokeIdToPointerId");field.setAccessible(true);maxAliases=Math.max(maxAliases,((SparseIntArray)field.get(injector)).size());
   if(reuse){check(slots.complete(allocation),"production slot batch commits");check(maxAliases<=10,"system alias table stays bounded");}
   plan.acknowledge(state);held.clear();held.putAll(batch.tail);
  }
  void settle()throws Exception{step(true);while(state.pending()>0)step(true);}
  void rootHeld(){check(downs[0]==1&&ups[0]==0,"bass never lifted or reattacked");}
  void end()throws Exception{state.clear();step(true);check(held.isEmpty(),"all production fingers released");for(int k=0;k<21;k++)check(downs[k]==ups[k],"every delivered note has matching release");}
 }
 static void probeScenario()throws Exception{
  TouchReuseScenario scenario=new TouchReuseScenario();Screen s=new Screen(scenario.state);
  while(scenario.next()){s.settle();if(scenario.index()<scenario.size())s.rootHeld();}
  check(s.trace.complete(scenario.downs,scenario.ups),"app on-device scenario passes actual AOSP event receiver");check(s.maxPointers==10&&s.maxAliases==10,"all ten slots tested without historical alias growth");
  System.out.println("PASS app compatibility scenario: "+scenario.size()+" packets / "+s.events+" native events / max aliases "+s.maxAliases);
 }
 static void arp()throws Exception{Screen s=new Screen(new LiveTouchState());for(int k:new int[]{0,4,7})s.down(k);s.settle();int old=-1;
  for(int i=0;i<2000;i++){int k=i%2==0?16:20;s.down(k);s.settle();if(old>=0){s.up(old);s.settle();}old=k;s.rootHeld();for(int low:new int[]{4,7})check(s.downs[low]==1&&s.ups[low]==0,"held chord remains continuous");}
  check(s.downs[16]+s.downs[20]==2000&&s.maxAliases==5,"every arpeggio onset uses the five existing slots");s.end();System.out.println("PASS held chord + 2000 arpeggio attacks: aliases="+s.maxAliases);
 }
 static void range()throws Exception{Screen s=new Screen(new LiveTouchState());s.down(0);s.settle();int old=-1;
  for(int i=0;i<2000;i++){int k=1+i%20;s.down(k);s.settle();if(old>=0){s.up(old);s.settle();}old=k;s.rootHeld();}
  check(s.maxAliases==3,"full range reuses only three live slot identities");s.end();System.out.println("PASS held root + 2000 full-range attacks: aliases="+s.maxAliases);
 }
 static void pairs()throws Exception{Screen s=new Screen(new LiveTouchState());s.traceRequired=false;int[][] pairs={{1,14},{3,20},{5,18},{6,16},{9,14}};int[] old=new int[0];
  // The root-specific probe receiver is bypassed for this user sequence; validate every event independently.
  final Set<Integer> active=new HashSet<>();s.injector.setNext(new BaseEventStreamTransformation(){@Override public void onMotionEvent(MotionEvent e,MotionEvent raw,int flags){Set<Integer> ids=new HashSet<>();for(int id:e.ids)check(ids.add(id),"pair sequence has distinct native IDs");int a=e.getActionMasked(),ix=e.action>>>8;if(a==0||a==5)check(active.add(e.ids[ix]),"pair onset creates a free pointer");if(a==1||a==6)check(active.remove(e.ids[ix]),"pair release closes existing pointer");if(a==3)throw new AssertionError("pair cancelled");}});
  for(int i=0;i<1000;i++){int[] next=pairs[i%5];for(int k:next)s.down(k);s.settle();for(int k:old)if(k!=next[0]&&k!=next[1])s.up(k);s.settle();old=next;}
  s.state.clear();s.step(true);check(active.isEmpty()&&s.maxAliases<=4,"pair chain terminates without aliases accumulating");System.out.println("PASS user pair sequence: 1000 transitions / aliases="+s.maxAliases);
 }
 static void cancelled()throws Exception{LiveTouchState state=new LiveTouchState();state.down(0,0,10);LiveTouchPlan plan=LiveTouchPlan.create(state,new long[21],20,10);LiveTouchSlots slots=new LiveTouchSlots();LiveTouchSlots.Batch pending=slots.plan(plan);GestureDispatchGuard guard=new GestureDispatchGuard();long token=guard.begin(0,24);guard.cancel();slots.clear();if(guard.finish(token)){slots.complete(pending);plan.acknowledge(state);}check(slots.slot(0)==-1&&state.pending()==1,"cancelled callback cannot restore ownership or consume unsent attack");}
 public static void main(String[]args)throws Exception{probeScenario();arp();range();pairs();cancelled();System.out.println("PASS: "+assertions+" AOSP source/production class integration assertions. JVM substitutes; not HarmonyOS/ART/game.");}
}
