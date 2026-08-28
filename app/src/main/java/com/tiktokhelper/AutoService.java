package com.tiktokhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TikTok 全功能自动化服务
 * 功能：关键词搜索评论、链接评论、子评论回复、自动养号
 */
public class AutoService extends AccessibilityService {

    private static final String TAG = "TikTokHelper";
    private static final String TIKTOK_PACKAGE = "com.zhiliaoapp.musically";
    
    // 单例
    private static AutoService instance;
    public static AutoService getInstance() {
        return instance;
    }
    
    // ============ 控制开关 ============
    public static boolean isRunning = false;
    public static boolean likeEnabled = true;
    public static boolean commentEnabled = true;
    public static boolean scrollEnabled = true;
    public static boolean followEnabled = false;
    public static boolean replyCommentEnabled = false;
    public static boolean warmUpMode = false;
    
    // ============ 概率设置 ============
    public static int likeChance = 70;
    public static int commentChance = 20;
    public static int followChance = 10;
    public static int replyChance = 30;
    
    // ============ 评论内容 ============
    public static List<String> commentList = new ArrayList<>();
    public static List<String> replyList = new ArrayList<>();
    
    // ============ 搜索关键词 ============
    public static String searchKeyword = "";
    public static String targetVideoUrl = "";
    
    // ============ 状态 ============
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
        if (event.getPackageName() == null || !event.getPackageName().equals(TIKTOK_PACKAGE)) return;
        
        long now = System.currentTimeMillis();
        if (now - lastActionTime < 2000) return;
        
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        
        try {
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
        // 养号模式：只点赞和浏览，不评论
        if (warmUpMode) {
            performWarmUp(rootNode);
            return;
        }
        
        // 正常模式
        if (likeEnabled && random.nextInt(100) < likeChance) {
            if (performLike(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
        
        if (followEnabled && random.nextInt(100) < followChance) {
            if (performFollow(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
        
        if (commentEnabled && random.nextInt(100) < commentChance) {
            if (performComment(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
        
        if (replyCommentEnabled && random.nextInt(100) < replyChance) {
            if (performReplyComment(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
    }
    
    /**
     * 养号模式：模拟真实用户浏览
     */
    private void performWarmUp(AccessibilityNodeInfo rootNode) {
        // 随机点赞
        if (random.nextInt(100) < 30) {
            performLike(rootNode);
        }
        
        // 随机关注
        if (random.nextInt(100) < 5) {
            performFollow(rootNode);
        }
        
        // 正常滑动
        if (scrollEnabled) {
            performScroll();
        }
    }
    
    /**
     * 执行点赞
     */
    private boolean performLike(AccessibilityNodeInfo rootNode) {
        performDoubleClick();
        Log.d(TAG, "执行双击点赞");
        return true;
    }
    
    /**
     * 执行关注
     */
    private boolean performFollow(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo followBtn = findNodeByText(rootNode, "Follow");
        if (followBtn == null) {
            followBtn = findNodeByDesc(rootNode, "Follow");
        }
        if (followBtn != null) {
            followBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            followBtn.recycle();
            Log.d(TAG, "执行关注");
            return true;
        }
        return false;
    }
    
    /**
     * 执行评论
     */
    private boolean performComment(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo commentBtn = findNodeByDesc(rootNode, "Comment");
        if (commentBtn == null) {
            commentBtn = findNodeById(rootNode, TIKTOK_PACKAGE + ":id/comment");
        }
        if (commentBtn == null) return false;
        
        commentBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        commentBtn.recycle();
        
        sleep(1500);
        
        AccessibilityNodeInfo newRoot = getRootInActiveWindow();
        if (newRoot == null) return false;
        
        AccessibilityNodeInfo inputBox = findNodeByClass(newRoot, "android.widget.EditText");
        if (inputBox == null) {
            newRoot.recycle();
            return false;
        }
        
        String comment = commentList.get(random.nextInt(commentList.size()));
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, comment);
        inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        inputBox.recycle();
        
        sleep(800);
        
        AccessibilityNodeInfo sendRoot = getRootInActiveWindow();
        if (sendRoot != null) {
            AccessibilityNodeInfo sendBtn = findNodeByText(sendRoot, "Send");
            if (sendBtn == null) sendBtn = findNodeByDesc(sendRoot, "Send");
            if (sendBtn != null) {
                sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                sendBtn.recycle();
            }
            sendRoot.recycle();
        }
        
        sleep(500);
        performGlobalAction(GLOBAL_ACTION_BACK);
        
        Log.d(TAG, "评论: " + comment);
        return true;
    }
    
    /**
     * 执行子评论回复
     */
    private boolean performReplyComment(AccessibilityNodeInfo rootNode) {
        // 先打开评论
        AccessibilityNodeInfo commentBtn = findNodeByDesc(rootNode, "Comment");
        if (commentBtn == null) {
            commentBtn = findNodeById(rootNode, TIKTOK_PACKAGE + ":id/comment");
        }
        if (commentBtn == null) return false;
        
        commentBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        commentBtn.recycle();
        
        sleep(1500);
        
        // 查找评论列表中的回复按钮
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        // 查找 "Reply" 或 "回复" 按钮
        AccessibilityNodeInfo replyBtn = findNodeByText(root, "Reply");
        if (replyBtn == null) {
            replyBtn = findNodeByText(root, "回复");
        }
        if (replyBtn == null) {
            // 尝试查找包含 "reply" 的节点
            replyBtn = findNodeContainingText(root, "reply");
        }
        
        if (replyBtn == null) {
            root.recycle();
            performGlobalAction(GLOBAL_ACTION_BACK);
            return false;
        }
        
        // 点击回复按钮
        replyBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        replyBtn.recycle();
        
        sleep(1000);
        
        // 输入回复内容
        AccessibilityNodeInfo inputRoot = getRootInActiveWindow();
        if (inputRoot == null) return false;
        
        AccessibilityNodeInfo inputBox = findNodeByClass(inputRoot, "android.widget.EditText");
        if (inputBox == null) {
            inputRoot.recycle();
            return false;
        }
        
        String reply = replyList.isEmpty() ? "Nice!" : replyList.get(random.nextInt(replyList.size()));
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply);
        inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        inputBox.recycle();
        
        sleep(800);
        
        // 发送
        AccessibilityNodeInfo sendRoot = getRootInActiveWindow();
        if (sendRoot != null) {
            AccessibilityNodeInfo sendBtn = findNodeByText(sendRoot, "Send");
            if (sendBtn == null) sendBtn = findNodeByDesc(sendRoot, "Send");
            if (sendBtn != null) {
                sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                sendBtn.recycle();
            }
            sendRoot.recycle();
        }
        
        sleep(500);
        performGlobalAction(GLOBAL_ACTION_BACK);
        sleep(300);
        performGlobalAction(GLOBAL_ACTION_BACK);
        
        Log.d(TAG, "回复子评论: " + reply);
        return true;
    }
    
    /**
     * 关键词搜索并评论
     */
    public void searchAndComment(String keyword) {
        if (!isRunning) return;
        
        new Thread(() -> {
            try {
                // 点击搜索按钮
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;
                
                AccessibilityNodeInfo searchBtn = findNodeByDesc(root, "Search");
                if (searchBtn == null) {
                    searchBtn = findNodeById(root, TIKTOK_PACKAGE + ":id/search");
                }
                if (searchBtn == null) {
                    root.recycle();
                    return;
                }
                
                searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                searchBtn.recycle();
                
                sleep(1000);
                
                // 输入关键词
                AccessibilityNodeInfo inputRoot = getRootInActiveWindow();
                if (inputRoot == null) return;
                
                AccessibilityNodeInfo searchInput = findNodeByClass(inputRoot, "android.widget.EditText");
                if (searchInput == null) {
                    inputRoot.recycle();
                    return;
                }
                
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword);
                searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                searchInput.recycle();
                
                sleep(500);
                
                // 点击搜索
                AccessibilityNodeInfo searchActionRoot = getRootInActiveWindow();
                if (searchActionRoot != null) {
                    AccessibilityNodeInfo searchAction = findNodeByText(searchActionRoot, "Search");
                    if (searchAction == null) searchAction = findNodeByDesc(searchActionRoot, "Search");
                    if (searchAction != null) {
                        searchAction.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        searchAction.recycle();
                    }
                    searchActionRoot.recycle();
                }
                
                sleep(2000);
                
                // 点击第一个搜索结果
                AccessibilityNodeInfo resultRoot = getRootInActiveWindow();
                if (resultRoot != null) {
                    // 查找视频列表并点击第一个
                    AccessibilityNodeInfo firstVideo = findFirstVideo(resultRoot);
                    if (firstVideo != null) {
                        firstVideo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        firstVideo.recycle();
                        sleep(2000);
                        
                        // 执行评论
                        AccessibilityNodeInfo videoRoot = getRootInActiveWindow();
                        if (videoRoot != null) {
                            performComment(videoRoot);
                            videoRoot.recycle();
                        }
                    }
                    resultRoot.recycle();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "搜索评论失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 通过链接打开视频并评论
     */
    public void openUrlAndComment(String url) {
        if (!isRunning) return;
        
        new Thread(() -> {
            try {
                // 使用 Intent 打开链接
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(url));
                intent.setPackage(TIKTOK_PACKAGE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                
                sleep(3000);
                
                // 执行评论
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    performComment(root);
                    root.recycle();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "链接评论失败: " + e.getMessage());
            }
        }).start();
    }
    
    // ============ 工具方法 ============
    
    /**
     * 执行滑动
     */
    public void performScroll() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        
        int startX = width / 2;
        int startY = (int) (height * 0.7);
        int endY = (int) (height * 0.3);
        
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(startX, endY);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription.StrokeDescription stroke = 
            new GestureDescription.StrokeDescription(swipePath, 0, 300, false);
        builder.addStroke(stroke);
        
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                videoCount++;
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
        
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        
        GestureDescription.StrokeDescription click1 = 
            new GestureDescription.StrokeDescription(clickPath, 0, 50, true);
        GestureDescription.StrokeDescription click2 = 
            new GestureDescription.StrokeDescription(clickPath, 100, 50, false);
        
        builder.addStroke(click1);
        builder.addStroke(click2);
        
        dispatchGesture(builder.build(), null, null);
    }
    
    // ============ 节点查找工具 ============
    
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
                child.getText().toString().equalsIgnoreCase(text)) {
                return child;
            }
            
            AccessibilityNodeInfo result = findNodeByText(child, text);
            if (result != null) return result;
        }
        return null;
    }
    
    private AccessibilityNodeInfo findNodeContainingText(AccessibilityNodeInfo root, String text) {
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            
            if (child.getText() != null && 
                child.getText().toString().toLowerCase().contains(text.toLowerCase())) {
                return child;
            }
            
            AccessibilityNodeInfo result = findNodeContainingText(child, text);
            if (result != null) return result;
        }
        return null;
    }
    
    private AccessibilityNodeInfo findFirstVideo(AccessibilityNodeInfo root) {
        // 查找可点击的视频节点
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            
            if (child.isClickable() && child.isVisibleToUser()) {
                return child;
            }
            
            AccessibilityNodeInfo result = findFirstVideo(child);
            if (result != null) return result;
        }
        return null;
    }
    
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 初始化评论列表
     */
    private void initComments() {
        commentList.add("I got the same for way less DM me");
        commentList.add("This is fire! Where can I get one?");
        commentList.add("I have this in stock, DM for details");
        commentList.add("Too expensive on their site, I found cheaper");
        commentList.add("Bro I can get you the same for 60% off, DM me");
        commentList.add("Limited stock available, interested? DM me");
        commentList.add("I sell these, way cheaper than retail hit me up");
        
        replyList.add("Thanks!");
        commentList.add("Exactly!");
        commentList.add("Same here!");
        commentList.add("Great taste!");
        commentList.add("Love this!");
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
