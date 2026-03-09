package com.example.firstaid.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.firstaid.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArGuideActivity extends AppCompatActivity {

    private final String[] steps = new String[]{
            "步骤1：检查患者意识与呼吸。",
            "步骤2：双手放在胸部中央，开始按压。",
            "步骤3：保持每分钟100~120次按压节奏。",
            "步骤4：准备AED并按照语音提示除颤。"
    };
    private static final int TARGET_BPM = 110;
    private static final long BEAT_INTERVAL_MS = 60_000L / TARGET_BPM;
    private int index = 0;
    private TextView tvStep;
    private PreviewView previewView;
    private CprOverlayView cprOverlayView;
    private TextToSpeech tts;
    private ExecutorService cameraExecutor;
    private PoseDetectorHelper poseDetectorHelper;
    private ProcessCameraProvider cameraProvider;
    private final Handler beatHandler = new Handler(Looper.getMainLooper());
    private final Runnable beatRunnable = new Runnable() {
        @Override
        public void run() {
            if (cprOverlayView != null) {
                cprOverlayView.onBeat(BEAT_INTERVAL_MS);
            }
            beatHandler.postDelayed(this, BEAT_INTERVAL_MS);
        }
    };
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCameraPipeline();
                } else {
                    Toast.makeText(this, "未授予相机权限，无法启动AR胸口定位。", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_guide);

        tvStep = findViewById(R.id.tvArStep);
        previewView = findViewById(R.id.previewView);
        cprOverlayView = findViewById(R.id.cprOverlayView);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnNext = findViewById(R.id.btnNextStep);
        cameraExecutor = Executors.newSingleThreadExecutor();
        poseDetectorHelper = new PoseDetectorHelper(this, new PoseDetectorHelper.Listener() {
            @Override
            public void onChestDetected(float chestX, float chestY) {
                runOnUiThread(() -> cprOverlayView.updateChestPoint(chestX, chestY));
            }

            @Override
            public void onPoseMissing() {
                runOnUiThread(() -> cprOverlayView.clearChestPoint());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ArGuideActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraPipeline();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraPipeline() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Toast.makeText(this, "相机初始化失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> poseDetectorHelper.detectFromImageProxy(imageProxy));

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
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
    protected void onResume() {
        super.onResume();
        beatHandler.removeCallbacks(beatRunnable);
        beatHandler.post(beatRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        beatHandler.removeCallbacks(beatRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beatHandler.removeCallbacks(beatRunnable);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (poseDetectorHelper != null) {
            poseDetectorHelper.release();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
