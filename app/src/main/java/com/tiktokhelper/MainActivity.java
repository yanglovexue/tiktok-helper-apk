package com.tiktokhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主控制面板
 */
public class MainActivity extends AppCompatActivity {

    private Button btnStart, btnStop, btnOpenTiktok;
    private CheckBox cbLike, cbComment;
    private SeekBar sbLikeChance, sbCommentChance;
    private EditText etComments;
    private TextView tvStatus, tvLikeChance, tvCommentChance;
    
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        updateUI();
    }
    
    private void initViews() {
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnOpenTiktok = findViewById(R.id.btn_open_tiktok);
        cbLike = findViewById(R.id.cb_like);
        cbComment = findViewById(R.id.cb_comment);
        sbLikeChance = findViewById(R.id.sb_like_chance);
        sbCommentChance = findViewById(R.id.sb_comment_chance);
        etComments = findViewById(R.id.et_comments);
        tvStatus = findViewById(R.id.tv_status);
        tvLikeChance = findViewById(R.id.tv_like_chance);
        tvCommentChance = findViewById(R.id.tv_comment_chance);
        
        // 启动按钮
        btnStart.setOnClickListener(v -> {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                openAccessibilitySettings();
                return;
            }
            startAutomation();
        });
        
        // 停止按钮
        btnStop.setOnClickListener(v -> stopAutomation());
        
        // 打开 TikTok
        btnOpenTiktok.setOnClickListener(v -> openTiktok());
        
        // 点赞概率滑动条
        sbLikeChance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLikeChance.setText("点赞概率: " + progress + "%");
                AutoService.likeChance = progress;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 评论概率滑动条
        sbCommentChance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCommentChance.setText("评论概率: " + progress + "%");
                AutoService.commentChance = progress;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 复选框
        cbLike.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AutoService.likeEnabled = isChecked;
        });
        
        cbComment.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AutoService.commentEnabled = isChecked;
        });
    }
    
    /**
     * 启动自动化
     */
    private void startAutomation() {
        // 保存评论
        String commentsText = etComments.getText().toString();
        if (!commentsText.isEmpty()) {
            AutoService.commentList.clear();
            String[] comments = commentsText.split("\n");
            for (String comment : comments) {
                if (!comment.trim().isEmpty()) {
                    AutoService.commentList.add(comment.trim());
                }
            }
        }
        
        AutoService.isRunning = true;
        isRunning = true;
        updateUI();
        
        // 启动滑动线程
        new Thread(() -> {
            while (AutoService.isRunning) {
                try {
                    Thread.sleep(3000);
                    if (AutoService.scrollEnabled) {
                        AutoService service = AutoService.getInstance();
                        if (service != null) {
                            service.performScroll();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
        
        Toast.makeText(this, "自动化已启动", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 停止自动化
     */
    private void stopAutomation() {
        AutoService.isRunning = false;
        isRunning = false;
        updateUI();
        Toast.makeText(this, "自动化已停止", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 打开 TikTok
     */
    private void openTiktok() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.zhiliaoapp.musically");
        if (intent != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "TikTok 未安装", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 检查无障碍服务是否开启
     */
    private boolean isAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices != null) {
            return enabledServices.contains(getPackageName() + "/" + AutoService.class.getName());
        }
        return false;
    }
    
    /**
     * 打开无障碍设置页面
     */
    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }
    
    /**
     * 更新 UI 状态
     */
    private void updateUI() {
        tvStatus.setText(isRunning ? "状态: 运行中 🟢" : "状态: 已停止 🔴");
        btnStart.setEnabled(!isRunning);
        btnStop.setEnabled(isRunning);
        
        // 初始化评论输入框
        if (etComments.getText().toString().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("I got the same for way less DM me 👕\n");
            sb.append("This is fire 🔥 where can I get one?\n");
            sb.append("I have this in stock, DM for details\n");
            sb.append("Too expensive on their site lol I found cheaper\n");
            sb.append("Bro I can get you the same for 60% off, DM me");
            etComments.setText(sb.toString());
        }
        
        // 初始化滑动条
        sbLikeChance.setProgress(AutoService.likeChance);
        sbCommentChance.setProgress(AutoService.commentChance);
        cbLike.setChecked(AutoService.likeEnabled);
        cbComment.setChecked(AutoService.commentEnabled);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
}
