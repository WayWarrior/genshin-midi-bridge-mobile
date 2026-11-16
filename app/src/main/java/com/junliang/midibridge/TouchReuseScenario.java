package com.junliang.midibridge;

import java.util.ArrayList;

/** Physical key sequence for the app's compatibility test; root key stays down until the last step. */
public final class TouchReuseScenario {
 public final LiveTouchState state=new LiveTouchState();
 public final int[] downs=new int[21],ups=new int[21];
 private final ArrayList<int[]> steps=new ArrayList<>();private int index;
 public TouchReuseScenario(){
  steps.add(new int[]{1,5,8});int previous=-1;
  for(int i=0;i<10;i++){int k=i%2==0?16:20;steps.add(new int[]{k+1});if(previous>=0)steps.add(new int[]{-previous-1});previous=k;}
  steps.add(new int[]{-5,-8,-previous-1});previous=-1;
  for(int k=1;k<21;k++){steps.add(new int[]{k+1});if(previous>=0)steps.add(new int[]{-previous-1});previous=k;}
  steps.add(new int[]{-previous-1});int[] chord=new int[9];for(int i=0;i<9;i++)chord[i]=i+2;steps.add(chord);
  for(int c=0;c<4;c++){int from=c%2==0?1:10,to=c%2==0?10:1;int[] change=new int[18];for(int i=0;i<9;i++){change[i]=-from-i-1;change[9+i]=to+i+1;}steps.add(change);}
  int[] release=new int[9];for(int i=0;i<9;i++)release[i]=-i-2;steps.add(release);
  for(int i=0;i<4;i++)steps.add(new int[]{21,-21});steps.add(new int[]{-1});
 }
 public boolean next(){
  if(index>=steps.size())return false;
  if(state.pending()!=0)throw new IllegalStateException("test attacks still pending");
  for(int a:steps.get(index++)){
   int key=Math.abs(a)-1;
   if(a>0){boolean held=state.held(key);int changed=state.down(key,key,10);if(changed<0)throw new IllegalStateException("test key limit");if(!held&&changed>0)downs[key]++;}
   else{boolean held=state.held(key);if(state.up(key)&&held)ups[key]++;}
  }
  return true;
 }
 public int index(){return index;}
 public int size(){return steps.size();}
}
