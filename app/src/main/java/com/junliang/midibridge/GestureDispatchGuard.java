package com.junliang.midibridge;

/** Main-thread state machine: missing and stale callbacks cannot lock or corrupt a later gesture. */
public final class GestureDispatchGuard {
 public static final long GRACE_MS=600;
 private long serial,active,deadline;
 public long begin(long now,long duration){if(active!=0)throw new IllegalStateException("gesture already pending");active=++serial;deadline=now+Math.max(1,duration)+GRACE_MS;return active;}
 public boolean matches(long token){return token!=0&&active==token;}
 public boolean finish(long token){if(!matches(token))return false;active=0;return true;}
 public boolean expire(long now){if(active==0||now<deadline)return false;active=0;return true;}
 public void cancel(){active=0;serial++;}
 public boolean busy(){return active!=0;}
 public long deadline(){return deadline;}
}
