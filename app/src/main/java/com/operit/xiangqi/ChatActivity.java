package com.operit.xiangqi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

public class ChatActivity extends Activity {
    private LinearLayout chatContainer;
    private EditText editMessage;
    private Button btnSend;
    private ScrollView scrollView;
    private String currentFen;
    private SharedPreferences prefs;
    private ArrayList<JSONObject> history = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("xiangqi_api", MODE_PRIVATE);
        currentFen = getIntent().getStringExtra("fen");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFFE8D5B5);
        
        // 标题栏
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(0xFF6B3A1F);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        
        TextView title = new TextView(this);
        title.setText("棋局讨论");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        TextView fenIndicator = new TextView(this);
        fenIndicator.setText("FEN已附带");
        fenIndicator.setTextColor(0xFFE8D5B5);
        fenIndicator.setTextSize(12);
        header.addView(fenIndicator);
        
        layout.addView(header);
        
        // 聊天区域
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(dp(8), dp(8), dp(8), dp(8));
        
        scrollView = new ScrollView(this);
        scrollView.addView(chatContainer);
        layout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        
        // 输入区域
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setBackgroundColor(Color.WHITE);
        inputBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        
        editMessage = new EditText(this);
        editMessage.setHint("输入消息...");
        editMessage.setTextSize(14);
        inputBar.addView(editMessage, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        btnSend = new Button(this);
        btnSend.setText("发送");
        btnSend.setOnClickListener(new View.OnClickListener() { 
            @Override public void onClick(View v) { sendMessage(); } 
        });
        inputBar.addView(btnSend);
        
        layout.addView(inputBar);
        
        // 欢迎消息
        addMessage("助手", "你好！我是你的象棋助手。当前棋局 FEN：" + currentFen + "\n你可以问我走法建议、局面分析或任何问题。", false);
        
        setContentView(layout);
    }
    
    private void addMessage(String sender, String text, boolean isUser) {
        TextView tv = new TextView(this);
        tv.setTextSize(14);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        
        if (isUser) {
            tv.setText("我：" + text);
            tv.setBackgroundColor(0xFF4CAF50);
            tv.setTextColor(Color.WHITE);
            lp.gravity = Gravity.END;
        } else {
            tv.setText(sender + "：\n" + text);
            tv.setBackgroundColor(Color.WHITE);
            tv.setTextColor(0xFF3A2A1F);
            lp.gravity = Gravity.START;
        }
        
        tv.setLayoutParams(lp);
        chatContainer.addView(tv);
        
        handler.postDelayed(new Runnable() { 
            @Override public void run() { scrollView.fullScroll(View.FOCUS_DOWN); } 
        }, 100);
    }
    
    private void sendMessage() {
        final String msg = editMessage.getText().toString().trim();
        if (msg.isEmpty()) return;
        
        final String apiUrl = prefs.getString("api_url", "");
        final String apiKey = prefs.getString("api_key", "");
        final String model = prefs.getString("model", "");
        
        if (apiUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            Toast.makeText(this, "请先设置 API", Toast.LENGTH_SHORT).show();
            return;
        }
        
        addMessage("我", msg, true);
        editMessage.setText("");
        btnSend.setEnabled(false);
        
        new Thread(new Runnable() { @Override public void run() {
            try {
                // 构建消息
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一个中国象棋助手。用户当前棋局的 FEN 是：" + currentFen + "。请用中文回答，可以分析局面、给出走法建议或讨论棋局。走法请用中文格式如'炮二平五'。");
                
                final JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", msg);
                
                JSONArray messages = new JSONArray();
                messages.put(systemMsg);
                for (JSONObject h : history) messages.put(h);
                messages.put(userMsg);
                
                // 构建请求
                JSONObject request = new JSONObject();
                request.put("model", model);
                request.put("messages", messages);
                
                // 发送请求
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                
                OutputStream os = conn.getOutputStream();
                os.write(request.toString().getBytes("UTF-8"));
                os.close();
                
                final int code = conn.getResponseCode();
                Scanner scanner;
                if (code == 200) {
                    scanner = new Scanner(conn.getInputStream());
                } else {
                    scanner = new Scanner(conn.getErrorStream());
                }
                scanner.useDelimiter("\\A");
                final String response = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                
                if (code == 200) {
                    JSONObject resp = new JSONObject(response);
                    final String reply = resp.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                    
                    // 记录历史
                    history.add(userMsg);
                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", reply);
                    history.add(assistantMsg);
                    
                    handler.post(new Runnable() { @Override public void run() {
                        addMessage("助手", reply, false);
                        btnSend.setEnabled(true);
                    }});
                } else {
                    handler.post(new Runnable() { @Override public void run() {
                        addMessage("系统", "API 错误：" + code + "\n" + response, false);
                        btnSend.setEnabled(true);
                    }});
                }
            } catch (final Exception e) {
                handler.post(new Runnable() { @Override public void run() {
                    addMessage("系统", "请求失败：" + e.getMessage(), false);
                    btnSend.setEnabled(true);
                }});
            }
        }}).start();
    }
    
    private int dp(int v) {
        return (int)(getResources().getDisplayMetrics().density * v);
    }
}