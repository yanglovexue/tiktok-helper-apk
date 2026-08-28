package com.tiktokhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
    public static volatile boolean isRunning = false;
    public static volatile boolean likeEnabled = true;
    public static volatile boolean commentEnabled = true;
    public static volatile boolean scrollEnabled = true;
    public static volatile boolean followEnabled = false;
    public static volatile boolean replyCommentEnabled = true;
    public static volatile boolean warmUpMode = false;
    public static volatile boolean smartReply = true;
    
    // ============ 概率设置（优化值）============
    public static volatile int likeChance = 60;        // 60% 点赞
    public static volatile int commentChance = 15;     // 15% 评论
    public static volatile int followChance = 5;       // 5% 关注
    public static volatile int replyChance = 25;       // 25% 回复
    
    // ============ 养号模式概率 ============
    public static volatile int warmUpLikeChance = 45;     // 养号模式：45% 点赞（真人只赞约一半视频）
    public static volatile int warmUpFollowChance = 8;    // 养号模式：8% 关注（养号期逐步关注同行）
    
    // ============ 延迟设置（防检测）============
    public static volatile int minDelay = 4000;        // 最小延迟 4秒
    public static volatile int maxDelay = 9000;        // 最大延迟 9秒
    public static volatile int watchMinTime = 5000;    // 最少观看 5秒（避免秒赞检测）
    public static volatile int watchMaxTime = 12000;   // 最多观看 12秒（真人完整观看）
    
    // ============ 评论内容 ============
    public static final List<String> commentList = new CopyOnWriteArrayList<>();
    public static final List<String> replyList = new CopyOnWriteArrayList<>();
    
    // ============ 搜索 ============
    public static String searchKeyword = "";
    public static String targetVideoUrl = "";
    
    // ============ 状态 ============
    private Random random = new Random();
    private volatile int videoCount = 0;
    private volatile int totalComments = 0;
    private volatile int totalReplies = 0;
    private volatile long lastActionTime = 0;
    private long sessionStartTime = 0;
    // 后台任务执行器：将耗时操作移出主线程，避免 ANR。有界队列防积压，多余任务直接丢弃
    private final ExecutorService actionExecutor = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<Runnable>(3),
        new ThreadPoolExecutor.DiscardPolicy());
    
    // ============ 评论内容过滤（排除非评论节点）============
    private static final List<String> COMMENT_SKIP_TEXTS = Arrays.asList(
        "Reply", "回复", "Send", "发送", "Comment", "评论", "Follow", "关注",
        "Share", "分享", "Like", "点赞", "Cancel", "取消", "View all", "查看全部",
        "Follow back", "回关", "Message", "私信"
    );
    
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
        "\\d+\\s*[dhms](\\s+ago)?|\\d+\\s*[天小时分]前|刚刚|\\bnow\\b|\\d{1,2}:\\d{2}",
        Pattern.CASE_INSENSITIVE);
    
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
        if (actionExecutor.isShutdown()) {
            rootNode.recycle();
            return;
        }
        
        // 安全闸：提交前检查 TikTok 是否在前台
        if (!isTikTokRoot(rootNode)) {
            rootNode.recycle();
            return;
        }
        
        // 耗时操作提交到后台单线程执行器执行，避免阻塞主线程导致 ANR
        actionExecutor.submit(() -> {
            try {
                performActions(rootNode);
            } catch (Exception e) {
                Log.e(TAG, "操作失败: " + e.getMessage());
            } finally {
                rootNode.recycle();
            }
        });
    }
    
    /**
     * 执行自动化操作 - 优化版
     */
    private void performActions(AccessibilityNodeInfo rootNode) {
        // 安全闸：TikTok 不在前台时立即返回，防止队列积压任务在非 TikTok 界面执行
        if (!isTikTokForeground()) {
            Log.d(TAG, "跳过操作：TikTok 不在前台");
            return;
        }
        
        // 养号会话时长上限（45分钟）：模拟真人单次使用时长，防止长时间挂机触发风控
        if (warmUpMode && sessionStartTime > 0
                && System.currentTimeMillis() - sessionStartTime > 45 * 60 * 1000) {
            Log.d(TAG, "养号会话已达 45 分钟，自动停止");
            isRunning = false;
            return;
        }
        
        // 养号模式：只点赞和浏览
        if (warmUpMode) {
            performWarmUp(rootNode);
            return;
        }
        
        // 智能决策：归一化加权随机选择
        int likeWeight = likeEnabled ? likeChance : 0;
        int followWeight = followEnabled ? followChance : 0;
        int replyWeight = replyCommentEnabled ? replyChance : 0;
        int commentWeight = commentEnabled ? commentChance : 0;
        int total = likeWeight + followWeight + replyWeight + commentWeight;
        if (total == 0) return;
        
        // 在 [0, total) 内取随机数，按权重依次扣减，命中即执行
        int action = random.nextInt(total);
        
        // 点赞 (权重 = likeChance)
        if (action < likeWeight) {
            if (performLike(rootNode)) {
                lastActionTime = System.currentTimeMillis();
            }
            return;
        }
        action -= likeWeight;
        
        // 关注 (权重 = followChance)
        if (action < followWeight) {
            if (performFollow(rootNode)) {
                lastActionTime = System.currentTimeMillis();
            }
            return;
        }
        action -= followWeight;
        
        // 回复子评论 (权重 = replyChance)
        if (action < replyWeight) {
            boolean ok = smartReply ? performSmartReply(rootNode) : performReplyComment(rootNode);
            if (ok) {
                lastActionTime = System.currentTimeMillis();
            }
            return;
        }
        action -= replyWeight;
        
        // 评论 (权重 = commentChance)
        if (action < commentWeight) {
            if (performComment(rootNode)) {
                lastActionTime = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * 养号模式 - 模拟真实用户
     */
    private void performWarmUp(AccessibilityNodeInfo rootNode) {
        int action = random.nextInt(100);
        
        // 使用养号模式概率：只点赞、关注或观看，不评论、不回复
        if (action < warmUpLikeChance) {
            // 45% 点赞：点赞后停留数秒再滑走，模拟真人看视频节奏
            performLike(rootNode);
            sleep(5000 + random.nextInt(6000));  // 停留 5-11 秒
        } else if (action < warmUpLikeChance + warmUpFollowChance) {
            // 8% 关注：关注后同样停留
            performFollow(rootNode);
            sleep(5000 + random.nextInt(6000));  // 停留 5-11 秒
        } else {
            // 47% 只是观看（完整看完，模拟真人）
            sleep(getRandomWatchTime());
        }
        
        // 看完后滑动：75% 滑走，25% 停留不动（真人不会每看一个就滑）
        if (scrollEnabled && random.nextInt(100) < 75) {
            sleep(1000);
            performScroll();
        }
    }
    
    /**
     * 执行点赞
     */
    private boolean performLike(AccessibilityNodeInfo rootNode) {
        // 优先点击点赞按钮节点，避免双击屏幕误触弹层菜单
        AccessibilityNodeInfo likeBtn = null;
        // 先找可点击的点赞按钮（TikTok 英文/中文 content-desc）
        AccessibilityNodeInfo likeEn = findNodeByDesc(rootNode, "Like");
        if (likeEn != null && likeEn.isClickable()) {
            likeBtn = likeEn;
        } else if (likeEn != null) {
            likeEn.recycle();
            likeEn = null;
        }
        if (likeBtn == null) {
            AccessibilityNodeInfo likeZh = findNodeByDesc(rootNode, "点赞");
            if (likeZh != null && likeZh.isClickable()) {
                likeBtn = likeZh;
            } else if (likeZh != null) {
                likeZh.recycle();
                likeZh = null;
            }
        }
        if (likeBtn != null) {
            likeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            likeBtn.recycle();
            Log.d(TAG, "点赞成功");
            return true;
        }
        
        // 找不到点赞按钮时不做任何手势操作（避免双击屏幕误触菜单/乱点）
        Log.d(TAG, "跳过点赞：未找到点赞按钮节点");
        return false;
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
        
        try {
            AccessibilityNodeInfo inputBox = findNodeByClass(newRoot, "android.widget.EditText");
            if (inputBox == null) return false;
            
            // 生成带主页引导的评论
            String comment = generateOptimizedComment();
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, comment);
            inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            inputBox.recycle();
            
            sleep(800);
            
            // 发送
            AccessibilityNodeInfo sendRoot = getRootInActiveWindow();
            boolean sent = false;
            if (sendRoot != null) {
                AccessibilityNodeInfo sendBtn = findNodeByText(sendRoot, "Send");
                if (sendBtn == null) sendBtn = findNodeByDesc(sendRoot, "Send");
                if (sendBtn == null) sendBtn = findNodeByText(sendRoot, "发送");
                if (sendBtn != null) {
                    sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    sendBtn.recycle();
                    totalComments++;
                    Log.d(TAG, "评论成功 [" + totalComments + "]: " + comment);
                    sent = true;
                }
                sendRoot.recycle();
            }
            
            sleep(500);
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            return sent;
        } finally {
            newRoot.recycle();
        }
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
        
        try {
            // 读取评论内容
            List<String> comments = extractComments(root);
            
            if (comments.isEmpty()) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                return false;
            }
            
            // 选择一条评论
            String targetComment = comments.get(random.nextInt(comments.size()));
            
            // 生成智能回复
            String reply = generateContextualReply(targetComment);
            
            boolean ok = performReplyFlow(root, reply);
            if (ok) {
                Log.d(TAG, "智能回复: " + reply + " <- " + targetComment);
            }
            sleep(500);
            performGlobalAction(GLOBAL_ACTION_BACK);
            return ok;
        } finally {
            root.recycle();
        }
    }
    
    /**
     * 执行普通回复 - 使用用户配置的回复列表
     */
    private boolean performReplyComment(AccessibilityNodeInfo rootNode) {
        AccessibilityNodeInfo commentBtn = findNodeByDesc(rootNode, "Comment");
        if (commentBtn == null) commentBtn = findNodeByDesc(rootNode, "评论");
        if (commentBtn == null) return false;
        
        commentBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        commentBtn.recycle();
        
        sleep(2000);
        
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        try {
            // 读取评论内容（用于兜底生成回复）
            List<String> comments = extractComments(root);
            
            // 优先使用用户配置的回复列表，为空时根据评论内容智能生成
            String reply;
            if (!replyList.isEmpty()) {
                reply = replyList.get(random.nextInt(replyList.size()));
            } else if (!comments.isEmpty()) {
                reply = generateContextualReply(comments.get(random.nextInt(comments.size())));
            } else {
                reply = "Check my profile for more! 🔥";
            }
            
            boolean ok = performReplyFlow(root, reply);
            if (ok) {
                Log.d(TAG, "回复成功: " + reply);
            }
            sleep(500);
            performGlobalAction(GLOBAL_ACTION_BACK);
            return ok;
        } finally {
            root.recycle();
        }
    }
    
    /**
     * 回复核心流程：点击回复按钮、输入回复内容并发送
     */
    private boolean performReplyFlow(AccessibilityNodeInfo root, String reply) {
        // 点击回复按钮
        AccessibilityNodeInfo replyBtn = findNodeByText(root, "Reply");
        if (replyBtn == null) replyBtn = findNodeByText(root, "回复");
        
        if (replyBtn == null) return false;
        
        replyBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        replyBtn.recycle();
        
        sleep(1000);
        
        // 输入回复
        AccessibilityNodeInfo inputRoot = getRootInActiveWindow();
        if (inputRoot == null) return false;
        
        try {
            AccessibilityNodeInfo inputBox = findNodeByClass(inputRoot, "android.widget.EditText");
            if (inputBox == null) return false;
            
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply);
            inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            inputBox.recycle();
            
            sleep(800);
            
            // 发送
            AccessibilityNodeInfo sendRoot = getRootInActiveWindow();
            if (sendRoot != null) {
                try {
                    AccessibilityNodeInfo sendBtn = findNodeByText(sendRoot, "Send");
                    if (sendBtn == null) sendBtn = findNodeByDesc(sendRoot, "Send");
                    if (sendBtn == null) sendBtn = findNodeByText(sendRoot, "发送");
                    if (sendBtn != null) {
                        sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        sendBtn.recycle();
                        totalReplies++;
                        Log.d(TAG, "回复成功 [" + totalReplies + "]: " + reply);
                        return true;
                    }
                } finally {
                    sendRoot.recycle();
                }
            }
            return false;
        } finally {
            inputRoot.recycle();
        }
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
                if (text != null) {
                    String t = text.toString().trim();
                    if (t.length() > 3 && t.length() < 200 && isLikelyComment(t)) {
                        comments.add(t);
                    }
                }
            }
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            extractCommentsRecursive(child, comments);
            child.recycle();
        }
    }
    
    /**
     * 判断是否为真正的评论内容（排除按钮文字、用户名、时间戳等非评论节点）
     */
    private boolean isLikelyComment(String text) {
        if (COMMENT_SKIP_TEXTS.contains(text)) return false;
        if (text.startsWith("@")) return false;                    // 用户名
        if (TIMESTAMP_PATTERN.matcher(text).find()) return false;  // 时间戳
        return true;
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
     * 执行滑动 - 仅当 TikTok 在前台时才执行，避免在其他应用界面误操作
     */
    public void performScroll() {
        if (!isTikTokForeground()) {
            Log.d(TAG, "跳过滚动：TikTok 不在前台");
            return;
        }
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
    
    // ============ 节点查找工具 ============
    
    private AccessibilityNodeInfo findNodeByDesc(AccessibilityNodeInfo root, String desc) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(desc);
        AccessibilityNodeInfo result = null;
        try {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.getContentDescription() != null && 
                    node.getContentDescription().toString().toLowerCase().contains(desc.toLowerCase())) {
                    result = node;
                    break;
                }
            }
            if (result == null && !nodes.isEmpty()) {
                result = nodes.get(0);
            }
            // 回收列表中除返回节点外的所有节点
            for (AccessibilityNodeInfo node : nodes) {
                if (node != result) {
                    node.recycle();
                }
            }
            return result;
        } finally {
            nodes.clear();
        }
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
            
            // 递归未命中时回收中间子节点
            child.recycle();
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
            
            // 递归未命中时回收中间子节点
            child.recycle();
        }
        return null;
    }
    
    /**
     * 判断根节点是否属于 TikTok 应用（安全检查）
     */
    private boolean isTikTokRoot(AccessibilityNodeInfo root) {
        CharSequence pkg = root.getPackageName();
        return pkg != null && isTikTokPackage(pkg.toString());
    }
    
    private boolean isTikTokPackage(String pkg) {
        for (String tiktokPkg : TIKTOK_PACKAGES) {
            if (tiktokPkg.equals(pkg)) return true;
        }
        return false;
    }
    
    /**
     * 检查 TikTok 是否在前台（当前活动窗口的包名是否为 TikTok）
     * 用于手势操作安全闸，防止在其他应用界面乱点乱滑
     */
    private boolean isTikTokForeground() {
        return isTikTokForegroundPublic();
    }
    
    /**
     * 公开版本，供 MainActivity 等外部调用
     */
    public boolean isTikTokForegroundPublic() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            try {
                return isTikTokRoot(root);
            } finally {
                root.recycle();
            }
        } catch (Exception e) {
            return false;
        }
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
        // 先清空，避免重复初始化时累积
        commentList.clear();
        replyList.clear();
        
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
                try {
                    // 安全检查：仅处理 TikTok 页面
                    if (!isTikTokRoot(root)) return;
                    
                    AccessibilityNodeInfo searchBtn = findNodeByDesc(root, "Search");
                    if (searchBtn == null) {
                        searchBtn = findNodeById(root, TIKTOK_PACKAGE + ":id/search");
                    }
                    if (searchBtn == null) return;
                    
                    searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    searchBtn.recycle();
                } finally {
                    root.recycle();
                }
                
                sleep(1000);
                
                // 输入关键词
                AccessibilityNodeInfo inputRoot = getRootInActiveWindow();
                if (inputRoot == null) return;
                try {
                    // 安全检查：仅处理 TikTok 页面
                    if (!isTikTokRoot(inputRoot)) return;
                    
                    AccessibilityNodeInfo searchInput = findNodeByClass(inputRoot, "android.widget.EditText");
                    if (searchInput == null) return;
                    
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword);
                    searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    searchInput.recycle();
                } finally {
                    inputRoot.recycle();
                }
                
                sleep(500);
                
                // 点击搜索
                AccessibilityNodeInfo searchActionRoot = getRootInActiveWindow();
                if (searchActionRoot != null) {
                    try {
                        // 安全检查：仅处理 TikTok 页面
                        if (!isTikTokRoot(searchActionRoot)) return;
                        
                        AccessibilityNodeInfo searchAction = findNodeByText(searchActionRoot, "Search");
                        if (searchAction == null) searchAction = findNodeByDesc(searchActionRoot, "Search");
                        if (searchAction != null) {
                            searchAction.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            searchAction.recycle();
                        }
                    } finally {
                        searchActionRoot.recycle();
                    }
                }
                
                sleep(2000);
                
                // 点击第一个搜索结果
                AccessibilityNodeInfo resultRoot = getRootInActiveWindow();
                if (resultRoot != null) {
                    try {
                        // 安全检查：仅处理 TikTok 页面
                        if (!isTikTokRoot(resultRoot)) return;
                        
                        AccessibilityNodeInfo firstVideo = findFirstVideo(resultRoot);
                        if (firstVideo != null) {
                            firstVideo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            firstVideo.recycle();
                            sleep(2000);
                            
                            AccessibilityNodeInfo videoRoot = getRootInActiveWindow();
                            if (videoRoot != null) {
                                try {
                                    // 安全检查：仅处理 TikTok 页面
                                    if (isTikTokRoot(videoRoot)) {
                                        performComment(videoRoot);
                                    }
                                } finally {
                                    videoRoot.recycle();
                                }
                            }
                        }
                    } finally {
                        resultRoot.recycle();
                    }
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
        
        // 校验链接必须是 http(s)
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Log.e(TAG, "无效链接: " + url);
            return;
        }
        
        new Thread(() -> {
            try {
                // 仅当 TikTok 未安装（ActivityNotFoundException）时才回退到浏览器
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse(url));
                    intent.setPackage(TIKTOK_PACKAGE);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "TikTok 未安装，回退到浏览器: " + e.getMessage());
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(browserIntent);
                    } catch (Exception ex) {
                        Log.e(TAG, "浏览器打开也失败: " + ex.getMessage());
                    }
                }
                
                sleep(3000);
                
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    try {
                        // 安全检查：仅处理 TikTok 页面
                        if (isTikTokRoot(root)) {
                            performComment(root);
                        }
                    } finally {
                        root.recycle();
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "链接评论失败: " + e.getMessage());
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
            
            // 递归未命中时回收中间子节点
            child.recycle();
        }
        return null;
    }
    
    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        AccessibilityNodeInfo result = nodes.isEmpty() ? null : nodes.get(0);
        // 回收列表中除返回节点外的所有节点
        for (AccessibilityNodeInfo node : nodes) {
            if (node != result) {
                node.recycle();
            }
        }
        nodes.clear();
        return result;
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        long sessionTime = (System.currentTimeMillis() - sessionStartTime) / 1000;
        return "视频: " + videoCount + " | 评论: " + totalComments + " | 回复: " + totalReplies + " | 时长: " + sessionTime + "s";
    }
    
    // ============ 统计访问器（供悬浮窗读取）============
    public int getVideoCount() { return videoCount; }
    public int getCommentCount() { return totalComments; }
    public int getReplyCount() { return totalReplies; }
    
    /**
     * 会话已运行秒数
     */
    public long getSessionSeconds() {
        if (sessionStartTime == 0) return 0;
        return (System.currentTimeMillis() - sessionStartTime) / 1000;
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "服务中断");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        actionExecutor.shutdown();
        instance = null;
        isRunning = false;
    }
}
