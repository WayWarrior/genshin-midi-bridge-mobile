package com.junliang.midibridge;
import android.app.*;
import android.content.*;
import android.media.midi.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
public final class MainActivity extends Activity {
 private static final int HOME=0,CONNECTION=1,MIDI_FILE=2,SOUND=3,SETTINGS=4,SAMPLES=5;
 private static final String[] PAGE_TITLES={"原琴小花园","01 · 连接小琴","02 · MIDI 文件","03 · 调音与声音","04 · 设置","05 · 音色与试弹"};
 private LinearLayout content,page;private ViewFlipper pages;private TextView pageTitle;
 private Button backButton;private int currentPage,pageWidth,portDevice=-1;
 private GardenBackgroundView gardenBackground;
 private final StableScrollView[] scrolls=new StableScrollView[6];
 private final TextView[] statusViews=new TextView[6];
 private Spinner devices,ports,sampleNotes,audioProfile;private EditText transpose,customPackage;private CheckBox fold,oneShot,liveHold,liveReuse;private TextView reuseStatus;private long lastReuseCheck;private SeekBar volume;
 private final List<MidiDeviceInfo> infos=new ArrayList<>();
 private final HashSet<Integer> testHeld=new HashSet<>();
 private final Handler main=new Handler(Looper.getMainLooper());
 private int importNote;
 private Spinner accidental,touchMode,fileSourceKey;private EditText fileSpeed,fileBpm;private CheckBox fileFold,fileNatural,fileTempoOverride,fileConvertC;
 private boolean midiImporting;
 private final Runnable poll=()->{updateStatus();main.postDelayed(this.poll,700);};
 private void updateStatus(){
  if(currentPage==SOUND&&reuseStatus!=null){
   TouchService service=TouchService.instance;String check=service!=null&&service.reuseChecking()?"正在检测，请暂时不要触屏…":Config.reuseStatus(this);
   if(!android.text.TextUtils.equals(reuseStatus.getText(),check)){scrolls[SOUND].preserveForStatusUpdate();reuseStatus.setText(check);}
   long stamp=Config.prefs(this).getLong("live_reuse_checked",0);
   if(stamp!=lastReuseCheck){lastReuseCheck=stamp;liveReuse.setChecked(Config.prefs(this).getBoolean("live_reuse",true));liveHold.setChecked(Config.prefs(this).getBoolean("live_hold",true));}
  }
  TextView target=statusViews[currentPage];if(target==null)return;
  BridgeService b=BridgeService.instance;TouchService t=TouchService.instance;
  String value;
  if(currentPage==MIDI_FILE)value=b==null||b.filePlayer==null?"还没有启动音源；导入文件或点本地试听即可启动。":b.filePlayer.progress();
  else{
   value="音源："+(b==null?"未运行":b.audio.diagnostics())+"\n采样："+(b==null?"未加载":b.sampleStatus)+"\nMIDI："+(b==null?"未运行":b.midiStatus)+"\n映射："+(t==null?"无障碍服务未启用":t.status);
   if(b!=null)value+="\n收到起音："+b.noteCount;
   if(b!=null&&b.filePlayer!=null)value+="\n文件："+b.filePlayer.progress();
   if(t!=null)value+=" / 已完成起音："+t.sent+" / 忽略或取消："+t.dropped;
  }
  if(android.text.TextUtils.equals(target.getText(),value))return;
  scrolls[currentPage].preserveForStatusUpdate();target.setText(value);
 }
 private void statusBox(int index){
  TextView t=text("正在查看状态…",13,GardenStyle.INK);t.setTextIsSelectable(false);t.setFocusable(false);t.setFocusableInTouchMode(false);t.setRevealOnFocusHint(false);
  t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setBackground(GardenStyle.shape(this,0xfff1f6ed,0,14));content.addView(t);statusViews[index]=t;
 }
 private int dp(int value){return GardenStyle.dp(this,value);}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
 private void label(String s){
  TextView t=text(s,14,GardenStyle.MUTED);t.setPadding(0,dp(10),0,dp(8));
  if(s.length()>110){
   TextView toggle=text("使用说明  ·  点按展开 ▾",13,GardenStyle.GREEN);toggle.setPadding(0,dp(10),0,dp(10));toggle.setMinHeight(dp(44));toggle.setBackground(GardenStyle.shape(this,0xfff4f8ef,0,10));toggle.setPadding(dp(12),dp(10),dp(12),dp(10));
   t.setVisibility(View.GONE);toggle.setOnClickListener(v->{boolean open=t.getVisibility()!=View.VISIBLE;t.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"使用说明  ·  收起 ▴":"使用说明  ·  点按展开 ▾");});content.addView(toggle);
  }
  content.addView(t);
 }
 private void section(String title,String subtitle){
  content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(18),dp(18),dp(18));content.setBackground(GardenStyle.shape(this,0xffffffff,0xffe0e9da,22));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,0);page.addView(content,lp);
  TextView heading=text(title,19,GardenStyle.INK);heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);content.addView(heading);
  TextView detail=text(subtitle,13,GardenStyle.MUTED);detail.setPadding(0,dp(5),0,dp(10));content.addView(detail);
 }
 private void header(){
  LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(6),dp(8),dp(6),dp(14));page.addView(hero);
  ImageView icon=new ImageView(this);android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=8;icon.setImageBitmap(android.graphics.BitmapFactory.decodeResource(getResources(),R.drawable.launcher_portrait,options));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);icon.setBackground(GardenStyle.shape(this,0xfff4f9e8,0,18));icon.setClipToOutline(true);hero.addView(icon,new LinearLayout.LayoutParams(dp(68),dp(68)));
  LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(16),0,0,0);hero.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  TextView title=text("今天，也弹开心一点",21,GardenStyle.INK);title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);words.addView(title);words.addView(text("选一扇小门，开始演奏。",14,GardenStyle.MUTED));
 }
 private void createPage(int index){
  StableScrollView sc=new StableScrollView(this);sc.setId(new int[]{R.id.page_home,R.id.page_connection,R.id.page_midi,R.id.page_sound,R.id.page_settings,R.id.page_samples}[index]);sc.setClipToPadding(false);scrolls[index]=sc;
  page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),0,dp(18),dp(22));sc.addView(page);pages.addView(sc,new ViewGroup.LayoutParams(-1,-1));content=page;
 }
 private void buildHome(){
  createPage(HOME);header();
  String[] titles={"01  ·  连接小琴","02  ·  MIDI 文件","03  ·  调音与声音","04  ·  设置","05 + 06  ·  音色与试弹"};
  String[] details={"选择键盘，启动后台音源","导入乐谱、试听与自动演奏","移调、音量、音频缓冲","无障碍、校准、悬浮球与游戏声音","管理采样，试弹小琴键"};
  int columns=pageWidth>=dp(520)?2:1;
  for(int i=0;i<titles.length;i+=columns){LinearLayout row=new LinearLayout(this);
   for(int j=i;j<Math.min(titles.length,i+columns);j++){
    final int destination=j+1;Button b=new Button(this);GardenStyle.button(b,0);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setMinHeight(dp(90));b.setMinimumHeight(dp(90));
    String label=titles[j]+"\n"+details[j];android.text.SpannableString styled=new android.text.SpannableString(label);styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,titles[j].length(),0);styled.setSpan(new android.text.style.RelativeSizeSpan(1.18f),0,titles[j].length(),0);b.setText(styled);b.setOnClickListener(v->showPage(destination));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,j==i&&i+1<titles.length&&columns==2?dp(10):0,dp(10));row.addView(b,lp);
   }page.addView(row);
  }
  section("✿  演奏状态","USB MIDI / MIDI 文件 → 本地音源 + 原琴");statusBox(HOME);
 }
 private void mountPages(){
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(GardenStyle.BACKGROUND);
  gardenBackground=new GardenBackgroundView(this);gardenBackground.setAnimationEnabled(Config.prefs(this).getBoolean("garden_motion",true));root.addView(gardenBackground,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout paper=new LinearLayout(this);paper.setOrientation(LinearLayout.VERTICAL);paper.setBackground(GardenStyle.shape(this,0xffffffff,0xffe0e9da,24));paper.setClipToOutline(true);
  pageWidth=Math.min(dp(720),getResources().getDisplayMetrics().widthPixels-dp(32));FrameLayout.LayoutParams paperLp=new FrameLayout.LayoutParams(pageWidth,-1,Gravity.CENTER_HORIZONTAL);paperLp.setMargins(0,dp(12),0,dp(16));root.addView(paper,paperLp);
  gardenBackground.setPaperWidth(pageWidth);
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(14),dp(8),dp(14),dp(8));paper.addView(bar);
  backButton=new Button(this);GardenStyle.button(backButton,0);backButton.setText("‹ 首页");backButton.setTextSize(13);backButton.setOnClickListener(v->showPage(HOME));LinearLayout.LayoutParams backLp=new LinearLayout.LayoutParams(dp(84),dp(44));backLp.setMargins(0,0,dp(12),0);bar.addView(backButton,backLp);
  pageTitle=text(PAGE_TITLES[HOME],18,GardenStyle.INK);pageTitle.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);bar.addView(pageTitle,new LinearLayout.LayoutParams(0,-2,1));
  TextView version=text("v0.18",12,GardenStyle.GREEN);version.setPadding(dp(8),dp(6),dp(8),dp(6));bar.addView(version);
  View line=new View(this);line.setBackgroundColor(0xffe8eee2);paper.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  pages=new ViewFlipper(this);pages.setAnimateFirstView(false);paper.addView(pages,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
 }
 private void showPage(int index){
  if(index<HOME||index>SAMPLES)index=HOME;
  if(currentPage==SAMPLES&&index!=SAMPLES)releaseTestKeys();
  View focused=getCurrentFocus();if(focused!=null){android.view.inputmethod.InputMethodManager im=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(im!=null)im.hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}
  currentPage=index;pages.setDisplayedChild(index);pageTitle.setText(PAGE_TITLES[index]);backButton.setVisibility(index==HOME?View.GONE:View.VISIBLE);scrolls[index].requestFocus();updateStatus();
 }
 private void assignFieldIds(){
  devices.setId(R.id.devices);ports.setId(R.id.ports);sampleNotes.setId(R.id.sample_notes);audioProfile.setId(R.id.audio_profile);transpose.setId(R.id.transpose);customPackage.setId(R.id.custom_package);fold.setId(R.id.fold);oneShot.setId(R.id.one_shot);liveHold.setId(R.id.live_hold);liveReuse.setId(R.id.live_reuse);volume.setId(R.id.volume);accidental.setId(R.id.accidental);touchMode.setId(R.id.touch_mode);fileSpeed.setId(R.id.file_speed);fileFold.setId(R.id.file_fold);fileNatural.setId(R.id.file_natural);
  fileTempoOverride.setId(R.id.file_tempo_override);fileBpm.setId(R.id.file_bpm);fileConvertC.setId(R.id.file_convert_c);fileSourceKey.setId(R.id.file_source_key);
 }
 private void releaseTestKeys(){BridgeService b=BridgeService.instance;if(b!=null)for(int note:testHeld)b.testNote(note,false);testHeld.clear();}
 private Button button(String s,Runnable r){
  Button b=new Button(this);b.setText(s);int kind=s.startsWith("启动")||s.equals("导入 MIDI 文件")||s.startsWith("本地试听")||s.startsWith("保存设置")?1:s.startsWith("停止音源")?2:0;GardenStyle.button(b,kind);b.setOnClickListener(v->{v.clearFocus();scrolls[currentPage].requestFocus();r.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(2));content.addView(b,lp);return b;
 }
 private void notifyUser(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);Config.migrateFileTouch(this);mountPages();
  buildHome();buildConnection();buildMidiFile();buildSound();buildSettings();buildSamples();
  assignFieldIds();for(StableScrollView scroll:scrolls)GardenStyle.fields(scroll);refreshDevices();
  importNote=state==null?NoteMap.NOTES[0]:state.getInt("import_note",NoteMap.NOTES[0]);
  showPage(state==null?HOME:state.getInt("page",HOME));
 }
 private void buildConnection(){createPage(CONNECTION);
  section("01  ·  连接小琴","USB MIDI 设备与输出端口");devices=new Spinner(this);ports=new Spinner(this);content.addView(devices);content.addView(ports);
  devices.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){fillPorts(pos);}});
  button("刷新 USB MIDI 设备",this::refreshDevices);
  button("启动 / 重新连接后台音源",()->{
   if(!saveSettings())return;
   Intent i=new Intent(this,BridgeService.class);int pos=devices.getSelectedItemPosition();
   if(pos>=0 && pos<infos.size())i.putExtra("device",infos.get(pos).getId());i.putExtra("port",Math.max(0,ports.getSelectedItemPosition()));
   try{startForegroundService(i);}catch(Exception e){notifyUser("启动失败："+e.getMessage());}
  });
  button("停止音源和映射",()->{stopService(new Intent(this,BridgeService.class));TouchService t=TouchService.instance;if(t!=null)t.setArmed(false);});
  section("后台音源状态","这里会显示连接结果和实际音频缓冲");statusBox(CONNECTION);
 }
 private void buildMidiFile(){createPage(MIDI_FILE);
  section("02  ·  乐谱小匣子","导入 MIDI，试听或让原琴自动演奏");
  label("默认按C调处理，也可选择原调自动转为C调；支持 .mid/.midi 的 Format 0/1、多音轨、速度变化、和弦、空拍和延音踏板，不强制对齐格子。文件≤16MiB、≤128轨、≤20万事件、≤6小时。");
  button("导入 MIDI 文件",()->{if(midiImporting){notifyUser("正在导入，请稍候");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,43);});
  fileConvertC=new CheckBox(this);fileConvertC.setText("按 MIDI 原调自动转为 C 调");fileConvertC.setChecked(Config.prefs(this).getBoolean("midi_convert_c",false));content.addView(fileConvertC);
  label("MIDI 原调（整首使用同一个调）");fileSourceKey=new Spinner(this);fileSourceKey.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,MidiFileOptions.keyChoices()));fileSourceKey.setSelection(Math.max(0,Math.min(11,Config.prefs(this).getInt("midi_source_key",0))));content.addView(fileSourceKey);
  label("关闭时视为已转C调。小调按关系大调处理：A小调选C大调 / A小调，B小调选D大调 / B小调。先转调，再处理调外音和八度折回。");
  label("此处只处理 MIDI 文件；“调音与声音”页的共同移调仍额外生效，通常保持0，避免重复转调。不会根据文件中的调号自动改变原调。");
  accidental=new Spinner(this);accidental.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"调外音：忽略（默认）","调外音：向上偏移半音","调外音：向下偏移半音"}));accidental.setSelection(Math.max(0,Math.min(2,Config.prefs(this).getInt("midi_accidental",0))));content.addView(accidental);
  fileFold=new CheckBox(this);fileFold.setText("文件超出C3–B5时按八度折回（默认开启）");fileFold.setChecked(Config.prefs(this).getBoolean("midi_fold",true));content.addView(fileFold);
  label("文件触控方式");touchMode=new Spinner(this);touchMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"琴类短点按（每次最多24ms，完整松键）"}));touchMode.setSelection(0);touchMode.setEnabled(false);content.addView(touchMode);
  label("文件按时值长按因原神崩溃反馈已停用，旧设置自动切回短点按。本地采样仍按MIDI时值播放；实时键盘按住/松开映射保留。");
  label("游戏点击按未发送的音符顺序推进，轻微迟到不跳过短音；迟到超过120ms会暂停并提示降低速度或减少音轨。调外音被忽略时请检查下方半音选项及播放状态的跳过计数。");
  fileNatural=new CheckBox(this);fileNatural.setText("文件试听：采样自然衰减（关闭时按文件时值收音）");fileNatural.setChecked(Config.prefs(this).getBoolean("midi_natural",false));content.addView(fileNatural);
  label("本地试听或继续试听时自动取消本地静音，保留所设音量；已经有声时保持有声。试听中仍可手动静音，游戏文件演奏不自动开本地声音。系统媒体音量或本地音量为0时仍需自己调高。");
  label("本地采样按文件时值进入短释音以避免爆音；采样播放完后不会循环或拉长。暂停后的长音会重新起音，并按剩余时值释放。");
  label("文件播放速度百分比：25–200，默认100（保留原速度变化）");fileSpeed=new EditText(this);fileSpeed.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);fileSpeed.setText(String.valueOf(Config.prefs(this).getInt("midi_speed",100)));content.addView(fileSpeed);
  fileTempoOverride=new CheckBox(this);fileTempoOverride.setText("覆盖原始速度（Override）");fileTempoOverride.setChecked(Config.prefs(this).getBoolean("midi_tempo_override",false));content.addView(fileTempoOverride);
  label("目标速度：20–400 BPM，可用小数；默认120");fileBpm=new EditText(this);fileBpm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);fileBpm.setText(String.valueOf(Config.prefs(this).getFloat("midi_bpm",MidiFileOptions.DEFAULT_BPM)));content.addView(fileBpm);
  label("覆盖开启后，整首以目标BPM播放，忽略原文件的速度变化，速度百分比暂不生效。关闭后恢复原速度与百分比。音符时值、空拍和踏板同步变速，不对齐格子。");
  fileTempoOverride.setOnCheckedChangeListener((v,checked)->updateFileOptionControls());fileConvertC.setOnCheckedChangeListener((v,checked)->updateFileOptionControls());updateFileOptionControls();
  button("选择要演奏的音轨（默认全部旋律轨，忽略鼓通道）",this::chooseFileTracks);
  button("保存文件演奏设置",()->{if(saveFileSettings())notifyUser("已保存；从头播放时使用新设置");});
  button("本地试听 MIDI（只播放采样）",()->{if(!saveSettings()||!saveFileSettings())return;try{startForegroundService(new Intent(this,BridgeService.class).setAction("preview_midi"));}catch(Exception e){notifyUser("启动失败："+e.getMessage());}});
  button("暂停文件演奏",()->{BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.pause();});
  button("继续本地试听",()->{BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.start(false);else notifyUser("先导入并试听 MIDI");});
  button("停止文件演奏 / 回到开头",()->{BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.stop();});
  label("游戏自动弹：导入文件并保存设置 → 回到原神21键琴界面 → 完成校准 → 悬浮球菜单点“MIDI文件”→ 开始演奏。3秒倒计时后双路播放。再次点“MIDI文件”会暂停并释放按键，可继续或从头停止。切离原神、分辨率变化或手势被打断时会暂停。文件播放/暂停期间暂停USB键盘输入，停止文件后恢复。");
  section("文件状态","导入、播放进度与暂停状态");statusBox(MIDI_FILE);
 }
 private void buildSound(){createPage(SOUND);
  section("03  ·  调音与声音","本地音源与原琴共用音高设置");
  label("移调半音数：-24 至 +24；默认 C3–B5 的21个自然音，黑键忽略");transpose=new EditText(this);transpose.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);transpose.setText(String.valueOf(Config.prefs(this).getInt("transpose",0)));content.addView(transpose);
  fold=new CheckBox(this);fold.setText("将超出范围的音按八度折回（仍不映射半音）");fold.setChecked(Config.prefs(this).getBoolean("fold",false));content.addView(fold);
  liveHold=new CheckBox(this);liveHold.setText("实时键盘：按下保持触控，松开释放（默认开启）");liveHold.setChecked(Config.prefs(this).getBoolean("live_hold",true));content.addView(liveHold);
  label("支持多个键分别保持，最多10个不同琴键。超出时仅忽略多出的键，映射继续；松开后重新按即可。本地采样仍照常播放。折回到同一琴键的音，全部松开才释放。延音踏板只影响本地音源；游戏触控跟随手指松键。取消勾选可恢复24ms短点按，修改后点保存设置。长按能否持续发声由游戏乐器决定，采样不会被自动拉长。");
  liveReuse=new CheckBox(this);liveReuse.setText("长按时复用触点（避免保持音重新起音）");liveReuse.setChecked(Config.prefs(this).getBoolean("live_reuse",true));content.addView(liveReuse);
  reuseStatus=text(Config.reuseStatus(this),13,GardenStyle.GREEN);reuseStatus.setPadding(0,dp(8),0,dp(6));content.addView(reuseStatus);lastReuseCheck=Config.prefs(this).getLong("live_reuse_checked",0);
  button("检测并启用触点复用（约3秒）",()->{
   if(!saveSettings())return;TouchService service=TouchService.instance;if(service==null){notifyUser("先到04 · 设置，开启系统无障碍服务");return;}
   android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(scrolls[SOUND].getWindowToken(),0);service.testTouchReuse();
  });
  label("先开启无障碍，再点上面的检测按钮；检测时暂时不要触屏。通过后会自动启用实时长按和触点复用，回到原神重新开启映射即可。检测不需要连接键盘，也不需要重新校准。系统更新后需重新检测。");
  label("触点复用是实验功能，实际琴声仍需在原神验证。取消“复用触点”可使用旧兼容长按：编号用尽时会松开再重接，保持音可能重新响一次。取消“按下保持触控”则使用短点按。更改选项后点保存设置，再重新开启映射。");
  oneShot=new CheckBox(this);oneShot.setText("采样自然衰减（推荐：松键不截断琴声）");oneShot.setChecked(Config.prefs(this).getBoolean("one_shot",true));content.addView(oneShot);
  label("本地音源音量");volume=new SeekBar(this);volume.setMax(100);volume.setProgress((int)(Config.prefs(this).getFloat("volume",0.65f)*100));content.addView(volume);
  label("在原神里点悬浮球菜单“本地音源静音 / 恢复本地声音”即可切换，无需返回应用；保留本地音量，并记住静音状态。静音时音源仍处理按下、松开和采样时序，恢复后不会重放静音期间的音符。悬浮菜单仍会暂停游戏触控；操作后点“开启映射”或继续 MIDI 文件。");
  label("音频缓冲：先用低延迟，若断音再选平衡或稳健");
  audioProfile=new Spinner(this);audioProfile.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"低延迟（默认，自动适应）","平衡（更大缓冲）","稳健（优先减少断音）"}));
  audioProfile.setSelection(Math.max(0,Math.min(2,Config.prefs(this).getInt("audio_profile",0))));content.addView(audioProfile);
  label("缓冲不足时会自动增大；上方显示系统实际缓冲，数值不是按键到发声的实测延迟。更换扬声器/有线耳机/USB声卡后，可停止并重启音源重新适配。");
  button("重试所选档位的小缓冲",()->{if(!saveSettings())return;BridgeService bridge=BridgeService.instance;if(bridge!=null){bridge.audio.resetBuffer();notifyUser("已请求重试；若断音会重新增大缓冲");}else notifyUser("先启动音源");});
  button("保存设置（会清空正在响的音符）",()->{if(saveSettings())notifyUser("已保存");});
 }
 private void buildSettings(){createPage(SETTINGS);
  section("04  ·  设置","校准琴键、悬浮球与游戏声音");button("打开系统无障碍设置",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
  label("若提示未识别为原神：先在游戏里点悬浮球菜单“诊断/绑定”，检查检测包名，再确认绑定。切离游戏后映射会自动关闭，回来要重新开启。\n启用“原琴 MIDI 双路桥”后出现可拖动的小悬浮球。点球展开菜单，拖动调整位置；位置会记住。拖动或打开菜单会暂停游戏触控，继续演奏需重新开启映射或继续文件。打开原神风物之诗琴界面，在悬浮球菜单点“3点校准”。推荐点 C3→B3→C5，或选快速 C3→D3→C4。预览21键的位置后保存，再点开启映射。屏幕分辨率或界面布局改变后需重新校准。");
  button("重置游戏触控（暂停映射后重新松键）",()->{TouchService t=TouchService.instance;if(t!=null)t.resetTouch();else notifyUser("先开启无障碍服务");});
  label("花园背景");CheckBox motion=new CheckBox(this);motion.setText("动态背景（花瓣、四叶草与闪光）");motion.setChecked(Config.prefs(this).getBoolean("garden_motion",true));content.addView(motion);
  motion.setOnCheckedChangeListener((v,checked)->{Config.prefs(this).edit().putBoolean("garden_motion",checked).apply();gardenBackground.setAnimationEnabled(checked);});
  label("背景轻微摇摆，图片四叶草和花瓣缓慢飘落；浅黄绿圆光点与暖黄色星光分别渐亮、渐暗、熄灭，起点随机错开，中间由白色页面遮住。动画只在应用前台播放，切回游戏就暂停。关闭此选项可恢复静态背景，立即生效。");
  label("游戏静音、游戏开声与本地采样");
  label("游戏静音和游戏开声都不调整系统媒体音量。首次在原神琴界面点相应快捷按钮，录制退出琴界面→设置→声音→调整总音量→返回琴界面的操作。静音录为0，开声录为你想要的大小；两套步骤独立保存，旧静音记录保留。在悬浮球菜单点“声音设置”，选择静音或开声，可重录或只校正第1步位置，保留后续步骤。录制/执行时暂停琴键映射，实时键盘本地音源继续，MIDI文件演奏会暂停；结束后重新开启映射或继续文件。");
  button("清除已录游戏静音操作",()->{Config.prefs(this).edit().remove("game_mute_steps").apply();notifyUser("已清除；返回原神琴界面，点游戏静音重新录制");});
  button("清除已录游戏开声操作",()->{Config.prefs(this).edit().remove("game_sound_on_steps").apply();notifyUser("已清除；返回原神琴界面，点游戏开声重新录制");});
  label("覆盖更新与无障碍");
  label("0.18与0.2–0.17使用同一签名和包名，直接覆盖安装，不要先卸载。通常会保留采样、校准、静音录制和无障碍开关；若鸿蒙重置了开关仍需手动开启。应用不能替你自动授予无障碍权限。");
  label("原神包名（默认支持官服国服/国际服；渠道服或云版在此填实际包名）");customPackage=new EditText(this);customPackage.setSingleLine();customPackage.setText(Config.prefs(this).getString("package",""));content.addView(customPackage);
  button("保存设置",()->{if(saveSettings())notifyUser("已保存");});
 }
 private void buildSamples(){createPage(SAMPLES);
  section("音色收藏夹","已内置 HarmonicKey 的 21 个琴键采样");
  label("每个 WAV 对应一个琴键，直接播放，不做跨音高复用。支持 PCM16 / float32、单/双声道；单个≤16MiB且≤30秒，加载后的采样总量≤64MiB。导入不删除原文件。");sampleNotes=new Spinner(this);ArrayList<String> names=new ArrayList<>();for(int n:NoteMap.NOTES)names.add(NoteMap.label(n));sampleNotes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));content.addView(sampleNotes);
  button("为选中的音导入一个 WAV",()->{importNote=NoteMap.NOTES[sampleNotes.getSelectedItemPosition()];Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,42);});
  button("删除选中音的自定义采样（恢复内置 HarmonicKey）",()->{int note=NoteMap.NOTES[sampleNotes.getSelectedItemPosition()];File f=new File(getFilesDir(),"note_"+note+".wav");if(f.exists()&&!f.delete()){notifyUser("删除失败");return;}BridgeService b=BridgeService.instance;if(b!=null)b.loadSamples();notifyUser("已恢复内置 HarmonicKey 采样");});
  section("试弹一下","先启动音源，再按住下方小琴键");
  LinearLayout keys=new LinearLayout(this);
  for(int ix=7;ix<14;ix++) {final int n=NoteMap.NOTES[ix];Button b=new Button(this);b.setText(NoteMap.label(n).split(" ")[0]);GardenStyle.button(b,0);b.setTextSize(12);b.setPadding(0,dp(12),0,dp(12));b.setMinHeight(dp(72));LinearLayout.LayoutParams keyLp=new LinearLayout.LayoutParams(0,dp(72),1);keyLp.setMargins(dp(2),0,dp(2),0);keys.addView(b,keyLp);b.setOnTouchListener((v,e)->{BridgeService bridge=BridgeService.instance;int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){if(bridge==null)return false;testHeld.add(n);bridge.testNote(n,true);}if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(testHeld.remove(n)&&bridge!=null)bridge.testNote(n,false);}return true;});}
  content.addView(keys);
  label("使用提示：听本地采样时可用“游戏静音”；想听原神琴声时用“游戏开声”。保留系统媒体音量，先用扬声器或有线耳机。华为应用启动管理中允许本应用后台活动。若切回原神后音源被静音，可能是音频焦点或后台限制；本版本尚未在华为真机验证。");
 }
 private boolean saveSettings(){
  try {int n=Integer.parseInt(transpose.getText().toString().trim());if(n<-24 || n>24)throw new IllegalArgumentException();Config.prefs(this).edit().putInt("transpose",n).putBoolean("fold",fold.isChecked()).putBoolean("live_hold",liveHold.isChecked()).putBoolean("live_reuse",liveReuse.isChecked()).putBoolean("one_shot",oneShot.isChecked()).putString("package",customPackage.getText().toString().trim()).putFloat("volume",volume.getProgress()/100f).putInt("audio_profile",Math.max(0,audioProfile.getSelectedItemPosition())).apply();BridgeService b=BridgeService.instance;if(b!=null)b.refreshConfig();return true;}
  catch(Exception e){notifyUser("移调请填 -24 到 +24 的整数");return false;}
 }
 private void refreshDevices() {
  int selected=devices.getSelectedItemPosition(),oldPort=Math.max(0,ports.getSelectedItemPosition());
  int oldDevice=selected>=0&&selected<infos.size()?infos.get(selected).getId():portDevice;
  infos.clear();ArrayList<String> names=new ArrayList<>();MidiManager mm=(MidiManager)getSystemService(MIDI_SERVICE);
  if(mm!=null)for(MidiDeviceInfo info:mm.getDevices())if(info.getType()==MidiDeviceInfo.TYPE_USB&&info.getOutputPortCount()>0){infos.add(info);names.add(info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"USB MIDI")+" #"+info.getId());}
  if(names.isEmpty())names.add("未发现设备（可先测试内置音源）");
  int next=0;for(int i=0;i<infos.size();i++)if(infos.get(i).getId()==oldDevice)next=i;
  devices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));devices.setSelection(next);fillPorts(next);
  if(portDevice==oldDevice)ports.setSelection(Math.min(oldPort,ports.getCount()-1));
 }
 private void fillPorts(int pos){
  int id=pos>=0&&pos<infos.size()?infos.get(pos).getId():-1;
  int previous=id==portDevice?Math.max(0,ports.getSelectedItemPosition()):0;
  ArrayList<String> labels=new ArrayList<>();if(pos>=0&&pos<infos.size())for(int i=0;i<infos.get(pos).getOutputPortCount();i++)labels.add("输出端口 "+i);
  if(labels.isEmpty())labels.add("输出端口 0");portDevice=id;
  ports.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));ports.setSelection(Math.min(previous,labels.size()-1));
 }
 private void ensureFileService(){if(BridgeService.instance!=null)return;try{startForegroundService(new Intent(this,BridgeService.class).setAction("load_midi"));}catch(Exception e){notifyUser("启动失败："+e.getMessage());}}
 private void updateFileOptionControls(){fileSpeed.setEnabled(!fileTempoOverride.isChecked());fileBpm.setEnabled(fileTempoOverride.isChecked());fileSourceKey.setEnabled(fileConvertC.isChecked());}
 private boolean saveFileSettings(){
  try{
   android.content.SharedPreferences p=Config.prefs(this);boolean override=fileTempoOverride.isChecked();int speed=p.getInt("midi_speed",100);double bpm=p.getFloat("midi_bpm",MidiFileOptions.DEFAULT_BPM);
   if(!override)try{speed=Integer.parseInt(fileSpeed.getText().toString().trim());}catch(NumberFormatException invalid){throw new IllegalArgumentException("文件速度百分比请填25–200的整数");}
   if(override)try{bpm=Double.parseDouble(fileBpm.getText().toString().trim());}catch(NumberFormatException invalid){throw new IllegalArgumentException("目标速度请填20–400 BPM，可用小数");}
   new MidiFileOptions(speed,override,bpm,fileConvertC.isChecked(),fileSourceKey.getSelectedItemPosition());
   p.edit().putInt("midi_accidental",accidental.getSelectedItemPosition()).putBoolean("midi_fold",fileFold.isChecked()).putBoolean("midi_hold",false).putBoolean("midi_natural",fileNatural.isChecked()).putInt("midi_speed",speed).putBoolean("midi_tempo_override",override).putFloat("midi_bpm",(float)bpm).putBoolean("midi_convert_c",fileConvertC.isChecked()).putInt("midi_source_key",fileSourceKey.getSelectedItemPosition()).apply();
   BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.stop();return true;
  }catch(Exception e){notifyUser(e.getMessage()==null?"文件设置无效":e.getMessage());return false;}
 }
 private void chooseFileTracks(){BridgeService b=BridgeService.instance;if(b==null||b.filePlayer==null||b.filePlayer.song==null){ensureFileService();notifyUser("先导入 MIDI；若正在解析，请稍候");return;}SmfFile song=b.filePlayer.song;boolean[] enabled=MidiFilePlayer.selectedTracks(song,Config.prefs(this).getString("midi_tracks",""));int[] counts=new int[song.trackNames.length];for(SmfFile.Note note:song.notes)if(note.channel!=9)counts[note.track]++;String[] labels=new String[counts.length];for(int i=0;i<counts.length;i++)labels[i]=(i+1)+". "+song.trackNames[i]+"（旋律音 "+counts[i]+"）";
  new AlertDialog.Builder(this).setTitle("选择旋律音轨").setMultiChoiceItems(labels,enabled,(d,w,checked)->enabled[w]=checked).setPositiveButton("保存",(d,w)->{StringBuilder value=new StringBuilder();for(int i=0;i<enabled.length;i++)if(enabled[i])value.append(i).append(',');Config.prefs(this).edit().putString("midi_tracks",value.length()==0?"none":value.toString()).apply();b.filePlayer.stop();notifyUser("已保存音轨选择");}).setNegativeButton("取消",null).show();
 }
 private void importMidi(Uri uri){midiImporting=true;String display="导入的 MIDI";
  try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())display=cursor.getString(0);}catch(Exception ignored){}final String title=display;
  notifyUser("正在导入 MIDI…");new Thread(()->{File temp=new File(getFilesDir(),"midi_import_"+System.nanoTime()+".tmp");try{
   try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("无法读取文件");byte[] buf=new byte[8192];int count,total=0;while((count=in.read(buf))!=-1){total+=count;if(total>SmfFile.MAX_BYTES)throw new IOException("MIDI 文件超过16MiB");out.write(buf,0,count);}}
   SmfFile parsed;try(InputStream in=new FileInputStream(temp)){parsed=SmfFile.read(in);}if(parsed.notes.isEmpty())throw new IOException("文件中没有音符事件");
   if(!temp.renameTo(new File(getFilesDir(),"selected.mid")))throw new IOException("保存 MIDI 失败");
   main.post(()->{midiImporting=false;Config.prefs(this).edit().putString("midi_name",title).remove("midi_tracks").apply();BridgeService b=BridgeService.instance;if(b!=null&&b.filePlayer!=null)b.filePlayer.load();else ensureFileService();notifyUser("已导入："+parsed.notes.size()+"音 / "+parsed.trackNames.length+"轨");});
  }catch(Exception e){temp.delete();main.post(()->{midiImporting=false;notifyUser("MIDI 导入失败："+e.getMessage());});}},"MidiImport").start();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;final Uri uri=data.getData();if(request==43){importMidi(uri);return;}if(request!=42)return;final int note=importNote;
  new Thread(()->{
   File temp=new File(getFilesDir(),"import_"+note+".tmp");
   try {
    try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)) {if(in==null)throw new IOException("无法读取文件");byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>16*1024*1024)throw new IOException("文件超出 16 MiB");out.write(buf,0,n);}}
    try(InputStream in=new FileInputStream(temp)){WavData.read(in);}
    File dest=new File(getFilesDir(),"note_"+note+".wav");if(!temp.renameTo(dest))throw new IOException("保存采样失败");BridgeService b=BridgeService.instance;if(b!=null)b.loadSamples();main.post(()->notifyUser("已导入 "+NoteMap.label(note)));
   }catch(Exception e){temp.delete();main.post(()->notifyUser("导入失败："+e.getMessage()));}
  },"WavImport").start();
 }
 @Override protected void onSaveInstanceState(Bundle state){state.putInt("page",currentPage);state.putInt("import_note",importNote);super.onSaveInstanceState(state);}
 @Override public void onBackPressed(){TouchService service=TouchService.instance;if(service!=null&&service.reuseChecking()){service.cancelReuseTest();return;}if(currentPage!=HOME){showPage(HOME);return;}super.onBackPressed();}
 @Override protected void onResume(){super.onResume();gardenBackground.setForeground(true);main.removeCallbacks(poll);main.post(poll);}
 @Override protected void onPause(){TouchService service=TouchService.instance;if(service!=null&&service.reuseChecking())service.cancelReuseTest();gardenBackground.setForeground(false);main.removeCallbacks(poll);releaseTestKeys();super.onPause();}
 @Override protected void onDestroy(){if(gardenBackground!=null)gardenBackground.setForeground(false);main.removeCallbacksAndMessages(null);super.onDestroy();}
}
