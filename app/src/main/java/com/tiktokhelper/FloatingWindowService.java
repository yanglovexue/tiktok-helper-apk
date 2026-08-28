package com.tiktokhelper;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 悬浮窗服务 - 简化版
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindow";
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvMode, tvTask, tvStats;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "FloatingWindowService onCreate");
        try {
            showFloatingWindow();
            startUpdating();
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    private void showFloatingWindow() {
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
        Log.d(TAG, "Floating window shown");
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
        return "运行中...";
    }

    private void updateDisplay(String mode, String task, String stats) {
        if (tvMode != null) tvMode.setText("模式: " + mode);
        if (tvTask != null) tvTask.setText("任务: " + task);
        if (tvStats != null) tvStats.setText(stats);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing view: " + e.getMessage());
            }
        }
        handler.removeCallbacksAndMessages(null);
    }
}
