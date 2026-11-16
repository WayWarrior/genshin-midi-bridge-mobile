package com.junliang.midibridge;
import java.util.*;

/** Reproduce AOSP's empty stationary continuation, then exercise release and missing callbacks. */
public final class TouchPlaybackChecks {
 private static int checks;
 private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void eq(long a,long b,String why){checks++;if(a!=b)throw new AssertionError(why+": "+a+" != "+b);}
 public static void main(String[] args){run();}
 public static void run(){checks=0;
  // AOSP emits MOVE only for coordinate changes, DOWN only for new pointers, UP for endings.
  eq(events(200,300,200,300,false,false),0,"0.5 stationary continued stroke has no MotionEvents");
  for(int fraction=0;fraction<100;fraction++){
   float center=200+fraction/100f,x=center,y=300.25f;
   for(int segment=0;segment<12;segment++){
    HoldMotion m=HoldMotion.create(center,300.25f,x,y,segment>0,true,1200);
    eq(Math.round(m.startX),Math.round(x),"continued first pixel matches previous final pixel");
    eq(Math.round(m.startY),Math.round(y),"continued y matches");
    ok(events(m.startX,m.startY,m.endX,m.endY,segment==0,false)>0,"every held segment has an actual injection event");
    ok(Math.abs(m.endX-center)<=1,"stay inside the same key");x=m.endX;y=m.endY;
   }
   HoldMotion release=HoldMotion.create(center,300.25f,x,y,true,false,1200);
   eq(events(release.startX,release.startY,release.endX,release.endY,false,true),1,"final held release emits UP");
  }
  HoldMotion edge=HoldMotion.create(999.8f,300,999.8f,300,true,true,1000);ok(edge.endX<1000&&edge.endX>=0,"right edge movement stays on-screen");
  List<MidiScore.Gate> taps=new ArrayList<>();taps.add(new MidiScore.Gate(0,0,100,0,24));taps.add(new MidiScore.Gate(1,1,100,95,119));taps.add(new MidiScore.Gate(2,2,100,110,134));
  GestureTimeline.Slice closed=new GestureTimeline(taps).closedSlice(0,100,20);eq(closed.parts.size(),3,"include following tap inside final release tail");eq(closed.end,134,"batch includes full release across former 100ms cut");
  for(GestureTimeline.Part p:closed.parts){ok(!p.continues,"lyre taps never retain a pointer");eq(p.offset,p.gate.startMs,"tap onset preserved across batching boundary");eq(p.offset+p.duration,p.gate.endMs,"tap release preserved across batching boundary");}
  taps.clear();for(int i=0;i<60;i++)taps.add(new MidiScore.Gate(i,i%7,100,i*10,i*10+8));GestureTimeline timeline=new GestureTimeline(taps);long from=0;Set<Integer> played=new HashSet<>();
  while(timeline.nextStart(from)!=Long.MAX_VALUE){from=timeline.nextStart(from);GestureTimeline.Slice s=timeline.closedSlice(from,from+100,4);ok(s.parts.size()<=4,"dense tap stroke budget");for(GestureTimeline.Part p:s.parts){ok(!p.continues,"dense taps fully release");ok(played.add(p.gate.id),"dense note emitted once");}ok(s.end>from,"closed timeline progresses");from=s.end;}eq(played.size(),60,"dense timeline reaches all later notes");
  GestureDispatchGuard guard=new GestureDispatchGuard();long first=guard.begin(1000,100);ok(guard.busy(),"gesture pending");ok(!guard.expire(1699),"do not expire before grace");ok(guard.expire(1700),"missing callback expires");ok(!guard.busy(),"missing callback cannot block forever");ok(!guard.finish(first),"late completion ignored after timeout");
  long release=guard.begin(1700,24);ok(!guard.finish(first),"old cancellation cannot finish release recovery");ok(guard.matches(release),"release still pending");ok(guard.finish(release),"release callback accepted");ok(!guard.finish(release),"duplicate callback ignored");
  long old=guard.begin(2000,100);guard.cancel();long newer=guard.begin(2001,24);ok(!guard.finish(old),"stop invalidates in-flight callback");ok(guard.finish(newer),"new playback not affected by stopped callback");
  long completed=guard.begin(3000,100);ok(guard.finish(completed),"normal completion");ok(!guard.expire(9999),"normal completion removes timeout effect");
  System.out.println("PASS: "+checks+" touch playback/recovery assertions");
 }
 private static int events(float sx,float sy,float ex,float ey,boolean newPointer,boolean release){int count=newPointer?1:0,x=Math.round(sx),y=Math.round(sy);for(int i=0;i<=10;i++){int nx=Math.round(sx+(ex-sx)*i/10f),ny=Math.round(sy+(ey-sy)*i/10f);if(nx!=x||ny!=y)count++;x=nx;y=ny;}return count+(release?1:0);}
}
