package com.junliang.midibridge;

/** Requested sizes: AudioTrack can clamp them, so callers must read actual sizes. */
final class AudioBufferPlan {
 final int rate,burst,profile;
 AudioBufferPlan(int preferredRate,int preferredBurst,int selectedProfile) {
  rate=preferredRate>=8000 && preferredRate<=192000?preferredRate:48000;
  burst=preferredBurst>=16 && preferredBurst<=4096?preferredBurst:256;
  profile=selectedProfile>=0 && selectedProfile<=2?selectedProfile:0;
 }
 int requestedFrames(){return burst*(profile+2);}
 int blockFrames(){return Math.min(64,burst);}
 int capacityFrames(int minimumBytes){return Math.max((minimumBytes+1)/2,burst*8);}
 int grow(int current,int capacity){return Math.min(Math.min(capacity,Math.max(current,burst*8)),current+burst);}
 static String label(int profile){return profile==1?"平衡":profile==2?"稳健":"低延迟";}
}
