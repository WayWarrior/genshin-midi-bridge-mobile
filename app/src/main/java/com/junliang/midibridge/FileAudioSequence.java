package com.junliang.midibridge;

/** Audio-thread score cursor: render between note edges instead of batching UI messages. */
final class FileAudioSequence {
 interface Sink {void note(int key,int velocity,boolean down);void render(int offset,int frames);}
 private final MidiScore score;private final int rate;private final long resume,startAt,durationFrames;
 private final long[] eventFrames;private final int[] restoreKeys=new int[21],restoreVelocities=new int[21];
 private int eventIndex,restoreCount;private long advanced;private boolean started;
 volatile boolean cancelled,finished;volatile long positionMs,noteOns,startLateMs;
 FileAudioSequence(MidiScore score,int rate,long startAt,long resume){
  this.score=score;this.rate=rate;this.startAt=startAt;this.resume=resume;positionMs=resume;
  durationFrames=Math.max(0,(score.durationMs-resume)*rate/1000);eventFrames=new long[score.events.size()];
  for(int i=0;i<eventFrames.length;i++)eventFrames[i]=Math.max(0,(score.events.get(i).ms-resume)*rate/1000);
  while(eventIndex<score.events.size()&&score.events.get(eventIndex).ms<resume)eventIndex++;
  if(resume>0)for(MidiScore.Gate g:score.holds)if(g.startMs<resume&&g.endMs>resume){restoreKeys[restoreCount]=g.key;restoreVelocities[restoreCount++]=g.velocity;}
 }
 void cancel(){cancelled=true;}
 private void edges(Sink sink){
  while(!cancelled&&eventIndex<eventFrames.length&&eventFrames[eventIndex]<=advanced){
   MidiScore.Event event=score.events.get(eventIndex++);sink.note(event.key,event.velocity,event.down);if(event.down)noteOns++;
  }
 }
 void render(long now,int frames,Sink sink){
  int offset=0;
  if(cancelled){sink.render(0,frames);return;}
  if(!started){
   int prefix=(int)Math.min(frames,Math.max(0,(startAt-now)*rate+999)/1000);
   if(prefix>0){sink.render(0,prefix);offset=prefix;}
   if(offset==frames)return;
   started=true;startLateMs=Math.max(0,now-startAt);
   for(int i=0;i<restoreCount&&!cancelled;i++){sink.note(restoreKeys[i],restoreVelocities[i],true);noteOns++;}
  }
  while(offset<frames&&!cancelled){
   edges(sink);long until=eventIndex<eventFrames.length?eventFrames[eventIndex]-advanced:Long.MAX_VALUE;
   int count=(int)Math.min(frames-offset,until);if(count<=0)continue;
   sink.render(offset,count);advanced+=count;offset+=count;
  }
  edges(sink);
  if(offset<frames)sink.render(offset,frames-offset);
  positionMs=Math.min(score.durationMs,resume+advanced*1000/rate);finished=!cancelled&&advanced>=durationFrames&&eventIndex==eventFrames.length;
 }
}
