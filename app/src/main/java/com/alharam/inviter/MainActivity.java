package com.alharam.inviter;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.view.MotionEvent;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
 WebView web; TextView status,log,resizeHandle; EditText url,startTime; Spinner limit,delay; ScrollView topPanel;
 Handler handler=new Handler(Looper.getMainLooper()); boolean running=false; int sent=0; int noInvitePasses=0; int stagnantPasses=0;
 Runnable task; SharedPreferences prefs;

 public void onCreate(Bundle b){
  super.onCreate(b); setContentView(R.layout.activity_main);
  web=findViewById(R.id.web); status=findViewById(R.id.status); log=findViewById(R.id.log);
  url=findViewById(R.id.url); limit=findViewById(R.id.limit); delay=findViewById(R.id.delay); startTime=findViewById(R.id.startTime);
  prefs=getSharedPreferences("settings",0);
  url.setText(prefs.getString("url",""));
  startTime.setText(prefs.getString("time",""));
  setupSpinners();
  topPanel=findViewById(R.id.topPanel); resizeHandle=findViewById(R.id.resizeHandle);
  setupResizableFacebookArea();
  WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setLoadWithOverviewMode(false); s.setUseWideViewPort(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false); web.setVerticalScrollBarEnabled(true); web.setHorizontalScrollBarEnabled(false); web.setOverScrollMode(View.OVER_SCROLL_ALWAYS); web.setNestedScrollingEnabled(true);
  web.setWebViewClient(new WebViewClient()); web.setWebChromeClient(new WebChromeClient());
  web.post(() -> {
    ViewGroup.LayoutParams lp = web.getLayoutParams();
    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
    web.setLayoutParams(lp);
    web.requestLayout();
  });
  web.loadUrl("https://www.facebook.com/");
  findViewById(R.id.open).setOnClickListener(v->{save(); String u=url.getText().toString().trim();
    if(!u.isEmpty()) web.loadUrl(u); else addLog("أدخل رابط المنشور أولًا.");});
  findViewById(R.id.start).setOnClickListener(v->{save(); scheduleOrStart();});
  findViewById(R.id.stop).setOnClickListener(v->{stop();});
 }
 void setupSpinners(){
  ArrayList<String> delays=new ArrayList<>(); for(int i=1;i<=300;i++) delays.add(String.valueOf(i));
  ArrayList<String> limits=new ArrayList<>(); for(int i=1;i<=1000;i++) limits.add(String.valueOf(i));
  ArrayAdapter<String> da=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,delays); da.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); delay.setAdapter(da);
  ArrayAdapter<String> la=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,limits); la.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); limit.setAdapter(la);
  delay.setSelection(Math.max(0,Math.min(299,prefs.getInt("delay",20)-1)));
  limit.setSelection(Math.max(0,Math.min(999,prefs.getInt("limit",10)-1)));
 }
 int spinnerValue(Spinner s,int d){try{return Integer.parseInt(String.valueOf(s.getSelectedItem()));}catch(Exception x){return d;}}
 void save(){prefs.edit().putString("url",url.getText().toString()).putInt("limit",spinnerValue(limit,10))
   .putInt("delay",spinnerValue(delay,20)).putString("time",startTime.getText().toString()).apply();}
 void setupResizableFacebookArea(){
  int saved=prefs.getInt("webHeight",0);
  if(saved>0) web.post(() -> applyWebHeight(saved));
  resizeHandle.setOnTouchListener(new View.OnTouchListener(){float downY; int startWeb;
   public boolean onTouch(View v,MotionEvent e){
    switch(e.getActionMasked()){
     case MotionEvent.ACTION_DOWN: downY=e.getRawY(); startWeb=web.getHeight(); v.setPressed(true); return true;
     case MotionEvent.ACTION_MOVE:
      int desired=(int)(startWeb-(e.getRawY()-downY)); applyWebHeight(desired); return true;
     case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
      v.setPressed(false); prefs.edit().putInt("webHeight",web.getHeight()).apply(); return true;
    } return true;
   }});
 }
 void applyWebHeight(int h){
  int parentH=((View)web.getParent()).getHeight(); int minWeb=120; int minTop=180;
  if(parentH<=0)return;
  int maxWeb=Math.max(minWeb,parentH-resizeHandle.getHeight()-minTop);
  h=Math.max(minWeb,Math.min(maxWeb,h));
  ViewGroup.LayoutParams wlp=web.getLayoutParams(); wlp.height=h; if(wlp instanceof LinearLayout.LayoutParams)((LinearLayout.LayoutParams)wlp).weight=0; web.setLayoutParams(wlp);
  ViewGroup.LayoutParams tlp=topPanel.getLayoutParams(); tlp.height=Math.max(minTop,parentH-resizeHandle.getHeight()-h); if(tlp instanceof LinearLayout.LayoutParams)((LinearLayout.LayoutParams)tlp).weight=0; topPanel.setLayoutParams(tlp);
  web.requestLayout(); topPanel.requestLayout();
 }
 void scheduleOrStart(){
   String t=startTime.getText().toString().trim();
   if(t.isEmpty()){startNow();return;}
   try{
    String[] a=t.split(":"); Calendar c=Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(a[0]));
    c.set(Calendar.MINUTE,Integer.parseInt(a[1])); c.set(Calendar.SECOND,0);
    if(c.getTimeInMillis()<=System.currentTimeMillis()) c.add(Calendar.DATE,1);
    long ms=c.getTimeInMillis()-System.currentTimeMillis(); status.setText("⏰ مجدول: "+t);
    handler.postDelayed(this::startNow,ms); addLog("تمت جدولة التشغيل.");
   }catch(Exception e){addLog("صيغة الوقت غير صحيحة؛ استخدم HH:MM");}
 }
 void startNow(){
  running=true; sent=0; noInvitePasses=0; stagnantPasses=0; int max=Math.min(1000,spinnerValue(limit,10)); int d=Math.max(1,Math.min(300,spinnerValue(delay,20)));
  status.setText("🟢 يعمل: 0 / "+max); addLog("بدأ التشغيل.");
  task=new Runnable(){public void run(){
   if(!running)return; if(sent>=max){stop(); addLog("اكتمل الحد المحدد.");return;}
   String js="(function(){"+
    "let els=[...document.querySelectorAll('button,[role=\\\"button\\\"],span[role=\\\"button\\\"]')].filter(e=>e.offsetParent!==null&&!e.disabled);"+
    "let norm=t=>(t||'').trim().toLowerCase();"+
    "let skip=/^(invited|already invited|following|followed|liked|like|مدعو|تمت الدعوة|مدعو بالفعل|يتابع|متابع|تمت المتابعة|أعجبني|إعجاب|اعجاب)$/i;"+
    "let x=els.find(e=>{let t=norm(e.innerText||e.textContent);"+
    "return (t==='invite'||t==='دعوة'||t.includes('invite'))&&!skip.test(t);});"+
    "let before=window.scrollY;"+
    "if(x){x.click();return JSON.stringify({state:'clicked',y:before});}"+
    "let maxY=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);"+
    "let atEnd=window.scrollY>=Math.max(0,maxY-40);"+
    "window.scrollBy(0,650);"+
    "return JSON.stringify({state:atEnd?'end':'skip',y:before,maxY:maxY});})()";
   web.evaluateJavascript(js,v->{if(!running)return;
     String r=v==null?"":v.replace("\\\"","\"").replace("\\\\","\\");
     if(r.contains("\"state\":\"clicked\"")){
       sent++; noInvitePasses=0; stagnantPasses=0;
       status.setText("🟢 تمت دعوة "+sent+" / "+max);
       addLog("تم العثور على Invite وإرسال الدعوة رقم "+sent+".");
       handler.postDelayed(this,d*1000L);
     }else if(r.contains("\"state\":\"end\"")){
       noInvitePasses++;
       if(noInvitePasses>=1){
         int total=sent;
         running=false;
         if(task!=null)handler.removeCallbacks(task);
         status.setText("✅ انتهى المنشور");
         addLog("✅ انتهى المنشور الحالي — تم تنفيذ "+total+" دعوة.");
         addLog("⏹ تم التوقف تلقائيًا لمنع تكرار نفس المنشور.");
       }else{
         handler.postDelayed(this,700);
       }
     }else{
       noInvitePasses++;
       if(noInvitePasses>=5){
         int total=sent;
         running=false;
         if(task!=null)handler.removeCallbacks(task);
         status.setText("✅ انتهى المنشور");
         addLog("✅ لم يعد هناك Invite جديد بعد الفحص — تم تنفيذ "+total+" دعوة.");
         addLog("⏹ تم التوقف تلقائيًا لمنع التكرار.");
       }else{
         addLog("لا يوجد Invite في الجزء الحالي؛ جاري فحص الجزء التالي.");
         handler.postDelayed(this,700);
       }
     }
   });
  }};
  handler.post(task);
 }
 void stop(){int total=sent; running=false;if(task!=null)handler.removeCallbacks(task);status.setText("🔴 متوقف");addLog("تم الإيقاف يدويًا — إجمالي الدعوات في هذا المنشور: "+total+".");}
 void addLog(String s){String tm=new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date());
  log.append("\n["+tm+"] "+s);}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}