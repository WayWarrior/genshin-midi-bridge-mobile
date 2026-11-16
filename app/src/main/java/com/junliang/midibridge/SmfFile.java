package com.junliang.midibridge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded Standard MIDI File reader. Times are relative microseconds, never quantized. */
public final class SmfFile {
 public static final int MAX_BYTES=16*1024*1024,MAX_EVENTS=200000,MAX_TRACKS=128;
 public static final long MAX_US=6L*60*60*1000000;
 public static final class Note {
  public final long startUs,endUs,startTick,endTick;public final int track,channel,pitch,velocity;
  Note(long start,long end,long onTick,long offTick,int t,int c,int p,int v){startUs=start;endUs=Math.max(start+1,end);startTick=onTick;endTick=Math.max(onTick,offTick);track=t;channel=c;pitch=p;velocity=v;}
 }
 public final List<Note> notes;public final String[] trackNames;public final long durationUs,durationTicks;
 public final int format,division,tempoChanges,unclosedNotes;
 private SmfFile(List<Note> n,String[] names,long duration,long ticks,int f,int d,int tempi,int unclosed){notes=Collections.unmodifiableList(n);trackNames=names;durationUs=duration;durationTicks=ticks;format=f;division=d;tempoChanges=tempi;unclosedNotes=unclosed;}
 private static final class Event {
  long tick,us;int order,track,status,a,b;
  Event(long t,int o,int tr,int s,int x,int y){tick=t;order=o;track=tr;status=s;a=x;b=y;}
 }
 private static final class Open {Event on;Open(Event e){on=e;}}
 private static final class Reader {
  byte[] bytes;int pos,end;
  Reader(byte[] b,int start,int limit){bytes=b;pos=start;end=limit;}
  int u8()throws IOException{if(pos>=end)throw new IOException("MIDI 文件被截断");return bytes[pos++]&255;}
  int u16()throws IOException{return (u8()<<8)|u8();}
  long u32()throws IOException{return ((long)u16()<<16)|u16();}
  long vlq()throws IOException{long v=0;for(int i=0;i<4;i++){int b=u8();v=(v<<7)|(b&127);if(b<128)return v;}throw new IOException("MIDI 可变长度数值超过4字节");}
  void skip(long n)throws IOException{if(n<0||n>end-pos)throw new IOException("MIDI 数据长度错误");pos+=(int)n;}
  int data()throws IOException{int b=u8();if(b>=128)throw new IOException("MIDI 音符/控制数据无效");return b;}
 }
 public static SmfFile read(InputStream in)throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
  while((n=in.read(block))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("MIDI 文件超过16MiB");out.write(block,0,n);}
  return read(out.toByteArray());
 }
 public static SmfFile read(byte[] bytes)throws IOException {
  if(bytes.length>MAX_BYTES)throw new IOException("MIDI 文件超过16MiB");
  Reader r=new Reader(bytes,0,bytes.length);
  if(r.u32()!=0x4d546864L)throw new IOException("请选择标准 .mid/.midi 文件（MThd）");
  long header=r.u32();if(header<6||header>r.end-r.pos)throw new IOException("MIDI 文件头无效");
  int format=r.u16(),tracks=r.u16(),division=r.u16();r.skip(header-6);
  if(format==2)throw new IOException("暂不支持 Format 2，请导出为 Format 0 或 1");
  if(format>1||tracks<1||tracks>MAX_TRACKS||(format==0&&tracks!=1))throw new IOException("MIDI 格式或音轨数量无效（最多128轨）");
  double fixedUs=0;
  if((division&0x8000)==0){if(division==0)throw new IOException("MIDI 每拍刻度不能为0");}
  else {int fps=-(byte)(division>>8),ticks=division&255;if(ticks==0||(fps!=24&&fps!=25&&fps!=29&&fps!=30))throw new IOException("SMPTE 时间基准无效");fixedUs=1000000.0/((fps==29?30000.0/1001:fps)*ticks);}
  ArrayList<Event> events=new ArrayList<>();String[] names=new String[tracks];long lastTick=0;int count=0,tempi=0;
  for(int t=0;t<tracks;t++) {
   while(true){long id=r.u32(),len=r.u32();if(len>r.end-r.pos)throw new IOException("MIDI 音轨被截断");if(id==0x4d54726bL){Reader tr=new Reader(bytes,r.pos,r.pos+(int)len);r.skip(len);long tick=0;int running=0;names[t]="音轨 "+(t+1);
     while(tr.pos<tr.end){tick+=tr.vlq();if(++count>MAX_EVENTS)throw new IOException("MIDI 事件超过20万个");int s=tr.u8();if(s<128){if(running==0)throw new IOException("MIDI 运行状态缺失");tr.pos--;s=running;}
      if(s>=0x80&&s<=0xef){running=s;int a=tr.data(),kind=s&0xf0,b=(kind==0xc0||kind==0xd0)?0:tr.data();if(kind==0x80||kind==0x90||kind==0xb0)events.add(new Event(tick,count,t,s,a,b));}
      else if(s==0xff){running=0;int type=tr.u8();long size=tr.vlq();if(size>tr.end-tr.pos)throw new IOException("MIDI 元事件被截断");if(type==0x51){if(size!=3)throw new IOException("MIDI 速度事件长度无效");int tempo=(tr.u8()<<16)|(tr.u8()<<8)|tr.u8();if(tempo==0)throw new IOException("MIDI 速度不能为0");events.add(new Event(tick,count,t,0xff,tempo,0));tempi++;}
       else {if(type==3&&size>0)names[t]=new String(bytes,tr.pos,(int)Math.min(256,size),StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}]","");tr.skip(size);}
       if(type==0x2f){if(size!=0)throw new IOException("MIDI 结束事件无效");break;}
      }else if(s==0xf0||s==0xf7){running=0;tr.skip(tr.vlq());}else throw new IOException("MIDI 含不支持的系统事件："+Integer.toHexString(s));
     }lastTick=Math.max(lastTick,tick);break;
    }r.skip(len);}
  }
  Collections.sort(events,(a,b)->a.tick==b.tick?Integer.compare(a.order,b.order):Long.compare(a.tick,b.tick));
  long tick=0;double us=0;int tempo=500000;
  for(Event e:events){us+=(e.tick-tick)*(fixedUs==0?(double)tempo/division:fixedUs);tick=e.tick;if(us>MAX_US)throw new IOException("MIDI 时长超过6小时");e.us=Math.round(us);if(e.status==0xff)tempo=e.a;}
  long duration=Math.round(us+(lastTick-tick)*(fixedUs==0?(double)tempo/division:fixedUs));if(duration>MAX_US)throw new IOException("MIDI 时长超过6小时");
  Map<Integer,ArrayDeque<Open>> active=new HashMap<>();ArrayList<Open> sustained=new ArrayList<>();boolean[] pedal=new boolean[16];ArrayList<Note> notes=new ArrayList<>();
  for(Event e:events){if(e.status==0xff)continue;int ch=e.status&15,kind=e.status&0xf0,key=e.track*2048+ch*128+e.a;
   if(kind==0x90&&e.b>0){active.computeIfAbsent(key,k->new ArrayDeque<>()).addLast(new Open(e));}
   else if(kind==0x80||(kind==0x90&&e.b==0)){ArrayDeque<Open> q=active.get(key);if(q!=null&&!q.isEmpty()){Open o=q.removeFirst();if(pedal[ch])sustained.add(o);else close(notes,o,e.us,e.tick);if(q.isEmpty())active.remove(key);}}
   else if(kind==0xb0){if(e.a==64){pedal[ch]=e.b>=64;if(!pedal[ch])releaseSustain(notes,sustained,ch,e.us,e.tick);}
    else if(e.a==120||e.a==123){for(Iterator<Map.Entry<Integer,ArrayDeque<Open>>> it=active.entrySet().iterator();it.hasNext();){ArrayDeque<Open> q=it.next().getValue();if(!q.isEmpty()&&(q.peek().on.status&15)==ch){for(Open o:q){if(e.a==123&&pedal[ch])sustained.add(o);else close(notes,o,e.us,e.tick);}it.remove();}}if(e.a==120)releaseSustain(notes,sustained,ch,e.us,e.tick);}
    else if(e.a==121){pedal[ch]=false;releaseSustain(notes,sustained,ch,e.us,e.tick);}
   }
  }
  int unclosed=0;for(ArrayDeque<Open> q:active.values())for(Open o:q){close(notes,o,duration,lastTick);unclosed++;}for(Open o:sustained)close(notes,o,duration,lastTick);
  Collections.sort(notes,(a,b)->a.startUs==b.startUs?Integer.compare(a.pitch,b.pitch):Long.compare(a.startUs,b.startUs));
  for(Note note:notes)duration=Math.max(duration,note.endUs);
  return new SmfFile(notes,names,duration,lastTick,format,division,tempi,unclosed);
 }
 private static void close(List<Note> notes,Open o,long end,long tick){Event e=o.on;notes.add(new Note(e.us,end,e.tick,tick,e.track,e.status&15,e.a,e.b));}
 private static void releaseSustain(List<Note> notes,List<Open> held,int channel,long end,long tick){for(Iterator<Open> it=held.iterator();it.hasNext();){Open o=it.next();if((o.on.status&15)==channel){close(notes,o,end,tick);it.remove();}}}
}
