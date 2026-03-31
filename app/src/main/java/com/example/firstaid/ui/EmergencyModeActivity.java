package com.example.firstaid.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;
import com.example.firstaid.logic.RiskPopupCoordinator;
import com.example.firstaid.model.RiskLevel;

import java.util.Locale;

public class EmergencyModeActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private View emergencyGuideOverlay;
    private View emergencyGuideStepCall;
    private View emergencyGuideStepActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!RiskPopupCoordinator.tryEnter(RiskLevel.HIGH)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_emergency_mode);

        TextView pulseDot = findViewById(R.id.viewPulseDot);
        TextView tvRescueState = findViewById(R.id.tvRescueState);
        Button btnCall = findViewById(R.id.btnAutoCall);
        Button btnLocation = findViewById(R.id.btnShareLocation);
        Button btnArGuide = findViewById(R.id.btnOpenArGuide);
        Button btnCollaboration = findViewById(R.id.btnOpenCollaboration);
        Button btnRecovery = findViewById(R.id.btnFinishEmergency);
        emergencyGuideOverlay = findViewById(R.id.emergencyGuideOverlay);
        emergencyGuideStepCall = findViewById(R.id.emergencyGuideStepCall);
        emergencyGuideStepActions = findViewById(R.id.emergencyGuideStepActions);
        Button btnPopupNext = findViewById(R.id.btnEmergencyPopupNext);
        View btnPopupClose = findViewById(R.id.btnEmergencyPopupClose);
        Button btnPopupAr = findViewById(R.id.btnEmergencyPopupAr);
        Button btnPopupAed = findViewById(R.id.btnEmergencyPopupAed);
        View btnPopupCloseStep2 = findViewById(R.id.btnEmergencyPopupCloseStep2);

        tvRescueState.setText(R.string.emergency_rescue_triggered);
        startPulseAnimation(pulseDot);
        initTts();

        btnCall.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:120"))));
        btnLocation.setOnClickListener(v -> Toast.makeText(this, R.string.emergency_share_location_toast, Toast.LENGTH_SHORT).show());
        btnArGuide.setOnClickListener(v -> startActivity(new Intent(this, ArGuideActivity.class)));
        btnCollaboration.setOnClickListener(v -> startActivity(new Intent(this, CollaborationActivity.class)));
        btnRecovery.setOnClickListener(v -> {
            startActivity(new Intent(this, RecoveryActivity.class));
            finish();
        });

        // Show first guidance popup every time this page is opened.
        resetPopupFlow();
        btnPopupNext.setOnClickListener(v -> {
            emergencyGuideStepCall.setVisibility(View.GONE);
            emergencyGuideStepActions.setVisibility(View.VISIBLE);
        });
        btnPopupClose.setOnClickListener(v -> emergencyGuideOverlay.setVisibility(View.GONE));
        btnPopupCloseStep2.setOnClickListener(v -> emergencyGuideOverlay.setVisibility(View.GONE));
        btnPopupAr.setOnClickListener(v -> {
            emergencyGuideOverlay.setVisibility(View.GONE);
            btnArGuide.performClick();
        });
        btnPopupAed.setOnClickListener(v -> {
            emergencyGuideOverlay.setVisibility(View.GONE);
            startActivity(new Intent(this, AedNavigationActivity.class));
        });
    }

    private void resetPopupFlow() {
        if (emergencyGuideOverlay == null || emergencyGuideStepCall == null || emergencyGuideStepActions == null) {
            return;
        }
        emergencyGuideOverlay.setVisibility(View.VISIBLE);
        emergencyGuideStepCall.setVisibility(View.VISIBLE);
        emergencyGuideStepActions.setVisibility(View.GONE);
    }

    private void startPulseAnimation(TextView pulseDot) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(pulseDot, "alpha", 0.2f, 1f);
        animator.setDuration(600);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINA);
                tts.speak(getString(R.string.emergency_tts_script), TextToSpeech.QUEUE_FLUSH, null, "emergency-mode");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        RiskPopupCoordinator.release(RiskLevel.HIGH);
    }
}
