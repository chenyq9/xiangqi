package com.operit.xiangqi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 中国象棋 - 带 LLM 对话
 */
public class MainActivity extends Activity {
    private XiangqiEngine engine = new XiangqiEngine();
    private XiangqiBoardView boardView;
    private TextView statusText;
    private LinearLayout editBar;
    private EditText chatInput;
    private Button sendBtn;
    private ScrollView chatScroll;
    private TextView chatHistory;
    private SharedPreferences prefs;

    private boolean editMode = false;
    private int currentPalette = XiangqiEngine.RKING;
    private List<String> history = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());

    private static final String[] PIECE_NAMES = {"帅","仕","相","马","车","炮","兵","将","士","象","马","车","炮","卒"};
    private static final int[] PIECE_VALUES = {
            XiangqiEngine.RKING, XiangqiEngine.RADVISOR, XiangqiEngine.RBISHOP,
            XiangqiEngine.RKNIGHT, XiangqiEngine.RROOK, XiangqiEngine.RCANNON, XiangqiEngine.RPAWN,
            XiangqiEngine.BKING, XiangqiEngine.BADVISOR, XiangqiEngine.BBISHOP,
            XiangqiEngine.BKNIGHT, XiangqiEngine.BROOK, XiangqiEngine.BCANNON, XiangqiEngine.BPAWN
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("xiangqi", MODE_PRIVATE);
        buildUI();
        boardView.setListener(new XiangqiBoardView.BoardListener() {
            @Override public void onBoardChanged(String fen, String status) { updateStatus(status); }
            @Override public void onMoveMade(int from, int to) { history.add(engine.toFen()); }
        });
        refreshStatus();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView title = new TextView(this);
        title.setText("中国象棋");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        boardView = new XiangqiBoardView(this, engine);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f);
        boardView.setLayoutParams(boardLp);
        root.addView(boardView);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setPadding(dp(4), dp(4), dp(4), dp(4));
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        editBar = buildEditBar();
        editBar.setVisibility(View.GONE);
        root.addView(editBar);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(btnRow);
        addButton(btnRow, "新局", new View.OnClickListener() { @Override public void onClick(View v) { newGame(); } });
        addButton(btnRow, "撤销", new View.OnClickListener() { @Override public void onClick(View v) { undo(); } });
        addButton(btnRow, "摆棋", new View.OnClickListener() { @Override public void onClick(View v) { toggleEditMode(); } });
        addButton(btnRow, "FEN", new View.OnClickListener() { @Override public void onClick(View v) { showFenDialog(); } });
        addButton(btnRow, "设置", new View.OnClickListener() { @Override public void onClick(View v) { showSettings(); } });

        // 聊天区
        chatScroll = new ScrollView(this);
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f);
        chatScroll.setLayoutParams(chatLp);
        chatHistory = new TextView(this);
        chatHistory.setTextSize(13);
        chatHistory.setPadding(dp(4), dp(4), dp(4), dp(4));
        chatScroll.addView(chatHistory);
        root.addView(chatScroll);

        // 输入行
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(inputRow);
        chatInput = new EditText(this);
        chatInput.setHint("输入消息，按发送");
        chatInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(chatInput);
        sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sendMessage(); } });
        inputRow.addView(sendBtn);

        setContentView(root);
        appendChat("系统", "点击“设置”配置 API Key 后即可对话。");
    }

    private LinearLayout buildEditBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(0, dp(4), 0, dp(4));

        TextView hint = new TextView(this);
        hint.setText("摆棋：选棋子类型，点棋盘放置；“擦除”删除。");
        hint.setTextSize(12);
        bar.addView(hint);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(row);
        bar.addView(hsv);

        for (int i = 0; i < PIECE_NAMES.length; i++) {
            final int val = PIECE_VALUES[i];
            Button b = new Button(this);
            b.setText(PIECE_NAMES[i]);
            b.setAllCaps(false);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                currentPalette = val;
                boardView.setPalette(val);
                toast("放置: " + XiangqiEngine.pieceName(val));
            }});
            row.addView(b);
        }
        Button erase = new Button(this);
        erase.setText("擦除");
        erase.setAllCaps(false);
        erase.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            currentPalette = XiangqiEngine.EMPTY;
            boardView.setPalette(XiangqiEngine.EMPTY);
            toast("擦除");
        }});
        row.addView(erase);
        Button done = new Button(this);
        done.setText("完成");
        done.setAllCaps(false);
        done.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleEditMode(); } });
        row.addView(done);

        return bar;
    }

    private void addButton(LinearLayout row, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(lp);
        row.addView(b);
    }

    private void newGame() {
        history.clear();
        engine.reset();
        boardView.setLastMove(-1, -1);
        boardView.invalidate();
        if (editMode) toggleEditMode();
        refreshStatus();
        toast("新局");
    }

    private void undo() {
        if (history.isEmpty()) { toast("无可撤销"); return; }
        engine.setFen(history.remove(history.size() - 1));
        boardView.invalidate();
        refreshStatus();
    }

    private void toggleEditMode() {
        editMode = !editMode;
        boardView.setEditMode(editMode);
        editBar.setVisibility(editMode ? View.VISIBLE : View.GONE);
        if (editMode) {
            history.clear();
            boardView.setSelected(-1, -1);
            boardView.setLastMove(-1, -1);
        }
        refreshStatus();
    }

    private void showFenDialog() {
        EditText et = new EditText(this);
        et.setText(engine.toFen());
        et.setSingleLine(true);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("FEN").setView(et)
                .setPositiveButton("导入", null)
                .setNeutralButton("复制", null)
                .setNegativeButton("关闭", null).create();
        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener() { @Override public void onShow(android.content.DialogInterface di) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                try { engine.setFen(et.getText().toString().trim()); history.clear(); boardView.invalidate(); refreshStatus(); toast("已导入"); dlg.dismiss(); }
                catch (Exception e) { toast("无效FEN"); }
            }});
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("fen", et.getText().toString()));
                toast("已复制");
            }});
        }});
        dlg.show();
    }

    private void showSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText apiUrlEt = new EditText(this);
        apiUrlEt.setHint("API URL (如 https://api.openai.com/v1/chat/completions)");
        apiUrlEt.setText(prefs.getString("api_url", ""));
        layout.addView(apiUrlEt);

        final EditText apiKeyEt = new EditText(this);
        apiKeyEt.setHint("API Key");
        apiKeyEt.setText(prefs.getString("api_key", ""));
        layout.addView(apiKeyEt);

        final EditText modelEt = new EditText(this);
        modelEt.setHint("模型名 (如 gpt-4o)");
        modelEt.setText(prefs.getString("model", "gpt-4o"));
        layout.addView(modelEt);

        new AlertDialog.Builder(this)
                .setTitle("设置 LLM API")
                .setView(layout)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface di, int which) {
                        prefs.edit()
                                .putString("api_url", apiUrlEt.getText().toString().trim())
                                .putString("api_key", apiKeyEt.getText().toString().trim())
                                .putString("model", modelEt.getText().toString().trim())
                                .apply();
                        toast("已保存");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void sendMessage() {
        String text = chatInput.getText().toString().trim();
        if (text.isEmpty()) return;
        chatInput.setText("");
        appendChat("我", text);

        final String apiUrl = prefs.getString("api_url", "");
        final String apiKey = prefs.getString("api_key", "");
        final String model = prefs.getString("model", "gpt-4o");

        if (apiUrl.isEmpty() || apiKey.isEmpty()) {
            appendChat("系统", "请先配置 API URL 和 Key（点“设置”）");
            return;
        }

        sendBtn.setEnabled(false);
        sendBtn.setText("...");

        final String userMsg = text;
        final String fen = engine.toFen();
        
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build();
                
                String systemPrompt = "你是中国象棋助手。当前FEN: " + fen + "。根据用户请求分析局面或给出走法建议。";
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(new JSONObject().put("role", "user").put("content", userMsg)));
                body.put("max_tokens", 1024);

                Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();

                Response response = client.newCall(request).execute();
                final String respBody = response.body() != null ? response.body().string() : "";
                final int code = response.code();
                
                handler.post(() -> {
                    if (code == 200) {
                        try {
                            JSONObject resp = new JSONObject(respBody);
                            String reply = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            appendChat("AI", reply);
                        } catch (Exception e) {
                            appendChat("系统", "解析错误: " + e.getMessage() + "\n" + respBody);
                        }
                    } else {
                        appendChat("系统", "API错误(" + code + "): " + respBody);
                    }
                    sendBtn.setEnabled(true);
                    sendBtn.setText("发送");
                });
            } catch (final Exception e) {
                handler.post(() -> {
                    appendChat("系统", "请求失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    sendBtn.setEnabled(true);
                    sendBtn.setText("发送");
                });
            }
        }).start();
    }

    private void appendChat(String who, String msg) {
        chatHistory.append("【" + who + "】 " + msg + "\n\n");
        handler.post(new Runnable() { @Override public void run() {
            chatScroll.fullScroll(View.FOCUS_DOWN);
        }});
    }

    private void refreshStatus() {
        String s = engine.statusText();
        if (editMode) s = "摆棋" + (s.isEmpty() ? "" : " - " + s);
        updateStatus(s);
        boardView.invalidate();
    }

    private void updateStatus(String s) {
        if (s == null || s.isEmpty()) s = (engine.side() == 0 ? "红方走" : "黑方走");
        if (editMode && !s.contains("摆棋")) s = "摆棋 - " + s;
        statusText.setText(s);
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }
}