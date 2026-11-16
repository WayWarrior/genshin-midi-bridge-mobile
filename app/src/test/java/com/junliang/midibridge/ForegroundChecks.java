package com.junliang.midibridge;
import java.util.*;
public final class ForegroundChecks {
 private static final String GAME="com.miHoYo.Yuanshen",OTHER="com.example.other",SELF="com.junliang.midibridge";
 private static int checks;
 private static ForegroundResolver.Window w(int id,int type,int layer,boolean f,boolean a,String p){return new ForegroundResolver.Window(id,type,layer,f,a,p);}
 private static void check(String expected,List<ForegroundResolver.Window> windows,String root,int type,String event,int id,String why){String actual=ForegroundResolver.resolve(windows,root,type,event,id);checks++;if(!expected.equals(actual))throw new AssertionError(why+": "+actual);}
 public static void main(String[] args){run();}
 public static void run(){
  checks=0;
  check(GAME,Arrays.asList(w(1,1,1,true,true,GAME)),GAME,1,"",-1,"normal game window");
  check(GAME,Arrays.asList(w(1,1,1,false,false,GAME),w(2,4,9,true,true,SELF)),SELF,4,"",-1,"overlay must not steal detection");
  check(GAME,Arrays.asList(w(1,1,1,true,false,null),w(2,4,9,false,true,SELF)),SELF,4,GAME,1,"null root uses same-window transition");
  check("",Arrays.asList(w(3,1,5,true,true,null)),null,-1,GAME,1,"old game event cannot identify new window");
  check(OTHER,Arrays.asList(w(1,1,1,false,false,GAME),w(3,1,5,true,true,OTHER)),null,-1,GAME,1,"switch apps despite game still visible");
  check(SELF,Arrays.asList(w(4,1,8,true,true,SELF)),SELF,1,GAME,1,"app settings are not an overlay");
  check("com.android.systemui",Arrays.asList(w(1,1,1,true,false,GAME),w(8,2,9,false,true,"com.android.systemui")),null,-1,GAME,1,"notification shade blocks mapping");
  check("[系统窗口]",Arrays.asList(w(1,1,1,true,false,GAME),w(8,2,9,true,false,null)),null,-1,GAME,1,"system root unavailable fails closed");
  check(GAME,Collections.emptyList(),GAME,-1,"",-1,"OEM root-only evidence");
  check(GAME,Collections.emptyList(),null,-1,GAME,1,"OEM transition-only evidence");
  check("",Arrays.asList(w(2,4,9,true,true,SELF)),SELF,4,GAME,1,"overlay-only evidence cannot reuse old event");
  check(OTHER,Collections.emptyList(),OTHER,1,GAME,1,"current root outranks old event");
  check("com.miHoYo.Yuanshen.bilibili",Arrays.asList(w(1,1,1,true,true,"com.miHoYo.Yuanshen.bilibili")),null,-1,"",-1,"channel package returned for explicit binding");
  System.out.println("PASS: "+checks+" foreground regression scenarios");
 }
}
