package com.junliang.midibridge;

import android.content.Context;
import android.graphics.Rect;
import android.view.*;
import android.widget.ScrollView;

/** Passive status updates preserve the user's current drag/fling position. */
final class StableScrollView extends ScrollView {
 private boolean passiveLayout;
 private ViewTreeObserver.OnPreDrawListener clearPassive;
 StableScrollView(Context context){
  super(context);setFillViewport(true);setFocusableInTouchMode(true);
  setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
 }
 void preserveForStatusUpdate(){
  passiveLayout=true;
  if(clearPassive!=null)return;
  clearPassive=()->{clearPending();return true;};
  getViewTreeObserver().addOnPreDrawListener(clearPassive);
 }
 private void clearPending(){
  passiveLayout=false;
  if(clearPassive!=null){if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnPreDrawListener(clearPassive);clearPassive=null;}
 }
 @Override public boolean requestChildRectangleOnScreen(View child,Rect rectangle,boolean immediate){
  return !passiveLayout&&super.requestChildRectangleOnScreen(child,rectangle,immediate);
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  boolean preserve=passiveLayout;int currentX=getScrollX(),currentY=getScrollY();
  super.onLayout(changed,l,t,r,b);
  // Read just before layout, so an ongoing fling is not rewound to the last polling tick.
  if(preserve)scrollTo(currentX,currentY);
 }
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
  if(w!=oldw||h!=oldh)clearPending(); // Keyboard and rotation may reveal the editor normally.
  super.onSizeChanged(w,h,oldw,oldh);
 }
 @Override protected void onDetachedFromWindow(){clearPending();super.onDetachedFromWindow();}
}
