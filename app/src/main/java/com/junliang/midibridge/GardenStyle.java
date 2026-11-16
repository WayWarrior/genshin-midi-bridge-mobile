package com.junliang.midibridge;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Lightweight native styling; no extra UI runtime or animation work on the audio path. */
final class GardenStyle {
 static final int BACKGROUND=0xfff5f9f2, INK=0xff314c3b, MUTED=0xff647569;
 static final int GREEN=0xff497454, MINT=0xffedf5e8, PINK=0xfffbe8ed;
 static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable shape(Context c,int color,int stroke,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
  if(stroke!=0)d.setStroke(dp(c,1),stroke);return d;
 }
 static void button(Button b,int kind){
  Context c=b.getContext();int fill=kind==1?GREEN:kind==2?PINK:MINT;
  b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2879a470),shape(c,fill,kind==1?0:0xffdce8d7,14),null));
  b.setBackgroundTintList(null);
  b.setTextColor(kind==1?0xffffffff:kind==2?0xff895268:INK);b.setAllCaps(false);b.setTextSize(14);
  b.setTypeface(Typeface.DEFAULT,kind==1?Typeface.BOLD:Typeface.NORMAL);
  b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));
  b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
 }
 static void fields(View view){
  Context c=view.getContext();
  if(view instanceof EditText){EditText e=(EditText)view;e.setTextColor(INK);e.setTextSize(15);e.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));e.setMinHeight(dp(c,48));e.setBackground(shape(c,0xfff8faf6,0xffd8e5d2,12));}
  else if(view instanceof CheckBox){CheckBox b=(CheckBox)view;b.setTextColor(INK);b.setTextSize(14);b.setButtonTintList(ColorStateList.valueOf(GREEN));b.setPadding(0,dp(c,7),0,dp(c,7));}
  else if(view instanceof Spinner){view.setBackgroundTintList(ColorStateList.valueOf(GREEN));view.setMinimumHeight(dp(c,48));}
  else if(view instanceof SeekBar){SeekBar b=(SeekBar)view;b.setProgressTintList(ColorStateList.valueOf(GREEN));b.setThumbTintList(ColorStateList.valueOf(GREEN));b.setMinimumHeight(dp(c,44));}
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)fields(group.getChildAt(i));}
 }
}
