package com.junliang.midibridge;

public final class RecoveryChecks {
 private static int checks;
 private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 public static void run(){
  checks=0;TouchRecoveryState r=new TouchRecoveryState();
  check(r.inputReady()&&r.menuReady(),"initial controls and input ready");
  long first=r.begin(100);
  check(!r.inputReady()&&!r.menuReady(),"shield blocks input and menu during release");
  check(r.completed(first),"system completion succeeds without a View UP");
  check(r.inputReady()&&r.menuReady(),"missing View delivery cannot lock ball");
  check(!r.failed(first)&&!r.completed(first),"duplicate callbacks ignored");
  long second=r.begin(1000);
  check(!r.expire(1699),"deadline has not arrived");
  check(r.expire(1700),"missing callback expires after bounded wait");
  check(r.menuReady()&&!r.inputReady(),"failed release leaves menu usable and injection blocked");
  check(!r.completed(second),"late completion cannot clear uncertainty");
  long retry=r.begin(1800);
  check(!r.completed(second)&&!r.failed(first),"previous callback cannot finish retry");
  check(!r.menuReady()&&!r.inputReady(),"retry is shielded");
  check(r.completed(retry)&&r.menuReady()&&r.inputReady(),"manual retry restores input without restart");
  long rejected=r.begin(2000);check(r.failed(rejected),"submission rejection recorded");
  check(r.menuReady()&&!r.inputReady(),"submission rejection cannot lock menu");
  long current=r.begin(2100),replacement=r.begin(2200);
  check(!r.completed(current)&&!r.failed(current),"replaced recovery callbacks ignored");
  check(r.completed(replacement),"new recovery succeeds");
  check(!r.expire(9999)&&r.menuReady()&&r.inputReady(),"old timeout cannot undo completion");
  System.out.println("Recovery checks passed: "+checks);
 }
 public static void main(String[] args){run();}
}
