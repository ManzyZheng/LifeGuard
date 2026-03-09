package com.example.firstaid.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.firstaid.MainActivity;
import com.example.firstaid.R;
import com.example.firstaid.logic.RiskPopupCoordinator;
import com.example.firstaid.logic.SensorFusionManager;
import com.example.firstaid.model.RiskLevel;
import com.example.firstaid.ui.EmergencyModeActivity;
import com.example.firstaid.ui.LowRiskPopupActivity;
import com.example.firstaid.ui.MediumRiskActivity;

public class BackgroundDetectionService extends Service {

    public static final String ACTION_START = "com.example.firstaid.action.START_BACKGROUND_DETECTION";
    public static final String ACTION_STOP = "com.example.firstaid.action.STOP_BACKGROUND_DETECTION";
    public static final String ACTION_USER_CONFIRMED_SAFE = "com.example.firstaid.action.USER_CONFIRMED_SAFE";
    public static final String ACTION_RISK_UPDATE = "com.example.firstaid.action.RISK_UPDATE";
    public static final String EXTRA_RISK_LEVEL = "extra_risk_level";
    public static final String EXTRA_RISK_SCORE = "extra_risk_score";
    public static final String EXTRA_MONITOR_STATE = "extra_monitor_state";
    public static final String EXTRA_SUGGESTION = "extra_suggestion";

    private static final String CHANNEL_MONITOR = "firstaid_monitor";
    private static final String CHANNEL_ALERT = "firstaid_alert";
    private static final int ID_FOREGROUND = 2001;
    private static final long MANUAL_SAFE_SUPPRESS_MS = 20_000L;

    private SensorFusionManager fusionManager;
    private long lastLowPopupMs = 0L;
    private long lastNavigationMs = 0L;
    private long suppressAutoEscalationUntilMs = 0L;
    private RiskLevel lastNavigatedRisk = RiskLevel.SAFE;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelsIfNeeded();
        fusionManager = new SensorFusionManager(this, this::onRiskUpdated);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_USER_CONFIRMED_SAFE.equals(action)) {
            if (fusionManager != null) {
                fusionManager.resetToSafe();
            }
            suppressAutoEscalationUntilMs = System.currentTimeMillis() + MANUAL_SAFE_SUPPRESS_MS;
            broadcastRisk(RiskLevel.SAFE, 100, "用户已确认安全", "已进入安全恢复冷却期");
            return START_STICKY;
        }
        try {
            startForeground(ID_FOREGROUND, buildForegroundNotification());
        } catch (RuntimeException e) {
            // Avoid app crash loop when device/ROM rejects foreground notification start.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (fusionManager != null) {
            fusionManager.start();
        }
        // Immediately publish a baseline state so UI does not stay at default text.
        broadcastRisk(RiskLevel.SAFE, 100, "后台传感器服务已启动", "状态稳定，继续监测。");
        return START_STICKY;
    }

    private void onRiskUpdated(RiskLevel level, int score, String monitorState, String suggestion) {
        long now = System.currentTimeMillis();
        if (now < suppressAutoEscalationUntilMs) {
            broadcastRisk(RiskLevel.SAFE, 100, monitorState, "已确认安全，冷却保护中");
            return;
        }
        broadcastRisk(level, score, monitorState, suggestion);
        if (level == RiskLevel.LOW) {
            if (now - lastLowPopupMs > 25_000L) {
                if (RiskPopupCoordinator.tryRequest(RiskLevel.LOW)) {
                    lastLowPopupMs = now;
                    launchLowRiskPopup();
                }
            }
            return;
        }
        if (level == RiskLevel.MEDIUM || level == RiskLevel.HIGH) {
            boolean sameRiskCooldown = (level == lastNavigatedRisk && now - lastNavigationMs < 12_000L);
            if (sameRiskCooldown) {
                return;
            }
            if (!RiskPopupCoordinator.tryRequest(level)) {
                return;
            }
            lastNavigationMs = now;
            lastNavigatedRisk = level;
            if (level == RiskLevel.HIGH) {
                launchRiskScreen(EmergencyModeActivity.class, "高风险急救模式");
            } else {
                launchRiskScreen(MediumRiskActivity.class, "中风险干预模式");
            }
        }
    }

    private void broadcastRisk(RiskLevel level, int score, String monitorState, String suggestion) {
        Intent intent = new Intent(ACTION_RISK_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_RISK_LEVEL, level.name());
        intent.putExtra(EXTRA_RISK_SCORE, score);
        intent.putExtra(EXTRA_MONITOR_STATE, monitorState);
        intent.putExtra(EXTRA_SUGGESTION, suggestion);
        sendBroadcast(intent);
    }

    private void launchLowRiskPopup() {
        Intent intent = new Intent(this, LowRiskPopupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            RiskPopupCoordinator.release(RiskLevel.LOW);
            // Fall back to alert notification if direct start is restricted by system.
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                1201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("低风险提醒")
                .setContentText("检测到轻微异常，点击查看并确认状态。")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build();
        safeNotify(3201, notification);
    }

    private void launchRiskScreen(Class<?> target, String title) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                this,
                target == EmergencyModeActivity.class ? 2202 : 2201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText("检测到风险事件，点击立即处理。")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(fullScreenIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .build();
        safeNotify(target == EmergencyModeActivity.class ? 4202 : 4201, notification);

        // Best effort direct launch.
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            if (target == EmergencyModeActivity.class) {
                RiskPopupCoordinator.release(RiskLevel.HIGH);
            } else {
                RiskPopupCoordinator.release(RiskLevel.MEDIUM);
            }
            // Keep full-screen notification fallback.
        }
    }

    private Notification buildForegroundNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 5001, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("救在身边正在后台监测")
                .setContentText("已开启自动检测，风险事件将自动唤醒处理")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void safeNotify(int id, Notification notification) {
        if (!canPostNotifications()) {
            return;
        }
        try {
            NotificationManagerCompat.from(this).notify(id, notification);
        } catch (SecurityException ignored) {
            // Do not crash service if notifications are blocked by ROM policy.
        }
    }

    private boolean canPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void createChannelsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel monitor = new NotificationChannel(
                CHANNEL_MONITOR,
                "后台监测",
                NotificationManager.IMPORTANCE_LOW
        );
        monitor.setDescription("持续运行的急救风险检测服务");
        manager.createNotificationChannel(monitor);

        NotificationChannel alert = new NotificationChannel(
                CHANNEL_ALERT,
                "风险告警",
                NotificationManager.IMPORTANCE_HIGH
        );
        alert.setDescription("中高风险自动唤醒与跳转");
        manager.createNotificationChannel(alert);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusionManager != null) {
            fusionManager.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
