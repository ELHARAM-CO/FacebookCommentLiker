package com.alharam.inviter;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    WebView web;
    TextView status, log, resizeHandle;
    EditText url;
    StepperView limit, delay, hour, minute, ampm;
    ScrollView topPanel, logScroll;
    View webContainer;
    Button fullscreenButton;
    Handler handler = new Handler(Looper.getMainLooper());
    boolean running = false;
    boolean fullscreenWeb = false;
    int sent = 0;
    int noInvitePasses = 0;
    Runnable task;
    SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        webContainer = findViewById(R.id.webContainer);
        fullscreenButton = findViewById(R.id.fullscreenButton);
        status = findViewById(R.id.status);
        log = findViewById(R.id.log);
        logScroll = findViewById(R.id.logScroll);
        url = findViewById(R.id.url);
        limit = findViewById(R.id.limit);
        delay = findViewById(R.id.delay);
        hour = findViewById(R.id.hour);
        minute = findViewById(R.id.minute);
        ampm = findViewById(R.id.ampm);
        topPanel = findViewById(R.id.topPanel);
        resizeHandle = findViewById(R.id.resizeHandle);

        prefs = getSharedPreferences("settings", 0);
        url.setText(prefs.getString("url", ""));
        setupSteppers();
        setupResizableFacebookArea();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        web.setVerticalScrollBarEnabled(true);
        web.setHorizontalScrollBarEnabled(false);
        web.setInitialScale(0);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        web.setNestedScrollingEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String js = "(function(){try{var m=document.querySelector(\"meta[name=\'viewport\"]\");if(!m){m=document.createElement(\"meta\");m.name=\"viewport\";document.head.appendChild(m);}m.content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes\";document.documentElement.style.width=\"100%\";if(document.body){document.body.style.width=\"100%\";document.body.style.maxWidth=\"100%\";}}catch(e){}})();";
                view.evaluateJavascript(js, null);
            }
        });
        web.loadUrl("https://www.facebook.com/");

        findViewById(R.id.open).setOnClickListener(v -> {
            save();
            String u = url.getText().toString().trim();
            if (!u.isEmpty()) web.loadUrl(u);
            else addLog("أدخل رابط المنشور أولًا.");
        });
        findViewById(R.id.start).setOnClickListener(v -> { save(); scheduleOrStart(); });
        findViewById(R.id.stop).setOnClickListener(v -> stop());
        findViewById(R.id.exitApp).setOnClickListener(v -> {
            if (running) stop();
            finishAffinity();
        });
        fullscreenButton.setOnClickListener(v -> setFacebookFullscreen(!fullscreenWeb));
    }

    void setupSteppers() {
        delay.configure(1, 300, prefs.getInt("delay", 20), false, "ثانية");
        limit.configure(1, 1000, prefs.getInt("limit", 10), false, "عملية");

        hour.configure(1, 12, prefs.getInt("hourValue", 0), true, "ساعة");
        minute.configure(0, 59, prefs.getInt("minuteValue", 0), true, "دقيقة");
        ampm.configureText(new String[]{"صباحًا", "مساءً"}, prefs.getInt("ampmValue", -1), true);
    }

    int spinnerValue(StepperView s, int d) {
        int v = s.getNumericValue();
        return v < 0 ? d : v;
    }

    void save() {
        prefs.edit()
                .putString("url", url.getText().toString())
                .putInt("limit", spinnerValue(limit, 10))
                .putInt("delay", spinnerValue(delay, 20))
                .putInt("hourValue", hour.getNumericValue())
                .putInt("minuteValue", minute.getNumericValue())
                .putInt("ampmValue", ampm.getTextIndex())
                .apply();
    }

    String selectedStartTime() {
        int h = hour.getNumericValue();
        int m = minute.getNumericValue();
        int p = ampm.getTextIndex();
        if (h < 0 && m < 0 && p < 0) return "";
        if (h < 0 || m < 0 || p < 0) return null;
        String hs = String.format(Locale.getDefault(), "%02d", h);
        String ms = String.format(Locale.getDefault(), "%02d", m);
        return hs + ":" + ms + " " + ampm.getTextValue();
    }

    void scheduleOrStart() {
        String selected = selectedStartTime();
        if (selected == null) {
            addLog("اختر الساعات والدقائق وصباحًا/مساءً معًا، أو اترك الساعات والدقائق فارغة للبدء فورًا.");
            return;
        }
        if (selected.isEmpty()) { startNow(); return; }
        try {
            int h12 = hour.getNumericValue();
            int m = minute.getNumericValue();
            String p = ampm.getTextValue();
            int h24 = h12 % 12;
            if ("مساءً".equals(p)) h24 += 12;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, h24);
            c.set(Calendar.MINUTE, m);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DATE, 1);
            long ms = c.getTimeInMillis() - System.currentTimeMillis();
            status.setText("⏰ مجدول: " + selected);
            addLog("تمت جدولة التشغيل الساعة " + selected + ".");
            if (task != null) handler.removeCallbacks(task);
            handler.postDelayed(this::startNow, ms);
        } catch (Exception e) {
            addLog("تعذر قراءة وقت البدء.");
        }
    }

    void startNow() {
        running = true;
        sent = 0;
        noInvitePasses = 0;
        int max = Math.min(1000, spinnerValue(limit, 10));
        int d = Math.max(1, Math.min(300, spinnerValue(delay, 20)));
        status.setText("🟢 يعمل: 0 / " + max);
        addLog("بدأ التشغيل من صفحة الـInvite الحالية؛ لن يتم فتح صفحات العملاء.");
        task = new Runnable() {
            @Override public void run() {
                if (!running) return;
                if (sent >= max) { finishRun("اكتمل الحد المحدد: " + sent + " دعوة."); return; }

                String js = "(function(){" +
                        "const norm=t=>(t||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                        "const visible=e=>{if(!e||e.disabled||e.getAttribute('aria-disabled')==='true')return false;const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';};" +
                        "const isInvite=t=>t==='invite'||t==='دعوة'||t==='invite friends'||t==='دعوة الأصدقاء';" +
                        "const candidates=[...document.querySelectorAll('button,[role=\\\"button\\\"]')].filter(visible);" +
                        "let x=candidates.find(e=>{let t=norm(e.innerText||e.textContent);if(!isInvite(t))return false;if(e.closest('a[href]'))return false;return true;});" +
                        "if(x){x.scrollIntoView({block:'center',inline:'nearest'});x.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,view:window}));x.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,cancelable:true,view:window}));x.click();return JSON.stringify({state:'clicked',text:norm(x.innerText||x.textContent),y:window.scrollY});}" +
                        "const before=window.scrollY;const maxY=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);const atEnd=before>=Math.max(0,maxY-40);if(!atEnd)window.scrollBy(0,650);return JSON.stringify({state:atEnd?'end':'scrolled',y:before,maxY:maxY});" +
                        "})()";
                web.evaluateJavascript(js, v -> {
                    if (!running) return;
                    String r = v == null ? "" : v.replace("\\\"", "\"").replace("\\\\", "\\");
                    if (r.contains("\"state\":\"clicked\"")) {
                        sent++; noInvitePasses = 0;
                        status.setText("🟢 تمت دعوة " + sent + " / " + max);
                        addLog("تم الضغط على زر Invite الحقيقي — الدعوة رقم " + sent + ".");
                        handler.postDelayed(task, d * 1000L);
                    } else if (r.contains("\"state\":\"end\"")) {
                        noInvitePasses++;
                        if (noInvitePasses >= 2) finishRun("انتهت قائمة الـInvite — تم تنفيذ " + sent + " دعوة.");
                        else handler.postDelayed(task, 900);
                    } else {
                        noInvitePasses = 0;
                        addLog("لا يوجد زر Invite في الجزء الظاهر؛ تم التمرير داخل نفس الصفحة للبحث عن التالي.");
                        handler.postDelayed(task, 900);
                    }
                });
            }
        };
        handler.post(task);
    }

    void finishRun(String message) {
        running = false;
        if (task != null) handler.removeCallbacks(task);
        status.setText("✅ انتهى التشغيل");
        addLog("✅ " + message);
    }

    void stop() {
        int total = sent;
        running = false;
        if (task != null) handler.removeCallbacks(task);
        status.setText("🔴 متوقف");
        addLog("تم الإيقاف يدويًا — إجمالي الدعوات: " + total + ".");
    }

    void addLog(String s) {
        String tm = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        log.append((log.length() == 0 ? "" : "\n") + "[" + tm + "] " + s);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    void setupResizableFacebookArea() {
        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            float downY; int startWeb;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: downY = e.getRawY(); startWeb = webContainer.getHeight(); v.setPressed(true); return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawY() - downY) > 8) {
                            int desired = (int)(startWeb - (e.getRawY() - downY));
                            applyWebHeight(desired);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (Math.abs(e.getRawY() - downY) < 12) {
                            setFacebookFullscreen(true);
                        } else {
                            prefs.edit().putInt("webHeight", webContainer.getHeight()).apply();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        prefs.edit().putInt("webHeight", webContainer.getHeight()).apply();
                        return true;
                    default: return true;
                }
            }
        });
        // Start with the Facebook area filling its normal allocated portion.
        // A user drag can still save a custom height for the current session.
        resizeHandle.post(() -> {
            ViewGroup.LayoutParams lp = webContainer.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                lp.height = 0;
                ((LinearLayout.LayoutParams) lp).weight = 2f;
                webContainer.setLayoutParams(lp);
            }
            ViewGroup.LayoutParams tlp = topPanel.getLayoutParams();
            if (tlp instanceof LinearLayout.LayoutParams) {
                tlp.height = 0;
                ((LinearLayout.LayoutParams) tlp).weight = 1f;
                topPanel.setLayoutParams(tlp);
            }
        });
    }

    void applyWebHeight(int h) {
        int parentH = ((View) webContainer.getParent()).getHeight();
        if (parentH <= 0) return;
        int minWeb = 180, minTop = 220;
        int maxWeb = Math.max(minWeb, parentH - resizeHandle.getHeight() - minTop);
        h = Math.max(minWeb, Math.min(maxWeb, h));
        ViewGroup.LayoutParams wlp = webContainer.getLayoutParams();
        wlp.height = h;
        if (wlp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams)wlp).weight = 0;
        webContainer.setLayoutParams(wlp);
        ViewGroup.LayoutParams tlp = topPanel.getLayoutParams();
        tlp.height = Math.max(minTop, parentH - resizeHandle.getHeight() - h);
        if (tlp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams)tlp).weight = 0;
        topPanel.setLayoutParams(tlp);
        webContainer.requestLayout(); topPanel.requestLayout();
    }

    void setFacebookFullscreen(boolean full) {
        fullscreenWeb = full;
        if (full) {
            topPanel.setVisibility(View.GONE);
            resizeHandle.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = webContainer.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (lp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams)lp).weight = 1;
            webContainer.setLayoutParams(lp);
            fullscreenButton.setText("تصغير صفحة Facebook");
            fullscreenButton.setVisibility(View.VISIBLE);
            addLog("تم تكبير صفحة Facebook بالكامل للمساعدة في التحقق.");
        } else {
            topPanel.setVisibility(View.VISIBLE);
            resizeHandle.setVisibility(View.VISIBLE);
            fullscreenButton.setVisibility(View.GONE);
            int saved = prefs.getInt("webHeight", 0);
            if (saved > 0) applyWebHeight(saved);
            else {
                ViewGroup.LayoutParams lp = webContainer.getLayoutParams();
                lp.height = 0;
                if (lp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams)lp).weight = 2;
                webContainer.setLayoutParams(lp);
            }
        }
        web.requestLayout();
    }

    @Override public void onBackPressed() {
        if (fullscreenWeb) { setFacebookFullscreen(false); return; }
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }



}
