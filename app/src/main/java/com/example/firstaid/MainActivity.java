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

import com.example.firstaid.model.RiskLevel;
import com.example.firstaid.service.BackgroundDetectionService;
import com.example.firstaid.ui.AedNavigationActivity;
import com.example.firstaid.ui.ArGuideActivity;
import com.example.firstaid.ui.EmergencyModeActivity;
import com.example.firstaid.ui.KnowledgeActivity;
import com.example.firstaid.ui.MediumRiskActivity;
import com.example.firstaid.ui.ProfileActivity;

public class MainActivity extends AppCompatActivity {
    private TextView tvRiskLevel;
    private TextView tvRiskScore;
    private TextView tvMonitor;
    private TextView tvSuggestion;
    private Button btnLowRiskHelp;
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
        btnLowRiskHelp = findViewById(R.id.btnLowRiskHelp);
    }

    private void bindActions() {
        Button btnAr = findViewById(R.id.btnArEntry);
        Button btnAed = findViewById(R.id.btnAedNav);
        Button btnHotline = findViewById(R.id.btnHotline);
        Button btnKnowledge = findViewById(R.id.btnKnowledge);
        Button btnProfile = findViewById(R.id.btnProfile);
        Button btnMockRisk = findViewById(R.id.btnMockRisk);

        btnAr.setOnClickListener(v -> startActivity(new Intent(this, ArGuideActivity.class)));
        btnAed.setOnClickListener(v -> startActivity(new Intent(this, AedNavigationActivity.class)));
        btnKnowledge.setOnClickListener(v -> startActivity(new Intent(this, KnowledgeActivity.class)));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnMockRisk.setOnClickListener(v -> forceRiskEscalationForDemo());
        btnLowRiskHelp.setOnClickListener(v -> maybeOpenMediumRisk());

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
        tvRiskScore.setText("实时安全评分：" + score + "/100");
        tvMonitor.setText("监测状态：" + monitorState);
        tvSuggestion.setText("提示：" + suggestion);
        updateLowRiskAction(level);

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

    private void maybeOpenMediumRisk() {
        demoLowRiskMode = false;
        if (inInterventionFlow) {
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
        inInterventionFlow = true;
        startActivity(new Intent(this, EmergencyModeActivity.class));
    }

    private void forceRiskEscalationForDemo() {
        demoLowRiskMode = true;
        tvRiskLevel.setText("当前风险等级：低风险");
        tvRiskScore.setText("实时安全评分：80/100");
        tvMonitor.setText("监测状态：演示模式（低风险起始）");
        tvSuggestion.setText("提示：如需帮助，请点击“我需要帮助（进入中风险）”。");
        updateLowRiskAction(RiskLevel.LOW);
        Toast.makeText(this, "已从低风险启动演示流程", Toast.LENGTH_SHORT).show();
    }

    private String getRiskText(RiskLevel level) {
        switch (level) {
            case LOW:
                return "当前风险等级：低风险";
            case MEDIUM:
                return "当前风险等级：中风险";
            case HIGH:
                return "当前风险等级：高风险";
            case SAFE:
            default:
                return "当前风险等级：安全";
        }
    }

    private void renderSafeStatusAfterManualConfirm() {
        tvRiskLevel.setText("当前风险等级：安全");
        tvRiskScore.setText("实时安全评分：100/100");
        tvSuggestion.setText("提示：已确认“我没事”，系统恢复安全监测。");
        updateLowRiskAction(RiskLevel.SAFE);
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
