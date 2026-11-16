package com.junliang.midibridge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/** Validates received pointer lifetimes, independently of the producer's stroke IDs. */
public final class TouchProbeTrace {
 private final HashMap<Integer,Integer> active=new HashMap<>();
 private final int[] downs=new int[21],ups=new int[21];private String error="";private int maxPointers;
 public boolean accept(int action,int index,int[] ids,int[] keys){
  if(!error.isEmpty())return false;
  if(action==3)return fail("检测触控被系统或手动触屏打断，请松手后重试");
  if(ids.length!=keys.length||ids.length==0||ids.length>10)return fail("触点数量不正确");
  HashSet<Integer> seen=new HashSet<>();
  for(int i=0;i<ids.length;i++)if(ids[i]<0||ids[i]>31||!seen.add(ids[i])||keys[i]<0||keys[i]>=21)return fail("触点编号重复或检测区域受到干扰");
  maxPointers=Math.max(maxPointers,ids.length);
  if(action==0||action==5){
   if(index<0||index>=ids.length||ids.length!=active.size()+1||active.containsKey(ids[index])||(action==0&&(!active.isEmpty()||ids.length!=1)))return fail("新触点起音顺序不正确");
   for(int i=0;i<ids.length;i++)if(i!=index&&!same(ids[i],keys[i]))return fail("保持中的触点发生变化");
   active.put(ids[index],keys[index]);downs[keys[index]]++;
   if(keys[index]==0&&downs[0]!=1)return fail("保持中的低音被重新按下");
  }else if(action==1||action==6||action==2){
   if(ids.length!=active.size())return fail("保持中的触点数量发生变化");
   for(int i=0;i<ids.length;i++)if(!same(ids[i],keys[i]))return fail("保持中的触点编号或位置发生变化");
   if(action!=2){if(index<0||index>=ids.length||(action==1&&ids.length!=1))return fail("松键顺序不正确");active.remove(ids[index]);ups[keys[index]]++;}
  }else return fail("收到非预期的触控事件");
  return true;
 }
 private boolean same(int id,int key){Integer old=active.get(id);return old!=null&&old==key;}
 private boolean fail(String reason){error=reason;return false;}
 public String error(){return error;}
 public boolean complete(int[] expectedDowns,int[] expectedUps){return error.isEmpty()&&active.isEmpty()&&maxPointers==10&&downs[0]==1&&ups[0]==1&&Arrays.equals(downs,expectedDowns)&&Arrays.equals(ups,expectedUps);}
 public boolean rootReleased(){return ups[0]>0;}
 public int active(){return active.size();}
}
