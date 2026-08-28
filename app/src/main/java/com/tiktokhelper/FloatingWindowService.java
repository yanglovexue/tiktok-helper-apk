package com.tiktokhelper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 悬浮窗服务 - 显示当前模式、任务名称、数量等信息
 */
public class FloatingWindowService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvMode, tvTask, tvStats;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private static final String ACTION_UPDATE = "com.tiktokhelper.UPDATE_FLOATING";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_TASK = "task";
    public static final String EXTRA_STATS = "stats";
    
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "tiktok_helper_floating";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        showFloatingWindow();
        startUpdating();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "TikTok Helper",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("TikTok Helper")
                .setContentText("自动化运行中...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        }
        return new Notification();
    }

    private void showFloatingWindow() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
            
            tvMode = floatingView.findViewById(R.id.tv_floating_mode);
            tvTask = floatingView.findViewById(R.id.tv_floating_task);
            tvStats = floatingView.findViewById(R.id.tv_floating_stats);
            
            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            );
            
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 50;
            params.y = 100;
            
            windowManager.addView(floatingView, params);
            updateDisplay("准备中...", "等待启动", "视频: 0 | 评论: 0 | 回复: 0");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startUpdating() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateFromService();
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void updateFromService() {
        if (AutoService.getInstance() != null) {
            String mode = AutoService.warmUpMode ? "养号模式" : "正常模式";
            String task = getCurrentTask();
            String stats = AutoService.getInstance().getStats();
            updateDisplay(mode, task, stats);
        }
    }

    private String getCurrentTask() {
        if (!AutoService.isRunning) return "已停止";
        if (AutoService.warmUpMode) return "浏览中...";
        
        int action = (int) (Math.random() * 100);
        if (action < AutoService.likeChance) return "点赞中...";
        if (action < AutoService.likeChance + AutoService.followChance) return "关注中...";
        if (action < AutoService.likeChance + AutoService.followChance + AutoService.replyChance) return "回复评论中...";
        return "评论中...";
    }

    public void updateDisplay(String mode, String task, String stats) {
        runOnUiThread(() -> {
            if (tvMode != null) tvMode.setText("模式: " + mode);
            if (tvTask != null) tvTask.setText("任务: " + task);
            if (tvStats != null) tvStats.setText(stats);
        });
    }

    private void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE.equals(intent.getAction())) {
            String mode = intent.getStringExtra(EXTRA_MODE);
            String task = intent.getStringExtra(EXTRA_TASK);
            String stats = intent.getStringExtra(EXTRA_STATS);
            updateDisplay(mode, task, stats);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        handler.removeCallbacksAndMessages(null);
    }
}
