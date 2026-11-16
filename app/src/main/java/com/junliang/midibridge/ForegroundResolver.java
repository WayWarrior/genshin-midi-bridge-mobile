package com.junliang.midibridge;
import java.util.List;
/** Pure decision logic: own overlay never overrides the underlying application. */
public final class ForegroundResolver {
 public static final int APP=1,SYSTEM=2,INPUT=3,OVERLAY=4;
 public static final class Window {
  public final int id,type,layer;public final boolean focused,active;public final String pkg;
  public Window(int id,int type,int layer,boolean focused,boolean active,String pkg){this.id=id;this.type=type;this.layer=layer;this.focused=focused;this.active=active;this.pkg=pkg;}
 }
 public static String resolve(List<Window> windows,String rootPkg,int rootType,String eventPkg,int eventId) {
  Window app=null;
  for(Window w:windows){
   if(w.type==SYSTEM && (w.focused||w.active))return value(w.pkg).isEmpty()?"[系统窗口]":w.pkg;
   if(w.type!=APP)continue;
   if(app==null || priority(w)>priority(app) || (priority(w)==priority(app)&&w.layer>app.layer))app=w;
  }
  if(app!=null){
   if(!value(app.pkg).isEmpty())return app.pkg;
   // An event fallback is only valid for the same application window, never a hidden old game.
   if(app.id==eventId && !value(eventPkg).isEmpty())return eventPkg;
   if(rootType==APP && !value(rootPkg).isEmpty())return rootPkg;
   return "";
  }
  if(rootType!=OVERLAY && !value(rootPkg).isEmpty())return rootPkg;
  // Some OEM builds expose no interactive windows. Use app window transition metadata.
  if(windows.isEmpty())return value(eventPkg);
  if(eventId>=0)for(Window w:windows)if(w.id==eventId && w.type==APP)return value(eventPkg);
  return "";
 }
 private static int priority(Window w){return w.focused?2:(w.active?1:0);}
 private static String value(String s){return s==null?"":s;}
}
