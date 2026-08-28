package com.tiktokhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TikTok 全功能自动化服务 - 优化版
 * 功能：智能评论、回复子评论、养号、主页引导、反检测
 */
public class AutoService extends AccessibilityService {

    private static final String TAG = "TikTokHelper";
    private static final String[] TIKTOK_PACKAGES = {
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.ss.android.ugc.aweme"
    };
    private static final String TIKTOK_PACKAGE = TIKTOK_PACKAGES[0];
    
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
    public static boolean replyCommentEnabled = true;
    public static boolean warmUpMode = false;
    public static boolean smartReply = true;
    
    // ============ 概率设置（优化值）============
    public static int likeChance = 60;        // 60% 点赞
    public static int commentChance = 15;     // 15% 评论（降低频率防检测）
    public static int followChance = 5;       // 5% 关注
    public static int replyChance = 25;       // 25% 回复
    
    // ============ 延迟设置（防检测）============
    public static int minDelay = 3000;        // 最小延迟 3秒
    public static int maxDelay = 8000;        // 最大延迟 8秒
    public static int watchMinTime = 2000;    // 最少观看 2秒
    public static int watchMaxTime = 6000;    // 最多观看 6秒
    
    // ============ 评论内容 ============
    public static List<String> commentList = new ArrayList<>();
    public static List<String> replyList = new ArrayList<>();
    public static String profileUsername = "";  // 用于主页引导
    
    // ============ 搜索 ============
    public static String searchKeyword = "";
    public static String targetVideoUrl = "";
    
    // ============ 状态 ============
    private Random random = new Random();
    private int videoCount = 0;
    private int totalComments = 0;
    private int totalReplies = 0;
    private long lastActionTime = 0;
    private long sessionStartTime = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        sessionStartTime = System.currentTimeMillis();
        Log.d(TAG, "服务已连接");
        initDefaultComments();
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRunning) return;
        if (event.getPackageName() == null || !isTikTokPackage(event.getPackageName().toString())) return;
        
        // 反检测：随机延迟
        long now = System.currentTimeMillis();
        int delay = getRandomDelay();
        if (now - lastActionTime < delay) return;
        
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
     * 执行自动化操作 - 优化版
     */
    private void performActions(AccessibilityNodeInfo rootNode) {
        // 养号模式：只点赞和浏览
        if (warmUpMode) {
            performWarmUp(rootNode);
            return;
        }
        
        // 智能决策：根据视频内容决定操作
        int action = random.nextInt(100);
        
        // 点赞 (60%)
        if (likeEnabled && action < likeChance) {
            if (performLike(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
        
        // 关注 (5%)
        if (followEnabled && action >= likeChance && action < likeChance + followChance) {
            if (performFollow(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
        
        // 回复子评论 (25%)
        if (replyCommentEnabled && action >= likeChance + followChance && 
            action < likeChance + followChance + replyChance) {
            if (smartReply ? performSmartReply(rootNode) : performReplyComment(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
        
        // 评论 (15%)
        if (commentEnabled && action >= likeChance + followChance + replyChance) {
            if (performComment(rootNode)) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
    }
    
    /**
     * 养号模式 - 模拟真实用户
     */
    private void performWarmUp(AccessibilityNodeInfo rootNode) {
        int action = random.nextInt(100);
        
        // 30% 点赞
        if (action < 30) {
            performLike(rootNode);
        }
        // 5% 关注
        else if (action < 35) {
            performFollow(rootNode);
        }
        // 65% 只是观看
        else {
            // 模拟观看时间
            sleep(getRandomWatchTime());
        }
        
        // 随机滑动
        if (scrollEnabled && random.nextInt(100) < 70) {
            sleep(1000);
            performScroll();
        }
    }
    
    /**
     * 执行点赞
     */
    private boolean performLike(AccessibilityNodeInfo rootNode) {
        // 方法1：双击屏幕
        performDoubleClick();
        Log.d(TAG, "点赞成功");
        return true;
    }
    
    /**
     * 执行关注
     */
    private boolean performFollow(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo followBtn = findNodeByDesc(rootNode, "Follow");
        if (followBtn == null) followBtn = findNodeByDesc(rootNode, "关注");
        if (followBtn != null) {
            followBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            followBtn.recycle();
            Log.d(TAG, "关注成功");
            return true;
        }
        return false;
    }
    
    /**
     * 执行评论 - 带主页引导
     */
    private boolean performComment(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo commentBtn = findNodeByDesc(rootNode, "Comment");
        if (commentBtn == null) commentBtn = findNodeByDesc(rootNode, "评论");
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
        
        // 生成带主页引导的评论
        String comment = generateOptimizedComment();
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, comment);
        inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        inputBox.recycle();
        
        sleep(800);
        
        // 发送
        AccessibilityNodeInfo sendRoot = getRootInActiveWindow();
        if (sendRoot != null) {
            AccessibilityNodeInfo sendBtn = findNodeByText(sendRoot, "Send");
            if (sendBtn == null) sendBtn = findNodeByDesc(sendRoot, "Send");
            if (sendBtn == null) sendBtn = findNodeByText(sendRoot, "发送");
            if (sendBtn != null) {
                sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                sendBtn.recycle();
                totalComments++;
                Log.d(TAG, "评论成功 [" + totalComments + "]: " + comment);
            }
            sendRoot.recycle();
        }
        
        sleep(500);
        performGlobalAction(GLOBAL_ACTION_BACK);
        
        return true;
    }
    
    /**
     * 执行智能评论回复
     */
    private boolean performSmartReply(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo commentBtn = findNodeByDesc(rootNode, "Comment");
        if (commentBtn == null) commentBtn = findNodeByDesc(rootNode, "评论");
        if (commentBtn == null) return false;
        
        commentBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        commentBtn.recycle();
        
        sleep(2000);
        
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        // 读取评论内容
        List<String> comments = extractComments(root);
        
        if (!comments.isEmpty()) {
            // 选择一条评论
            String targetComment = comments.get(random.nextInt(comments.size()));
            
            // 生成智能回复
            String reply = generateContextualReply(targetComment);
            
            // 点击回复按钮
            AccessibilityNodeInfo replyBtn = findNodeByText(root, "Reply");
            if (replyBtn == null) replyBtn = findNodeByText(root, "回复");
            
            if (replyBtn != null) {
                replyBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                replyBtn.recycle();
                
                sleep(1000);
                
                // 输入回复
                AccessibilityNodeInfo inputRoot = getRootInActiveWindow();
                if (inputRoot != null) {
                    AccessibilityNodeInfo inputBox = findNodeByClass(inputRoot, "android.widget.EditText");
                    if (inputBox != null) {
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
                            if (sendBtn == null) sendBtn = findNodeByText(sendRoot, "发送");
                            if (sendBtn != null) {
                                sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                sendBtn.recycle();
                                totalReplies++;
                                Log.d(TAG, "回复成功 [" + totalReplies + "]: " + reply + " <- " + targetComment);
                            }
                            sendRoot.recycle();
                        }
                    }
                    inputRoot.recycle();
                }
                
                sleep(500);
                performGlobalAction(GLOBAL_ACTION_BACK);
                root.recycle();
                return true;
            }
        }
        
        performGlobalAction(GLOBAL_ACTION_BACK);
        root.recycle();
        return false;
    }
    
    /**
     * 执行普通回复
     */
    private boolean performReplyComment(AccessibilityNodeInfo rootNode) {
        return performSmartReply(rootNode);
    }
    
    /**
     * 提取评论内容
     */
    private List<String> extractComments(AccessibilityNodeInfo root) {
        List<String> comments = new ArrayList<>();
        extractCommentsRecursive(root, comments);
        return comments;
    }
    
    private void extractCommentsRecursive(AccessibilityNodeInfo node, List<String> comments) {
        if (node == null) return;
        
        if (node.getClassName() != null) {
            String className = node.getClassName().toString();
            if (className.contains("TextView") && !className.contains("Button")) {
                CharSequence text = node.getText();
                if (text != null && text.length() > 3 && text.length() < 200) {
                    comments.add(text.toString());
                }
            }
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            extractCommentsRecursive(node.getChild(i), comments);
        }
    }
    
    /**
     * 根据评论内容生成上下文相关回复
     */
    private String generateContextualReply(String comment) {
        String lower = comment.toLowerCase();
        
        // 价格相关
        if (lower.contains("price") || lower.contains("cost") || lower.contains("how much") || 
            lower.contains("多少钱") || lower.contains("价格")) {
            String[] replies = {
                "DM me for the best price! 🔥",
                "Way cheaper than retail, DM me!",
                "I got mine for cheap, check my profile"
            };
            return replies[random.nextInt(replies.length)];
        }
        
        // 购买/链接相关
        if (lower.contains("where") || lower.contains("buy") || lower.contains("get") || 
            lower.contains("link") || lower.contains("哪里买") || lower.contains("链接")) {
            String[] replies = {
                "DM me I'll share the link!",
                "Check my profile for the link 👆",
                "I have the link, follow me!"
            };
            return replies[random.nextInt(replies.length)];
        }
        
        // 好看/喜欢
        if (lower.contains("love") || lower.contains("nice") || lower.contains("cute") || 
            lower.contains("fire") || lower.contains("amazing") || lower.contains("beautiful") ||
            lower.contains("好看") || lower.contains("喜欢")) {
            String[] replies = {
                "Right?! Check my profile for more! 🔥",
                "Same taste! More on my page",
                "Thanks! Follow me for similar finds"
            };
            return replies[random.nextInt(replies.length)];
        }
        
        // 品牌/真假相关
        if (lower.contains("brand") || lower.contains("original") || lower.contains("fake") || 
            lower.contains("real") || lower.contains("正品") || lower.contains("仿牌")) {
            String[] replies = {
                "High quality stuff, DM me for details",
                "Check my profile for more info!",
                "Quality is great, follow me for finds"
            };
            return replies[random.nextInt(replies.length)];
        }
        
        // 默认回复 + 主页引导
        String[] defaultReplies = {
            "Check my profile for more! 🔥",
            "I have similar stuff, follow me!",
            "DM me for the link! Check my page too",
            "Same taste! More on my profile",
            "Thanks! Follow me for more finds",
            "Great choice! See my profile for more 👆"
        };
        
        return defaultReplies[random.nextInt(defaultReplies.length)];
    }
    
    /**
     * 生成优化评论 - 带主页引导
     */
    private String generateOptimizedComment() {
        // 如果有自定义评论，50% 使用自定义，50% 使用引导评论
        if (!commentList.isEmpty() && random.nextBoolean()) {
            return commentList.get(random.nextInt(commentList.size()));
        }
        
        // 引导评论
        String[] ctaComments = {
            "Check my profile for more! 🔥",
            "More on my page, follow me!",
            "DM me + check my profile",
            "I post finds daily, follow me!",
            "See my profile for similar stuff",
            "Follow me for more! 👆",
            "Link in my bio! Check it out",
            "Similar stuff on my page 🔥"
        };
        
        return ctaComments[random.nextInt(ctaComments.length)];
    }
    
    /**
     * 执行滑动
     */
    public void performScroll() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        
        int startX = width / 2 + random.nextInt(100) - 50;  // 随机偏移
        int startY = (int) (height * 0.7) + random.nextInt(50);
        int endY = (int) (height * 0.3) + random.nextInt(50);
        
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(startX, endY);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        int duration = 200 + random.nextInt(200);  // 随机滑动速度
        GestureDescription.StrokeDescription stroke = 
            new GestureDescription.StrokeDescription(swipePath, 0, duration, false);
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
        
        int x = width / 2 + random.nextInt(60) - 30;  // 随机偏移
        int y = height / 2 + random.nextInt(60) - 30;
        
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        
        GestureDescription.StrokeDescription click1 = 
            new GestureDescription.StrokeDescription(clickPath, 0, 50, true);
        GestureDescription.StrokeDescription click2 = 
            new GestureDescription.StrokeDescription(clickPath, 80, 50, false);
        
        builder.addStroke(click1);
        builder.addStroke(click2);
        
        dispatchGesture(builder.build(), null, null);
    }
    
    // ============ 节点查找工具 ============
    
    private AccessibilityNodeInfo findNodeByDesc(AccessibilityNodeInfo root, String desc) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(desc);
        for (AccessibilityNodeInfo node : nodes) {
            if (node.getContentDescription() != null && 
                node.getContentDescription().toString().toLowerCase().contains(desc.toLowerCase())) {
                return node;
            }
        }
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
    
    private boolean isTikTokPackage(String pkg) {
        for (String tiktokPkg : TIKTOK_PACKAGES) {
            if (tiktokPkg.equals(pkg)) return true;
        }
        return false;
    }
    
    private int getRandomDelay() {
        return minDelay + random.nextInt(maxDelay - minDelay);
    }
    
    private int getRandomWatchTime() {
        return watchMinTime + random.nextInt(watchMaxTime - watchMinTime);
    }
    
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 初始化默认评论
     */
    private void initDefaultComments() {
        commentList.add("I got the same for way less DM me");
        commentList.add("This is fire! Where can I get one?");
        commentList.add("I have this in stock, DM for details");
        commentList.add("Too expensive on their site, I found cheaper");
        commentList.add("Bro I can get you the same for 60% off, DM me");
        commentList.add("Check my profile for more! 🔥");
        commentList.add("Link in my bio! Follow me for finds");
        
        replyList.add("Thanks!");
        replyList.add("Exactly!");
        replyList.add("Same here!");
        replyList.add("Great taste!");
        replyList.add("Love this!");
        replyList.add("Check my profile! 🔥");
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
                    AccessibilityNodeInfo firstVideo = findFirstVideo(resultRoot);
                    if (firstVideo != null) {
                        firstVideo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        firstVideo.recycle();
                        sleep(2000);
                        
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
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(url));
                intent.setPackage(TIKTOK_PACKAGE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                
                sleep(3000);
                
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    performComment(root);
                    root.recycle();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "链接评论失败: " + e.getMessage());
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                } catch (Exception ex) {
                    Log.e(TAG, "浏览器打开也失败: " + ex.getMessage());
                }
            }
        }).start();
    }
    
    private AccessibilityNodeInfo findFirstVideo(AccessibilityNodeInfo root) {
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
    
    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        return nodes.isEmpty() ? null : nodes.get(0);
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        long sessionTime = (System.currentTimeMillis() - sessionStartTime) / 1000;
        return "视频: " + videoCount + " | 评论: " + totalComments + " | 回复: " + totalReplies + " | 时长: " + sessionTime + "s";
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
