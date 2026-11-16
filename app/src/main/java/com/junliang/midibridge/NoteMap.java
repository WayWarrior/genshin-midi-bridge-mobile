package com.junliang.midibridge;
public final class NoteMap {
 public static final int[] NOTES={48,50,52,53,55,57,59,60,62,64,65,67,69,71,72,74,76,77,79,81,83};
 public static int index(int raw,int transpose,boolean fold) {
  int n=raw+transpose;
  if(fold) { while(n<48) n+=12; while(n>83) n-=12; }
  for(int i=0;i<NOTES.length;i++) if(NOTES[i]==n) return i;
  return -1;
 }
 /** accidental: 0=skip, 1=up one semitone, 2=down one semitone. */
 public static int index(int raw,int transpose,boolean fold,int accidental) {
  int n=raw+transpose,pc=Math.floorMod(n,12);
  if(pc==1||pc==3||pc==6||pc==8||pc==10){if(accidental==1)n++;else if(accidental==2)n--;else return -1;}
  return index(n,0,fold);
 }
 public static String label(int n) { String[] names={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"}; return names[n%12]+(n/12-1)+" ["+n+"]"; }
}
