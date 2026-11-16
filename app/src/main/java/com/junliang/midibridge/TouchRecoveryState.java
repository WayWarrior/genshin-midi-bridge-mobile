package com.junliang.midibridge;

/** Recovery failure blocks game injection, while leaving the control menu usable. */
public final class TouchRecoveryState {
 public static final long TIMEOUT_MS=700;
 private int phase; // 0: ready, 1: recovering, 2: release uncertain
 private long serial,active,deadline;
 public long begin(long now){active=++serial;phase=1;deadline=now+TIMEOUT_MS;return active;}
 private boolean matches(long token){return phase==1&&token!=0&&token==active;}
 public boolean completed(long token){if(!matches(token))return false;phase=0;active=0;return true;}
 public boolean failed(long token){if(!matches(token))return false;phase=2;active=0;return true;}
 public boolean expire(long now){return phase==1&&now>=deadline&&failed(active);}
 public boolean inputReady(){return phase==0;}
 public boolean menuReady(){return phase!=1;}
 public long deadline(){return deadline;}
}
