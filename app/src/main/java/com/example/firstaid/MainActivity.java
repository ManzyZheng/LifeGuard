package com.example.firstaid;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.firstaid.logic.RiskPopupCoordinator;
import com.example.firstaid.model.RiskLevel;
import com.example.firstaid.service.BackgroundDetectionService;
import com.example.firstaid.ui.AedNavigationActivity;
import com.example.firstaid.ui.ArGuideActivity;
import com.example.firstaid.ui.EmergencyModeActivity;
import com.example.firstaid.ui.KnowledgeActivity;
import com.example.firstaid.ui.MediumRiskActivity;
import com.example.firstaid.ui.ProfileActivity;
import com.example.firstaid.ui.RiskGaugeView;

public class MainActivity extends AppCompatActivity {
    private TextView tvRiskLevel;
    private TextView tvRiskScore;
    private TextView tvMonitor;
    private TextView tvSuggestion;
    private TextView tvRiskState;
    private View viewRiskDot;
    private RiskGaugeView riskGaugeView;
    private Button btnLowRiskHelp;
    private Button btnSafeConfirm;
    private View btnMockRisk;
    private boolean demoLowRiskMode = false;
    private boolean inInterventionFlow = false;
    private boolean riskReceiverRegistered = false;
    private long lastRiskUpdateMs = 0L;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> mediumRiskLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                inInterventionFlow = false;
                Intent data = result.getData();
                if (data == null) {
                    return;
                }
                boolean confirmedSafe = data.getBooleanExtra(MediumRiskActivity.EXTRA_CONFIRMED_SAFE, false);
                if (confirmedSafe) {
                    notifyBackgroundServiceUserSafe();
                    renderSafeStatusAfterManualConfirm();
                }
            });
    private final BroadcastReceiver riskReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !BackgroundDetectionService.ACTION_RISK_UPDATE.equals(intent.getAction())) {
                return;
            }
            String levelName = intent.getStringExtra(BackgroundDetectionService.EXTRA_RISK_LEVEL);
            int score = intent.getIntExtra(BackgroundDetectionService.EXTRA_RISK_SCORE, 100);
            String monitorState = intent.getStringExtra(BackgroundDetectionService.EXTRA_MONITOR_STATE);
            String suggestion = intent.getStringExtra(BackgroundDetectionService.EXTRA_SUGGESTION);
            lastRiskUpdateMs = System.currentTimeMillis();

            RiskLevel level;
            try {
                level = levelName == null ? RiskLevel.SAFE : RiskLevel.valueOf(levelName);
            } catch (IllegalArgumentException e) {
                level = RiskLevel.SAFE;
            }
            onRiskUpdated(level, score,
                    monitorState == null ? "后台监测中" : monitorState,
                    suggestion == null ? "状态稳定，继续监测。" : suggestion);
        }
    };
    private final Runnable monitorWatchdog = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            // If no risk updates for >4s, restart background service automatically.
            if (now - lastRiskUpdateMs > 4_000L) {
                tvMonitor.setText("监测状态：后台服务重连中...");
                startBackgroundDetectionService();
            }
            watchdogHandler.postDelayed(this, 2_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        bindActions();
        requestRuntimePermissions();
        startBackgroundDetectionService();
    }

    private void bindViews() {
        tvRiskLevel = findViewById(R.id.tvRiskLevel);
        tvRiskScore = findViewById(R.id.tvRiskScore);
        tvMonitor = findViewById(R.id.tvMonitorState);
        tvSuggestion = findViewById(R.id.tvSuggestion);
        tvRiskState = findViewById(R.id.tvRiskState);
        viewRiskDot = findViewById(R.id.viewRiskDot);
        riskGaugeView = findViewById(R.id.viewRiskGauge);
        btnLowRiskHelp = findViewById(R.id.btnLowRiskHelp);
        btnSafeConfirm = findViewById(R.id.btnSafeConfirm);
    }

    private void bindActions() {
        View btnAr = findViewById(R.id.btnArEntry);
        View btnAed = findViewById(R.id.btnAedNav);
        View btnHotline = findViewById(R.id.btnHotline);
        View btnKnowledge = findViewById(R.id.btnKnowledge);
        View btnProfile = findViewById(R.id.btnProfile);
        btnMockRisk = findViewById(R.id.btnMockRisk);
        View navHome = findViewById(R.id.navHome);
        View navKnowledge = findViewById(R.id.navKnowledge);
        View navAed = findViewById(R.id.navAed);
        View navProfile = findViewById(R.id.navProfile);

        btnAr.setOnClickListener(v -> startActivity(new Intent(this, ArGuideActivity.class)));
        btnAed.setOnClickListener(v -> startActivity(new Intent(this, AedNavigationActivity.class)));
        btnKnowledge.setOnClickListener(v -> startActivity(new Intent(this, KnowledgeActivity.class)));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnMockRisk.setOnClickListener(v -> forceRiskEscalationForDemo());
        btnLowRiskHelp.setOnClickListener(v -> maybeOpenMediumRisk());
        btnSafeConfirm.setOnClickListener(v -> {
            notifyBackgroundServiceUserSafe();
            renderSafeStatusAfterManualConfirm();
        });

        navHome.setOnClickListener(v -> {
            // Already on home; keep current behavior.
        });
        navKnowledge.setOnClickListener(v -> btnKnowledge.performClick());
        navAed.setOnClickListener(v -> btnAed.performClick());
        navProfile.setOnClickListener(v -> btnProfile.performClick());

        btnHotline.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:120"));
            startActivity(intent);
        });
    }

    private void onRiskUpdated(RiskLevel level, int score, String monitorState, String suggestion) {
        if (demoLowRiskMode) {
            // Keep demo state stable until user explicitly clicks "我需要帮助".
            return;
        }
        tvRiskLevel.setText(getRiskText(level));
        tvRiskScore.setText(String.valueOf(score));
        tvMonitor.setText("监测状态：" + monitorState);
        tvSuggestion.setText(suggestion);
        applyRiskVisual(level, score);
        updateLowRiskAction(level);
        updateSafeConfirmAction(level);
        updateDemoEntryVisibility(level);

        // Foreground fallback: even if background service is restricted by ROM,
        // app should still navigate immediately while user is on the home screen.
        if (level == RiskLevel.HIGH) {
            maybeOpenEmergencyMode();
        } else if (level == RiskLevel.MEDIUM) {
            maybeOpenMediumRisk();
        }
    }

    private void updateLowRiskAction(RiskLevel level) {
        if (btnLowRiskHelp == null) {
            return;
        }
        btnLowRiskHelp.setVisibility(level == RiskLevel.LOW ? View.VISIBLE : View.GONE);
    }

    private void updateSafeConfirmAction(RiskLevel level) {
        if (btnSafeConfirm == null) {
            return;
        }
        btnSafeConfirm.setVisibility(level == RiskLevel.SAFE ? View.GONE : View.VISIBLE);
    }

    private void maybeOpenMediumRisk() {
        demoLowRiskMode = false;
        if (inInterventionFlow) {
            return;
        }
        if (!RiskPopupCoordinator.tryRequest(RiskLevel.MEDIUM)) {
            return;
        }
        inInterventionFlow = true;
        Intent intent = new Intent(this, MediumRiskActivity.class);
        mediumRiskLauncher.launch(intent);
    }

    private void maybeOpenEmergencyMode() {
        if (inInterventionFlow) {
            return;
        }
        if (!RiskPopupCoordinator.tryRequest(RiskLevel.HIGH)) {
            return;
        }
        inInterventionFlow = true;
        startActivity(new Intent(this, EmergencyModeActivity.class));
    }

    private void forceRiskEscalationForDemo() {
        demoLowRiskMode = true;
        tvRiskLevel.setText("低风险");
        tvRiskScore.setText("80");
        tvMonitor.setText("监测状态：演示模式（低风险起始）");
        tvSuggestion.setText("如需帮助，请点击“我需要帮助（进入中风险）”。");
        applyRiskVisual(RiskLevel.LOW, 80);
        updateLowRiskAction(RiskLevel.LOW);
        updateSafeConfirmAction(RiskLevel.LOW);
        updateDemoEntryVisibility(RiskLevel.LOW);
        Toast.makeText(this, "已从低风险启动演示流程", Toast.LENGTH_SHORT).show();
    }

    private String getRiskText(RiskLevel level) {
        switch (level) {
            case LOW:
                return "低风险";
            case MEDIUM:
                return "中风险";
            case HIGH:
                return "高风险";
            case SAFE:
            default:
                return "安全";
        }
    }

    private void renderSafeStatusAfterManualConfirm() {
        tvRiskLevel.setText("安全");
        tvRiskScore.setText("100");
        tvSuggestion.setText("已确认“我没事”，系统恢复安全监测。");
        applyRiskVisual(RiskLevel.SAFE, 100);
        updateLowRiskAction(RiskLevel.SAFE);
        updateSafeConfirmAction(RiskLevel.SAFE);
        updateDemoEntryVisibility(RiskLevel.SAFE);
    }

    private void updateDemoEntryVisibility(RiskLevel level) {
        if (btnMockRisk == null) {
            return;
        }
        btnMockRisk.setVisibility(level == RiskLevel.LOW ? View.GONE : View.VISIBLE);
    }

    private void applyRiskVisual(RiskLevel level, int score) {
        int levelColorRes;
        int gaugeColorRes;
        switch (level) {
            case LOW:
                levelColorRes = R.color.ui_risk_low;
                gaugeColorRes = R.color.ui_risk_low;
                break;
            case MEDIUM:
                levelColorRes = R.color.ui_risk_medium;
                gaugeColorRes = R.color.ui_risk_medium;
                break;
            case HIGH:
                levelColorRes = R.color.ui_risk_high;
                gaugeColorRes = R.color.ui_risk_high;
                break;
            case SAFE:
            default:
                levelColorRes = R.color.ui_risk_safe;
                gaugeColorRes = R.color.ui_risk_safe;
                break;
        }
        int levelColor = ContextCompat.getColor(this, levelColorRes);
        int gaugeColor = ContextCompat.getColor(this, gaugeColorRes);
        int riskStateColor = ContextCompat.getColor(this,
                level == RiskLevel.SAFE ? R.color.ui_risk_safe : R.color.ui_risk_high);
        tvRiskLevel.setTextColor(levelColor);

        if (tvRiskState != null) {
            tvRiskState.setText(level == RiskLevel.SAFE ? "监测中" : "风险预警");
            tvRiskState.setTextColor(riskStateColor);
        }

        if (viewRiskDot != null) {
            viewRiskDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(riskStateColor));
        }
        if (riskGaugeView != null) {
            riskGaugeView.setProgressColor(gaugeColor);
            riskGaugeView.setScore(score);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!riskReceiverRegistered) {
            IntentFilter filter = new IntentFilter(BackgroundDetectionService.ACTION_RISK_UPDATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(riskReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(riskReceiver, filter);
            }
            riskReceiverRegistered = true;
        }
        // Ensure service is alive whenever home screen becomes visible.
        startBackgroundDetectionService();
        if (lastRiskUpdateMs == 0L) {
            lastRiskUpdateMs = System.currentTimeMillis();
        }
        watchdogHandler.removeCallbacks(monitorWatchdog);
        watchdogHandler.postDelayed(monitorWatchdog, 2_000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (riskReceiverRegistered) {
            unregisterReceiver(riskReceiver);
            riskReceiverRegistered = false;
        }
        watchdogHandler.removeCallbacks(monitorWatchdog);
        inInterventionFlow = false;
    }

    private void requestRuntimePermissions() {
        String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS
        };

        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, 1101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1101) {
            return;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "部分权限未授权，相关能力将受限。", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "已获取核心权限。", Toast.LENGTH_SHORT).show();
    }

    private void startBackgroundDetectionService() {
        Intent serviceIntent = new Intent(this, BackgroundDetectionService.class);
        serviceIntent.setAction(BackgroundDetectionService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void notifyBackgroundServiceUserSafe() {
        Intent intent = new Intent(this, BackgroundDetectionService.class);
        intent.setAction(BackgroundDetectionService.ACTION_USER_CONFIRMED_SAFE);
        // This action is sent while UI is in foreground; regular startService is sufficient
        // and avoids foreground-service start timing edge cases.
        startService(intent);
    }
}
