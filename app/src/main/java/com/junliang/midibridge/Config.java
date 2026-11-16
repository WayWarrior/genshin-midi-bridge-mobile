package com.junliang.midibridge;
import android.content.*;
public final class Config {
 public static final String PREF="bridge";
 public static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,0);}
 private static String reuseKey(){return "v18/"+android.os.Build.VERSION.SDK_INT+"/"+android.os.Build.FINGERPRINT;}
 public static boolean reusePassed(Context c){return reuseKey().equals(prefs(c).getString("live_reuse_verified",""));}
 public static String reuseStatus(Context c){return reusePassed(c)?"触点复用检测通过，勾选后用于实时长按":prefs(c).getBoolean("live_reuse_checking",false)?"上次检测尚未完成，请重新检测":prefs(c).getString("live_reuse_result","触点复用：尚未检测，请点下方按钮");}
 public static void beginReuseCheck(Context c){prefs(c).edit().remove("live_reuse_verified").putBoolean("live_reuse_checking",true).putString("live_reuse_result","正在检测长按兼容性…").apply();}
 public static void finishReuseCheck(Context c,boolean passed,String detail){prefs(c).edit().putString("live_reuse_verified",passed?reuseKey():"").putBoolean("live_reuse_checking",false).putBoolean("live_reuse",passed).putBoolean("live_hold",passed||prefs(c).getBoolean("live_hold",true)).putString("live_reuse_result",detail+(passed?"":"；未启用触点复用，仍可使用兼容长按或短点按")).putLong("live_reuse_checked",Math.max(System.currentTimeMillis(),prefs(c).getLong("live_reuse_checked",0)+1)).apply();}
 public static void migrateFileTouch(Context c){android.content.SharedPreferences p=prefs(c);if(!p.getBoolean("file_touch_v16",false))p.edit().putBoolean("midi_hold",false).putBoolean("file_touch_v6",true).putBoolean("file_touch_v16",true).apply();}
 public static boolean calibrated(Context c,int width,int height) {
  android.content.SharedPreferences p=prefs(c);
  if(p.getInt("width",0)!=width || p.getInt("height",0)!=height)return false;
  for(int i=0;i<21;i++)if(!p.contains("x"+i) || !p.contains("y"+i))return false;
  return true;
 }
 public static boolean target(Context c,String pkg) {
  if(pkg==null)return false;
  String custom=prefs(c).getString("package","").trim();
  return pkg.equals("com.miHoYo.Yuanshen") || pkg.equals("com.miHoYo.GenshinImpact") || (!custom.isEmpty() && pkg.equals(custom));
 }
}
