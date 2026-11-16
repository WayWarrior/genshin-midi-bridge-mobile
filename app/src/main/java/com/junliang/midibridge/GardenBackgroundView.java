package com.junliang.midibridge;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/** Native decoration behind the paper. No layout, touch handling or background timer. */
final class GardenBackgroundView extends View {
 private static final int FRAME_MS=33,FOLIAGE=16,PARTICLES=28;
 private static final class Particle {float x,y,speed,size,phase,angle,spin,glowOffset;int kind;}
 private final Bitmap artwork,clover,speck;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final RectF bounds=new RectF(),cloverBounds=new RectF(),speckBounds=new RectF();
 private final Path petal=new Path(),star=new Path(),vein=new Path();
 private final Shader petalColor=new LinearGradient(-1,-1,1,1,0xfff7a3c3,0xffffeff4,Shader.TileMode.CLAMP);
 private final Shader speckGlow=new RadialGradient(0,0,2,0x80ecf8b9,0x00ecf8b9,Shader.TileMode.CLAMP);
 private final Shader yellowGlow=new RadialGradient(0,0,2,0x80f1e575,0x00f1e575,Shader.TileMode.CLAMP);
 private final Particle[] particles=new Particle[PARTICLES];
 private final float density;
 private boolean enabled=true,foreground;
 private int paperWidth;
 private long elapsed,lastFrame;
 private final Runnable frame=()->{
  if(!shouldAnimate()){lastFrame=0;return;}
  long now=SystemClock.uptimeMillis();if(lastFrame!=0)elapsed+=Math.min(100,Math.max(0,now-lastFrame));lastFrame=now;
  invalidate();postDelayed(this.frame,FRAME_MS);
 };
 GardenBackgroundView(Context context){
  super(context);density=getResources().getDisplayMetrics().density;
  BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
  artwork=BitmapFactory.decodeResource(getResources(),R.drawable.garden_background,options);
  Bitmap[] decorations=decodeDecorations();clover=decorations[0];speck=decorations[1];
  setSpriteBounds(clover,cloverBounds);setSpriteBounds(speck,speckBounds);
  setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  petal.moveTo(0,-1);petal.cubicTo(.95f,-.75f,.7f,.55f,-.06f,1);petal.cubicTo(-.8f,.75f,-.65f,-.45f,0,-1);petal.close();
  vein.moveTo(-.06f,.65f);vein.quadTo(.05f,-.06f,0,-.75f);
  // Soft four-point outline, with the warm yellow of the first reference.
  star.moveTo(0,-1);star.cubicTo(.20f,-.34f,.19f,-.22f,.68f,-.11f);star.quadTo(1,0,.68f,.11f);
  star.cubicTo(.19f,.22f,.20f,.34f,0,1);star.cubicTo(-.20f,.34f,-.19f,.22f,-.68f,.11f);
  star.quadTo(-1,0,-.68f,-.11f);star.cubicTo(-.19f,-.22f,-.20f,-.34f,0,-1);star.close();
  Random random=new Random(20261004L);
  for(int i=0;i<PARTICLES;i++){
   Particle p=new Particle();p.kind=i>=FOLIAGE?(i%2==0?2:3):i%3==0?1:0;p.x=.12f+random.nextFloat()*.76f;p.y=random.nextFloat();
   p.speed=density*(p.kind>=2?5+random.nextFloat()*6:12+random.nextFloat()*10);
   p.size=density*(p.kind==2?2.8f+random.nextFloat()*1.4f:p.kind==3?3.5f+random.nextFloat()*2:5+random.nextFloat()*4);
   p.phase=random.nextFloat()*(float)(Math.PI*2);p.angle=random.nextFloat()*360;p.spin=(random.nextFloat()-.5f)*(p.kind>=2?4:12);particles[i]=p;
   p.glowOffset=random.nextFloat()*SparkCycle.PERIOD;
  }
 }
 private Bitmap[] decodeDecorations(){
  // Decode just the two transparent regions from the supplied full artwork, once.
  BitmapRegionDecoder decoder=null;
  try(InputStream stream=getResources().openRawResource(R.drawable.garden_elements)){
   decoder=BitmapRegionDecoder.newInstance(stream,false);
   BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
   Bitmap leaf=decoder.decodeRegion(new Rect(879,122,1019,272),options);
   Bitmap dot=decoder.decodeRegion(new Rect(1048,130,1077,160),options);
   return new Bitmap[]{leaf,dot};
  }catch(IOException failure){return new Bitmap[]{null,null};}
  finally{if(decoder!=null)decoder.recycle();}
 }
 private static void setSpriteBounds(Bitmap bitmap,RectF bounds){
  if(bitmap==null)return;float max=Math.max(bitmap.getWidth(),bitmap.getHeight());
  bounds.set(-bitmap.getWidth()/max,-bitmap.getHeight()/max,bitmap.getWidth()/max,bitmap.getHeight()/max);
 }
 void setPaperWidth(int width){paperWidth=width;invalidate();}
 void setAnimationEnabled(boolean value){enabled=value;syncFrames();}
 void setForeground(boolean value){foreground=value;syncFrames();}
 private boolean shouldAnimate(){return enabled&&foreground&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown();}
 private void syncFrames(){if(frame==null)return;removeCallbacks(frame);lastFrame=0;if(shouldAnimate()){lastFrame=SystemClock.uptimeMillis();postDelayed(frame,FRAME_MS);}invalidate();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncFrames();}
 @Override protected void onDetachedFromWindow(){removeCallbacks(frame);lastFrame=0;super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);syncFrames();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);syncFrames();}
 @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);bounds.set(0,0,w,h);}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);if(artwork==null)return;
  float seconds=elapsed/1000f,w=getWidth(),h=getHeight();paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
  canvas.save();if(enabled){canvas.scale(1.018f,1.018f,w/2,h/2);canvas.rotate((float)Math.sin(seconds*.42f)*.2f,w/2,h/2);canvas.translate((float)Math.sin(seconds*.6f)*density,0);}
  canvas.drawBitmap(artwork,null,bounds,paint);canvas.restore();if(!enabled||w<=0||h<=0)return;
  float band=Math.max(8*density,(w-Math.min(w,paperWidth))/2),margin=16*density;
  for(int i=0;i<particles.length;i++){
   Particle p=particles[i];float y=((p.y+seconds*p.speed/(h+2*margin))%1)*(h+2*margin)-margin;
   float sway=(float)Math.sin(seconds*.65f+p.phase)*Math.min(5*density,band*.18f),x=band*p.x+sway;if(i%2!=0)x=w-x;
   canvas.save();canvas.translate(x,y);
   if(p.kind>=2)drawSpark(canvas,p,SparkCycle.phase(seconds+p.glowOffset));else{
    canvas.rotate(p.angle+seconds*p.spin+16*(float)Math.sin(seconds*.55f+p.phase));
    canvas.scale(p.size,p.size*(.85f+.12f*(float)Math.sin(seconds*.75f+p.phase)));
    if(p.kind==0)drawPetal(canvas);else drawClover(canvas);
   }
   canvas.restore();
  }
  paint.setShader(null);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
 }
 private void drawPetal(Canvas canvas){
  paint.setStyle(Paint.Style.FILL);paint.setShader(petalColor);paint.setAlpha(150);canvas.drawPath(petal,paint);
  paint.setShader(null);paint.setColor(0xfffffff5);paint.setAlpha(160);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.05f);canvas.drawPath(vein,paint);paint.setStyle(Paint.Style.FILL);
 }
 private void drawClover(Canvas canvas){
  if(clover==null)return;
  paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);canvas.drawBitmap(clover,null,cloverBounds,paint);
 }
 private void drawSpark(Canvas canvas,Particle p,float phase){
  float pulse=SparkCycle.brightness(phase);if(pulse<=0)return;
  canvas.rotate(p.angle+(elapsed/1000f)*p.spin);canvas.scale(p.size,p.size);
  paint.setStyle(Paint.Style.FILL);paint.setShader(p.kind==2?speckGlow:yellowGlow);
  paint.setAlpha(Math.round(255*pulse));canvas.drawCircle(0,0,2,paint);paint.setShader(null);
  if(p.kind==2){
   if(speck!=null){paint.setAlpha(Math.round(255*pulse));canvas.drawBitmap(speck,null,speckBounds,paint);}return;
  }
  paint.setColor(0xfffffdec);paint.setAlpha(Math.round(120*pulse));canvas.drawPath(star,paint);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(0xffeee16a);
  paint.setStrokeWidth(.40f);paint.setAlpha(Math.round(55*pulse));canvas.drawPath(star,paint);
  paint.setStrokeWidth(.13f);paint.setAlpha(Math.round(245*pulse));canvas.drawPath(star,paint);paint.setStyle(Paint.Style.FILL);
 }
}
