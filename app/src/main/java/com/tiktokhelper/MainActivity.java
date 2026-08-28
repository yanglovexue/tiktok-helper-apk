package com.tiktokhelper;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主控制面板 - 全功能版
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TikTokHelper";
    
    private Button btnStart, btnStop, btnSearch, btnOpenUrl;
    private CheckBox cbLike, cbComment, cbFollow, cbReply, cbWarmUp, cbFloat;
    private SeekBar sbLikeChance, sbCommentChance, sbFollowChance, sbReplyChance;
    private EditText etComments, etReplies, etKeyword, etUrl;
    private TextView tvStatus, tvLikeChance, tvCommentChance, tvFollowChance, tvReplyChance;
    
    private boolean isRunning = false;
    private Thread scrollThread;

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
        // btnOpenTiktok removed - auto-launch on start
        btnSearch = findViewById(R.id.btn_search);
        btnOpenUrl = findViewById(R.id.btn_open_url);
        
        cbLike = findViewById(R.id.cb_like);
        cbComment = findViewById(R.id.cb_comment);
        cbFollow = findViewById(R.id.cb_follow);
        cbReply = findViewById(R.id.cb_reply);
        cbWarmUp = findViewById(R.id.cb_warmup);
        cbFloat = findViewById(R.id.cb_float);
        
        sbLikeChance = findViewById(R.id.sb_like_chance);
        sbCommentChance = findViewById(R.id.sb_comment_chance);
        sbFollowChance = findViewById(R.id.sb_follow_chance);
        sbReplyChance = findViewById(R.id.sb_reply_chance);
        
        etComments = findViewById(R.id.et_comments);
        etReplies = findViewById(R.id.et_replies);
        etKeyword = findViewById(R.id.et_keyword);
        etUrl = findViewById(R.id.et_url);
        
        tvStatus = findViewById(R.id.tv_status);
        tvLikeChance = findViewById(R.id.tv_like_chance);
        tvCommentChance = findViewById(R.id.tv_comment_chance);
        tvFollowChance = findViewById(R.id.tv_follow_chance);
        tvReplyChance = findViewById(R.id.tv_reply_chance);
        
        // 启动按钮
        btnStart.setOnClickListener(v -> {
            startAutomation();
        });
        
        // 停止按钮
        btnStop.setOnClickListener(v -> stopAutomation());
        
        // 搜索并评论
        btnSearch.setOnClickListener(v -> {
            String keyword = etKeyword.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            AutoService.searchKeyword = keyword;
            if (AutoService.getInstance() == null) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                return;
            }
            AutoService.getInstance().searchAndComment(keyword);
            Toast.makeText(this, "正在搜索: " + keyword, Toast.LENGTH_SHORT).show();
        });
        
        // 打开链接并评论
        btnOpenUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入视频链接", Toast.LENGTH_SHORT).show();
                return;
            }
            AutoService.targetVideoUrl = url;
            if (AutoService.getInstance() == null) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                return;
            }
            AutoService.getInstance().openUrlAndComment(url);
            Toast.makeText(this, "正在打开链接", Toast.LENGTH_SHORT).show();
        });
        
        // 概率滑动条
        sbLikeChance.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLikeChance.setText("点赞概率: " + progress + "%");
                AutoService.likeChance = progress;
            }
        });
        
        sbCommentChance.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCommentChance.setText("评论概率: " + progress + "%");
                AutoService.commentChance = progress;
            }
        });
        
        sbFollowChance.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvFollowChance.setText("关注概率: " + progress + "%");
                AutoService.followChance = progress;
            }
        });
        
        sbReplyChance.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvReplyChance.setText("回复概率: " + progress + "%");
                AutoService.replyChance = progress;
            }
        });
        
        // 复选框
        cbLike.setOnCheckedChangeListener((buttonView, isChecked) -> AutoService.likeEnabled = isChecked);
        cbComment.setOnCheckedChangeListener((buttonView, isChecked) -> AutoService.commentEnabled = isChecked);
        cbFollow.setOnCheckedChangeListener((buttonView, isChecked) -> AutoService.followEnabled = isChecked);
        cbReply.setOnCheckedChangeListener((buttonView, isChecked) -> AutoService.replyCommentEnabled = isChecked);
        cbWarmUp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AutoService.warmUpMode = isChecked;
            // 养号模式下禁用无关功能开关，无需手动取消勾选
            updateFeatureControls(isChecked);
        });
        
        // 悬浮窗开关
        cbFloat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("float_enabled", isChecked).apply();
            if (isChecked) {
                // 检查悬浮窗权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "需要悬浮窗权限，请在系统设置中开启", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    // 等待用户授权后 onResume 再启动
                    buttonView.setChecked(false);
                    return;
                }
                startFloatingWindow();
            } else {
                stopFloatingWindow();
            }
        });
    }
    
    private void startFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, FloatingWindowService.class);
        startService(intent);
        Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show();
    }
    
    private void stopFloatingWindow() {
        stopService(new Intent(this, FloatingWindowService.class));
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 养号模式：自动勾选涉及养号的功能（点赞/关注），取消并禁用其他（评论/回复）
     */
    private void updateFeatureControls(boolean warmUpMode) {
        if (warmUpMode) {
            // 涉及养号的功能：自动勾选（养号模式内部会点赞/关注）
            cbLike.setChecked(true);
            cbFollow.setChecked(true);
            // 与养号无关的功能：取消勾选
            cbComment.setChecked(false);
            cbReply.setChecked(false);
        }
        // 养号模式下全部锁定，防止手动修改
        cbLike.setEnabled(!warmUpMode);
        cbComment.setEnabled(!warmUpMode);
        cbFollow.setEnabled(!warmUpMode);
        cbReply.setEnabled(!warmUpMode);
    }
    
    private void startAutomation() {
        // 检查无障碍服务是否开启
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
            openAccessibilitySettings();
            return;
        }
        
        // 保存评论
        saveComments();
        
        AutoService.isRunning = true;
        isRunning = true;
        updateUI();
        
        // 自动打开 TikTok
        openTiktok();
        
        // 启动滑动线程（先停止旧的线程，避免重复启动）
        if (scrollThread != null && scrollThread.isAlive()) {
            scrollThread.interrupt();
        }
        scrollThread = new Thread(() -> {
            while (AutoService.isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3000);
                    if (AutoService.scrollEnabled && AutoService.getInstance() != null) {
                        AutoService.getInstance().performScroll();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        scrollThread.start();
        
        Toast.makeText(this, "自动化已启动", Toast.LENGTH_SHORT).show();
    }
    
    private void stopAutomation() {
        AutoService.isRunning = false;
        isRunning = false;
        updateUI();
        Toast.makeText(this, "自动化已停止", Toast.LENGTH_SHORT).show();
    }
    
    private void saveComments() {
        // 保存主评论（先清空再填充）
        AutoService.commentList.clear();
        String commentsText = etComments.getText().toString();
        String[] comments = commentsText.split("\n");
        for (String comment : comments) {
            if (!comment.trim().isEmpty()) {
                AutoService.commentList.add(comment.trim());
            }
        }
        
        // 保存回复评论（先清空再填充）
        AutoService.replyList.clear();
        String repliesText = etReplies.getText().toString();
        String[] replies = repliesText.split("\n");
        for (String reply : replies) {
            if (!reply.trim().isEmpty()) {
                AutoService.replyList.add(reply.trim());
            }
        }
    }
    
    private void openTiktok() {
        try {
            // 尝试多种方式打开 TikTok
            String[] tiktokPackages = {
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme"
            };
            
            for (String pkg : tiktokPackages) {
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    startActivity(intent);
                    return;
                }
            }
            
            // 如果 getLaunchIntentForPackage 失败，尝试直接启动 SplashActivity
            Intent intent = new Intent();
            intent.setClassName("com.zhiliaoapp.musically", "com.ss.android.ugc.aweme.splash.SplashActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "TikTok 未安装", Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean isAccessibilityEnabled() {
        try {
            String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabledServices != null) {
                String expectedService = getPackageName() + "/" + AutoService.class.getName();
                return enabledServices.contains(expectedService);
            }
        } catch (Exception e) {
            Log.e(TAG, "检查无障碍服务失败: " + e.getMessage());
        }
        return false;
    }
    
    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }
    
    private void updateUI() {
        tvStatus.setText(isRunning ? "状态: 运行中 🟢" : "状态: 已停止 🔴");
        btnStart.setEnabled(!isRunning);
        btnStop.setEnabled(isRunning);
        
        // 初始化默认评论
        if (etComments.getText().toString().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("I got the same for way less DM me\n");
            sb.append("This is fire! Where can I get one?\n");
            sb.append("I have this in stock, DM for details\n");
            sb.append("Too expensive on their site, I found cheaper\n");
            sb.append("Bro I can get you the same for 60% off, DM me");
            etComments.setText(sb.toString());
        }
        
        if (etReplies.getText().toString().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Thanks!\n");
            sb.append("Exactly!\n");
            sb.append("Same here!\n");
            sb.append("Great taste!\n");
            sb.append("Love this!");
            etReplies.setText(sb.toString());
        }
        
        // 初始化滑动条
        sbLikeChance.setProgress(AutoService.likeChance);
        sbCommentChance.setProgress(AutoService.commentChance);
        sbFollowChance.setProgress(AutoService.followChance);
        sbReplyChance.setProgress(AutoService.replyChance);
        
        cbLike.setChecked(AutoService.likeEnabled);
        cbComment.setChecked(AutoService.commentEnabled);
        cbFollow.setChecked(AutoService.followEnabled);
        cbReply.setChecked(AutoService.replyCommentEnabled);
        cbWarmUp.setChecked(AutoService.warmUpMode);
        
        // 悬浮窗开关状态（有权限且服务运行中则勾选）
        boolean floatPref = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("float_enabled", false);
        boolean hasOverlayPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        cbFloat.setChecked(floatPref && hasOverlayPerm);
        
        // 之前勾选过且权限正常但服务未运行 → 自动重启悬浮窗（如进程被系统回收后）
        if (floatPref && hasOverlayPerm && !isServiceRunning(FloatingWindowService.class)) {
            startService(new Intent(this, FloatingWindowService.class));
        }
        
        // 养号模式下同步禁用功能开关
        updateFeatureControls(AutoService.warmUpMode);
    }
    
    private boolean isFloatingWindowRunning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return false;
        }
        return getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("float_enabled", false);
    }
    
    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止滑动线程，避免 Activity 销毁后线程仍在运行
        if (scrollThread != null) {
            scrollThread.interrupt();
            scrollThread = null;
        }
    }
    
    // 简化的 SeekBar 监听器
    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
