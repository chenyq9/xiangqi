package com.operit.xiangqi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText editApiUrl, editApiKey, editModel;
    private SharedPreferences prefs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("xiangqi_api", MODE_PRIVATE);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackgroundColor(0xFFE8D5B5);
        
        // 标题
        TextView title = new TextView(this);
        title.setText("API 设置");
        title.setTextSize(20);
        title.setTextColor(0xFF3A2A1F);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        layout.addView(title);
        
        // API URL
        layout.addView(label("API 地址："));
        editApiUrl = new EditText(this);
        editApiUrl.setText(prefs.getString("api_url", "https://api.openai.com/v1/chat/completions"));
        editApiUrl.setTextSize(14);
        editApiUrl.setSingleLine();
        layout.addView(editApiUrl, params());
        
        layout.addView(spacer());
        
        // API Key
        layout.addView(label("API Key："));
        editApiKey = new EditText(this);
        editApiKey.setText(prefs.getString("api_key", ""));
        editApiKey.setTextSize(14);
        editApiKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(editApiKey, params());
        
        layout.addView(spacer());
        
        // Model
        layout.addView(label("模型名称："));
        editModel = new EditText(this);
        editModel.setText(prefs.getString("model", "gpt-4o-mini"));
        editModel.setTextSize(14);
        editModel.setSingleLine();
        layout.addView(editModel, params());
        
        layout.addView(spacer());
        
        // 保存按钮
        Button btnSave = new Button(this);
        btnSave.setText("保存设置");
        btnSave.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { saveSettings(); } });
        layout.addView(btnSave);
        
        layout.addView(spacer());
        
        // 提示
        TextView hint = new TextView(this);
        hint.setText("提示：支持 OpenAI/Claude/其他兼容 API\n发送消息时会自动附带当前棋局 FEN");
        hint.setTextSize(12);
        hint.setTextColor(0xFF6B3A1F);
        hint.setPadding(0, dp(16), 0, 0);
        layout.addView(hint);
        
        setContentView(layout);
    }
    
    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFF3A2A1F);
        return tv;
    }
    
    private View spacer() {
        View v = new View(this);
        v.setMinimumHeight(dp(12));
        return v;
    }
    
    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    
    private int dp(int v) {
        return (int)(getResources().getDisplayMetrics().density * v);
    }
    
    private void saveSettings() {
        String url = editApiUrl.getText().toString().trim();
        String key = editApiKey.getText().toString().trim();
        String model = editModel.getText().toString().trim();
        
        if (url.isEmpty() || key.isEmpty() || model.isEmpty()) {
            Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show();
            return;
        }
        
        prefs.edit()
            .putString("api_url", url)
            .putString("api_key", key)
            .putString("model", model)
            .apply();
        
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}