package com.tiktokhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TikTok 自动化服务 - 通过无障碍服务实现
 */
public class AutoService extends AccessibilityService {

    private static final String TAG = "TikTokHelper";
    private static final String TIKTOK_PACKAGE = "com.zhiliaoapp.musically";
    
    // 单例引用
    private static AutoService instance;
    
    public static AutoService getInstance() {
        return instance;
    }
    
    // 控制开关
    public static boolean isRunning = false;
    public static boolean likeEnabled = true;
    public static boolean commentEnabled = true;
    public static boolean scrollEnabled = true;
    
    // 概率设置
    public static int likeChance = 70;
    public static int commentChance = 20;
    
    // 评论内容
    public static List<String> commentList = new ArrayList<>();
    
    private Random random = new Random();
    private int videoCount = 0;
    private long lastActionTime = 0;
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "服务已连接");
        initComments();
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRunning) return;
        
        // 检查是否是 TikTok
        if (event.getPackageName() == null || 
            !event.getPackageName().equals(TIKTOK_PACKAGE)) {
            return;
        }
        
        // 控制操作频率，不要太快
        long now = System.currentTimeMillis();
        if (now - lastActionTime < 2000) return;
        
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        
        try {
            // 执行自动化操作
            performActions(rootNode);
        } catch (Exception e) {
            Log.e(TAG, "操作失败: " + e.getMessage());
        }
        
        rootNode.recycle();
    }
    
    /**
     * 执行自动化操作
     */
    private void performActions(AccessibilityNodeInfo rootNode) {
        // 1. 尝试点赞
        if (likeEnabled && random.nextInt(100) < likeChance) {
            if (performLike(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
        
        // 2. 尝试评论
        if (commentEnabled && random.nextInt(100) < commentChance) {
            if (performComment(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
    }
    
    /**
     * 执行点赞
     */
    private boolean performLike(AccessibilityNodeInfo rootNode) {
        // 方法1：双击屏幕
        performDoubleClick();
        Log.d(TAG, "执行双击点赞");
        return true;
    }
    
    /**
     * 执行评论
     */
    private boolean performComment(AccessibilityNodeInfo rootNode) {
        // 查找评论按钮
        AccessibilityNodeInfo commentBtn = findNodeByDesc(rootNode, "Comment");
        if (commentBtn == null) {
            commentBtn = findNodeById(rootNode, "com.zhiliaoapp.musically:id/comment");
        }
        
        if (commentBtn == null) return false;
        
        // 点击评论按钮
        commentBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        commentBtn.recycle();
        
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 重新获取根节点
        AccessibilityNodeInfo newRoot = getRootInActiveWindow();
        if (newRoot == null) return false;
        
        // 查找输入框
        AccessibilityNodeInfo inputBox = findNodeByClass(newRoot, "android.widget.EditText");
        if (inputBox == null) {
            newRoot.recycle();
            return false;
        }
        
        // 输入评论
        String comment = commentList.get(random.nextInt(commentList.size()));
        Bundle args = new Bundle();
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, 
            comment
        );
        inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        inputBox.recycle();
        
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 重新获取根节点找发送按钮
        AccessibilityNodeInfo sendRoot = getRootInActiveWindow();
        if (sendRoot != null) {
            AccessibilityNodeInfo sendBtn = findNodeByText(sendRoot, "Send");
            if (sendBtn == null) {
                sendBtn = findNodeByDesc(sendRoot, "Send");
            }
            if (sendBtn != null) {
                sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                sendBtn.recycle();
            }
            sendRoot.recycle();
        }
        
        // 返回
        try {
            Thread.sleep(500);
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Log.d(TAG, "评论: " + comment);
        return true;
    }
    
    /**
     * 执行滑动（下一个视频）
     */
    public void performScroll() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        
        int startX = width / 2;
        int startY = (int) (height * 0.7);
        int endY = (int) (height * 0.3);
        
        Rect path = new Rect();
        // 使用 GestureDescription 进行滑动
        android.graphics.Path swipePath = new android.graphics.Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(startX, endY);
        
        android.accessibilityservice.GestureDescription.Builder builder = 
            new android.accessibilityservice.GestureDescription.Builder();
        android.accessibilityservice.GestureDescription.StrokeDescription stroke = 
            new android.accessibilityservice.GestureDescription.StrokeDescription(
                swipePath, 0, 300, false
            );
        builder.addStroke(stroke);
        
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "滑动完成");
                videoCount++;
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
            }
        }, null);
    }
    
    /**
     * 双击屏幕点赞
     */
    private void performDoubleClick() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        
        int x = width / 2;
        int y = height / 2;
        
        android.graphics.Path clickPath = new android.graphics.Path();
        clickPath.moveTo(x, y);
        
        android.accessibilityservice.GestureDescription.Builder builder = 
            new android.accessibilityservice.GestureDescription.Builder();
        
        // 第一次点击
        android.accessibilityservice.GestureDescription.StrokeDescription click1 = 
            new android.accessibilityservice.GestureDescription.StrokeDescription(
                clickPath, 0, 50, true
            );
        
        // 第二次点击
        android.accessibilityservice.GestureDescription.StrokeDescription click2 = 
            new android.accessibilityservice.GestureDescription.StrokeDescription(
                clickPath, 100, 50, false
            );
        
        builder.addStroke(click1);
        builder.addStroke(click2);
        
        dispatchGesture(builder.build(), null, null);
    }
    
    // ============ 节点查找工具方法 ============
    
    private AccessibilityNodeInfo findNodeByDesc(AccessibilityNodeInfo root, String desc) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(desc);
        for (AccessibilityNodeInfo node : nodes) {
            if (node.getContentDescription() != null && 
                node.getContentDescription().toString().contains(desc)) {
                return node;
            }
        }
        return nodes.isEmpty() ? null : nodes.get(0);
    }
    
    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        return nodes.isEmpty() ? null : nodes.get(0);
    }
    
    private AccessibilityNodeInfo findNodeByClass(AccessibilityNodeInfo root, String className) {
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            
            if (child.getClassName() != null && 
                child.getClassName().toString().equals(className)) {
                return child;
            }
            
            AccessibilityNodeInfo result = findNodeByClass(child, className);
            if (result != null) return result;
        }
        return null;
    }
    
    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String text) {
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            
            if (child.getText() != null && 
                child.getText().toString().equals(text)) {
                return child;
            }
            
            AccessibilityNodeInfo result = findNodeByText(child, text);
            if (result != null) return result;
        }
        return null;
    }
    
    /**
     * 初始化评论列表
     */
    private void initComments() {
        commentList.add("I got the same for way less DM me 👕");
        commentList.add("This is fire 🔥 where can I get one?");
        commentList.add("I have this in stock, DM for details");
        commentList.add("Too expensive on their site lol I found cheaper");
        commentList.add("Bro I can get you the same for 60% off, DM me");
        commentList.add("Limited stock available, interested? DM me");
        commentList.add("I sell these, way cheaper than retail hit me up");
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "服务中断");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        isRunning = false;
    }
}
