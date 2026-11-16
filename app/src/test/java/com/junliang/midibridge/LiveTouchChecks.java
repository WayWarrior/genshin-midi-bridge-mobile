package com.junliang.midibridge;

import java.util.*;

public final class LiveTouchChecks {
 private static int checks;
 private static void check(boolean yes,String detail){checks++;if(!yes)throw new AssertionError(detail);}
 private static final class Screen {
  long[] held=new long[21];int attacks,releases;
  LiveTouchPlan step(LiveTouchState state){return step(state,20);}
  LiveTouchPlan step(LiveTouchState state,int strokeLimit){
   LiveTouchPlan plan=LiveTouchPlan.create(state,held,strokeLimit,10);
   check(plan.parts.size()<=strokeLimit,"stroke capacity");
   HashSet<Integer> oldKeys=new HashSet<>();int active=0;
   for(long v:held)if(v!=0)active++;
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind!=LiveTouchPlan.START){
    check(held[p.key]==p.version,"continuation identity");check(oldKeys.add(p.key),"one continuation per old pointer");
    if(p.kind==LiveTouchPlan.RELEASE){check(!p.continues&&p.duration==1,"release is closed");active--;releases++;}
    else check(p.continues&&p.offset==0,"other held keys continue");
   }
   int old=0;for(long v:held)if(v!=0)old++;
   check(old==oldKeys.size(),"every retained pointer is continued or released");
   if(old>0){int firstStep=0;for(LiveTouchPlan.Part p:plan.parts)if(p.offset==0)firstStep++;check(firstStep==old,"AOSP first continuation step must contain exactly the old pointers");}
   int peak=active;
   for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.START){
    if(released(plan))check(p.offset>=2,"new down follows old up");
    check(p.duration>0&&p.offset+p.duration<=plan.duration,"bounded new stroke");active++;peak=Math.max(peak,active);attacks++;
   }
   check(peak<=10,"pointer capacity including queued brief taps");
   long[] next=new long[21];for(LiveTouchPlan.Part p:plan.parts)if(p.continues){check(next[p.key]==0,"one live pointer per key");next[p.key]=p.version;}
   plan.acknowledge(state);held=next;return plan;
  }
  boolean empty(){for(long v:held)if(v!=0)return false;return true;}
 }
 private static boolean released(LiveTouchPlan plan){for(LiveTouchPlan.Part p:plan.parts)if(p.kind==LiveTouchPlan.RELEASE)return true;return false;}
 private static int parts(LiveTouchPlan plan,int kind){int n=0;for(LiveTouchPlan.Part p:plan.parts)if(p.kind==kind)n++;return n;}
 private static void settled(LiveTouchState state,Screen screen){
  for(int i=0;state.pending()!=0&&i<70;i++)screen.step(state);
  screen.step(state); // Pending releases also need a frame when there are no queued attacks.
  for(int k=0;k<21;k++)check(screen.held[k]==state.version(k),"settled keys follow physical key state");
  check(state.pending()==0,"all quick attacks eventually delivered");
 }
 public static void run(){
  checks=0;LiveTouchState s=new LiveTouchState();Screen screen=new Screen();
  check(s.down(60,7,10)==1,"first key accepted");screen.step(s);long a=screen.held[7];
  for(int i=0;i<200;i++){LiveTouchPlan plan=screen.step(s);check(parts(plan,LiveTouchPlan.START)==0&&screen.held[7]==a,"held key does not repeatedly click");}
  check(screen.attacks==1,"long hold has one down");
  s.down(64,9,10);LiveTouchPlan chord=screen.step(s);
  check(parts(chord,LiveTouchPlan.KEEP)==1&&parts(chord,LiveTouchPlan.START)==1,"second down keeps first held");
  for(LiveTouchPlan.Part part:chord.parts)if(part.kind==LiveTouchPlan.START)check(part.offset>0,"new chord key joins after initial continuation validation");
  s.up(60);LiveTouchPlan off=screen.step(s);check(parts(off,LiveTouchPlan.RELEASE)==1&&screen.held[9]!=0&&screen.held[7]==0,"release first while second remains down");
  s.up(64);screen.step(s);check(screen.empty(),"last key releases");

  s=new LiveTouchState();screen=new Screen();s.down(48,0,10);s.down(60,0,10);s.down(188,0,10);screen.step(s);
  check(s.count()==1&&screen.attacks==1,"folded notes/channels share one pointer");
  s.up(48);s.up(60);screen.step(s);check(screen.held[0]!=0,"folded key waits for last source");
  s.up(188);screen.step(s);check(screen.empty(),"folded last source releases");
  s.down(48,0,10);screen.step(s);a=screen.held[0];
  check(s.down(48,0,10)==0,"repeated Note On is idempotent");screen.step(s);check(screen.held[0]==a,"duplicate down retains pointer");
  s.up(48);screen.step(s);check(screen.empty(),"no refcount leak after duplicate on");check(!s.up(48),"unknown off harmless");

  // Excess Note On is ignored, without a panic: existing keys keep their identities and release.
  s=new LiveTouchState();screen=new Screen();
  for(int k=0;k<10;k++)check(s.down(200+k,k,10)==1,"ten-key chord accepted");
  screen.step(s);long[] tenHeld=screen.held.clone();
  for(int k=10;k<21;k++)check(s.down(200+k,k,10)==-1,"overflow ignored");
  screen.step(s);check(Arrays.equals(tenHeld,screen.held)&&s.count()==10,"overflow leaves ten held pointers unchanged");
  for(int k=10;k<21;k++)check(!s.up(200+k),"ignored off cannot release accepted keys");
  s.down(500,0,10);check(s.count()==10,"folded alias still accepted at capacity");
  s.up(200);screen.step(s);check(screen.held[0]!=0,"alias protects held key after overflow");
  s.up(500);s.up(201);screen.step(s);check(s.count()==8,"existing keys release independently");
  check(s.down(210,10,10)==1,"previously ignored key accepted on a fresh press");screen.step(s);
  for(int k=2;k<10;k++)s.up(200+k);s.up(210);settled(s,screen);
  check(screen.empty()&&!s.hasWork(),"overflow chord fully releases without clearing mapping state");
  check(s.down(300,12,10)==1,"next chord works without app restart");screen.step(s);s.up(300);screen.step(s);
  check(screen.empty(),"next chord fully releases");

  s=new LiveTouchState();screen=new Screen();s.down(60,7,10);s.down(64,9,10);screen.step(s);a=screen.held[7];
  s.up(60);s.down(60,7,10);LiveTouchPlan reattack=screen.step(s);
  check(parts(reattack,LiveTouchPlan.RELEASE)==1&&parts(reattack,LiveTouchPlan.START)==1&&parts(reattack,LiveTouchPlan.KEEP)==1,"rapid repeat releases/represses only its own key");
  check(screen.held[7]!=a&&screen.held[9]!=0,"new attack identity and unchanged companion");

  s=new LiveTouchState();screen=new Screen();
  for(int i=0;i<5;i++){s.down(60,7,10);s.up(60);}check(s.pending()==5&&!s.held(7),"brief notes queued before first dispatch");
  settled(s,screen);check(screen.attacks==5&&screen.empty(),"five quick on/off pairs produce five complete taps");
  s.down(60,7,10);long[] before=screen.held.clone();LiveTouchPlan inFlight=LiveTouchPlan.create(s,before,20,10);
  s.up(60);s.down(60,7,10);s.up(60);inFlight.acknowledge(s);
  for(LiveTouchPlan.Part p:inFlight.parts)if(p.continues)screen.held[p.key]=p.version;
  check(s.pending()==1,"callback acknowledges only dispatched attack");settled(s,screen);check(screen.empty(),"in-flight rapid taps end released");

  s=new LiveTouchState();screen=new Screen();for(int k=0;k<10;k++)s.down(k,k,10);screen.step(s);
  check(s.down(11,11,10)==-1&&s.count()==10&&!s.held(11),"eleventh distinct held key rejected atomically");
  check(s.down(0,10,10)==1&&s.count()==10&&!s.held(0),"source replacement allowed at capacity");screen.step(s);settled(s,screen);
  for(int k=1;k<=9;k++)s.up(k);s.up(0);screen.step(s);check(screen.empty(),"ten-key chord releases");
  for(int k=0;k<10;k++)s.down(k,k,10);screen.step(s);
  for(int k=0;k<10;k++){s.up(k);s.down(k,k+10,10);}LiveTouchPlan replace=screen.step(s);
  check(replace.parts.size()==20&&parts(replace,LiveTouchPlan.RELEASE)==10&&parts(replace,LiveTouchPlan.START)==10,"ten old ups precede ten new downs");settled(s,screen);
  for(int k=0;k<10;k++){s.up(k);s.down(k,k,10);}LiveTouchPlan stage=screen.step(s,10);
  check(parts(stage,LiveTouchPlan.START)==0&&screen.empty()&&s.pending()==10,"small stroke budget stages release before attack");screen.step(s,10);settled(s,screen);

  s=new LiveTouchState();screen=new Screen();
  for(int k=0;k<21;k++){s.down(k,k,10);s.up(k);}for(int k=0;k<10;k++)s.down(100+k,k,10);
  settled(s,screen);check(screen.attacks==31&&s.count()==10,"queued ended notes coexist with full held chord without exceeding pointer count");
  s.clear();screen.step(s);check(screen.empty()&&!s.hasWork(),"panic clears keys and queued attacks");
  for(int i=0;i<64;i++){check(s.down(1,1,10)==1,"bounded pending accepted");s.up(1);}
  check(s.down(1,1,10)==-1&&s.count()==0,"pending overload rejected atomically");s.clear();

  final LiveTouchState parsed=new LiveTouchState();MidiParser parser=new MidiParser((status,n,velocity)->{
   int kind=status&240,source=(status&15)*128+n;
   if(kind==144&&velocity>0)parsed.down(source,NoteMap.index(n,0,false),10);
   else if(kind==128||(kind==144&&velocity==0))parsed.up(source);
  });
  parser.feed(new byte[]{(byte)0x90,60,100,64,100},0,5);check(parsed.count()==2,"running status chord");
  parser.feed(new byte[]{60,0},0,2);check(parsed.count()==1&&!parsed.held(7),"velocity-zero Note On is release");
  parser.feed(new byte[]{(byte)0x80,64,50},0,3);check(parsed.count()==0,"ordinary Note Off releases");

  Random random=new Random(701);s=new LiveTouchState();screen=new Screen();int[] sources=new int[64];Arrays.fill(sources,-1);
  for(int i=0;i<5000;i++){
   int id=random.nextInt(sources.length);
   if(random.nextBoolean()){int key=random.nextInt(21);int accepted=s.down(id,key,10);if(accepted>=0)sources[id]=key;}
   else{s.up(id);sources[id]=-1;}
   if(i%3==0)screen.step(s);
   int unique=0;for(int key=0;key<21;key++){boolean physical=false;for(int source:sources)if(source==key)physical=true;check(s.held(key)==physical,"random source ownership");if(physical)unique++;}
   check(s.count()==unique&&unique<=10,"random held count");
  }
  settled(s,screen);for(int id=0;id<sources.length;id++)s.up(id);settled(s,screen);screen.step(s);check(screen.empty(),"random stream final release");
  System.out.println("Live touch checks passed: "+checks);
 }
 public static void main(String[] args){run();}
}
