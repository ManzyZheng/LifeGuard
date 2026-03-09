package com.example.firstaid.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.logic.RiskPopupCoordinator;
import com.example.firstaid.model.RiskLevel;
import com.example.firstaid.R;
import com.example.firstaid.service.BackgroundDetectionService;

public class LowRiskPopupActivity extends AppCompatActivity {
    private boolean riskReceiverRegistered = false;
    private final BroadcastReceiver riskReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !BackgroundDetectionService.ACTION_RISK_UPDATE.equals(intent.getAction())) {
                return;
            }
            String levelName = intent.getStringExtra(BackgroundDetectionService.EXTRA_RISK_LEVEL);
            RiskLevel level;
            try {
                level = levelName == null ? RiskLevel.SAFE : RiskLevel.valueOf(levelName);
            } catch (IllegalArgumentException e) {
                level = RiskLevel.SAFE;
            }
            if (level == RiskLevel.MEDIUM || level == RiskLevel.HIGH) {
                // Escalated risk should replace low-risk popup immediately.
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!RiskPopupCoordinator.tryEnter(RiskLevel.LOW)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_low_risk_popup);

        TextView tvMessage = findViewById(R.id.tvLowRiskMessage);
        Button btnIgnore = findViewById(R.id.btnLowRiskIgnore);
        Button btnNeedHelp = findViewById(R.id.btnLowRiskNeedHelp);

        tvMessage.setText("检测到轻微异常运动，请确认是否需要帮助。");
        btnIgnore.setOnClickListener(v -> finish());
        btnNeedHelp.setOnClickListener(v -> {
            if (!RiskPopupCoordinator.tryRequest(RiskLevel.MEDIUM)) {
                finish();
                return;
            }
            Intent intent = new Intent(this, MediumRiskActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!riskReceiverRegistered) {
            IntentFilter filter = new IntentFilter(BackgroundDetectionService.ACTION_RISK_UPDATE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(riskReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(riskReceiver, filter);
            }
            riskReceiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (riskReceiverRegistered) {
            unregisterReceiver(riskReceiver);
            riskReceiverRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RiskPopupCoordinator.release(RiskLevel.LOW);
    }
}
