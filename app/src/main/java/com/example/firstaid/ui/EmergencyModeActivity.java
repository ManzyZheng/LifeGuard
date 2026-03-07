package com.example.firstaid.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;

import java.util.Locale;

public class EmergencyModeActivity extends AppCompatActivity {

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_mode);

        TextView pulseDot = findViewById(R.id.viewPulseDot);
        TextView tvRescueState = findViewById(R.id.tvRescueState);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnCall = findViewById(R.id.btnAutoCall);
        Button btnLocation = findViewById(R.id.btnShareLocation);
        Button btnArGuide = findViewById(R.id.btnOpenArGuide);
        Button btnCollaboration = findViewById(R.id.btnOpenCollaboration);
        Button btnRecovery = findViewById(R.id.btnFinishEmergency);

        tvRescueState.setText("救援状态：已触发急救流程，等待120响应。");
        startPulseAnimation(pulseDot);
        initTts();

        btnBack.setOnClickListener(v -> finish());
        btnCall.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:120"))));
        btnLocation.setOnClickListener(v -> Toast.makeText(this, "已发送定位信息（示例流程）", Toast.LENGTH_SHORT).show());
        btnArGuide.setOnClickListener(v -> startActivity(new Intent(this, ArGuideActivity.class)));
        btnCollaboration.setOnClickListener(v -> startActivity(new Intent(this, CollaborationActivity.class)));
        btnRecovery.setOnClickListener(v -> {
            startActivity(new Intent(this, RecoveryActivity.class));
            finish();
        });
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
                tts.speak("急救模式已启动。步骤一，检查呼吸。步骤二，开始胸外按压。步骤三，准备AED。", TextToSpeech.QUEUE_FLUSH, null, "emergency-mode");
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
    }
}
