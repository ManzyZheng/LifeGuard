package com.example.firstaid.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.firstaid.R;

import java.util.Locale;

public class ArGuideActivity extends AppCompatActivity {

    private final String[] steps = new String[]{
            "步骤1：检查患者意识与呼吸。",
            "步骤2：双手放在胸部中央，开始按压。",
            "步骤3：保持每分钟100~120次按压节奏。",
            "步骤4：准备AED并按照语音提示除颤。"
    };
    private int index = 0;
    private TextView tvStep;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_guide);

        tvStep = findViewById(R.id.tvArStep);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnNext = findViewById(R.id.btnNextStep);

        ensureCameraPermission();
        initTts();
        renderStep();

        btnBack.setOnClickListener(v -> finish());

        btnNext.setOnClickListener(v -> {
            index = (index + 1) % steps.length;
            renderStep();
        });
    }

    private void ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "未授予相机权限，AR预览使用占位视图。", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderStep() {
        tvStep.setText(steps[index]);
        if (tts != null) {
            tts.speak(steps[index], TextToSpeech.QUEUE_FLUSH, null, "ar-step-" + index);
        }
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINA);
                renderStep();
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
