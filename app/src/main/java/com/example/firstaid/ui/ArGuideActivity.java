package com.example.firstaid.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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

    private static final String STEP1_CENTER_TEXT = "检查患者意识\n轻拍肩膀并呼喊";
    private final String[] steps = new String[]{
            STEP1_CENTER_TEXT,
            "步骤2：双手放在胸部中央持续按压，保持每分钟120次节奏。",
            "步骤3：准备AED并按照语音提示除颤。"
    };
    private static final int TARGET_BPM = 110;
    private static final long BEAT_INTERVAL_MS = 60_000L / TARGET_BPM;
    private static final long DOUBLE_BEAT_GAP_MS = 120L;
    private static final long GUIDE_FADE_DURATION_MS = 220L;
    private static final int DEFAULT_BUTTON_HEIGHT_DP = 58;
    private static final int AED_BUTTON_HEIGHT_DP = 52;
    private static final float DEFAULT_BUTTON_TEXT_SP = 20f;
    private static final float AED_BUTTON_TEXT_SP = 18f;
    private int index = 0;
    private TextView tvStep;
    private TextView tvCenterInstruction;
    private TextView tvArHint;
    private PreviewView previewView;
    private CprOverlayView cprOverlayView;
    private Button btnNext;
    private TextToSpeech tts;
    private ToneGenerator toneGenerator;
    private boolean ttsReady = false;
    private boolean beatEnabled = false;
    private String pendingUtteranceId = "";
    private int pendingUtteranceStep = -1;
    private ExecutorService cameraExecutor;
    private PoseDetectorHelper poseDetectorHelper;
    private ProcessCameraProvider cameraProvider;
    private final Handler beatHandler = new Handler(Looper.getMainLooper());
    private final Runnable secondBeatToneRunnable = () -> playSingleBeatTone();
    private final Runnable beatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!beatEnabled) {
                return;
            }
            triggerBeatFeedback();
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
        tvCenterInstruction = findViewById(R.id.tvCenterInstruction);
        tvArHint = findViewById(R.id.tvArHint);
        previewView = findViewById(R.id.previewView);
        cprOverlayView = findViewById(R.id.cprOverlayView);
        Button btnBack = findViewById(R.id.btnTopBack);
        btnNext = findViewById(R.id.btnNextStep);
        cameraExecutor = Executors.newSingleThreadExecutor();
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
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
            if (isStepOne(index)) {
                index = 1;
            } else {
                index = (index + 1) % steps.length;
            }
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
        stopBeatMode();
        updateStepUi();
        if (tts != null && ttsReady) {
            pendingUtteranceStep = index;
            pendingUtteranceId = "ar-step-" + index + "-" + System.currentTimeMillis();
            tts.speak(steps[index], TextToSpeech.QUEUE_FLUSH, null, pendingUtteranceId);
        } else if (isCompressionStep(index)) {
            // If TTS is unavailable, still provide visual+audio beat guidance.
            startBeatMode();
        }
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(Locale.CHINA);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        // no-op
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if (!utteranceId.equals(pendingUtteranceId)) {
                            return;
                        }
                        runOnUiThread(() -> {
                            if (pendingUtteranceStep == index && isCompressionStep(index)) {
                                startBeatMode();
                            } else {
                                stopBeatMode();
                            }
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {
                        if (!utteranceId.equals(pendingUtteranceId)) {
                            return;
                        }
                        runOnUiThread(() -> {
                            if (isCompressionStep(index)) {
                                startBeatMode();
                            }
                        });
                    }
                });
                renderStep();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isCompressionStep(index) && (!ttsReady || tts == null || !tts.isSpeaking())) {
            startBeatMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBeatMode();
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
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private boolean isCompressionStep(int stepIndex) {
        return stepIndex == 1;
    }

    private boolean isStepOne(int stepIndex) {
        return stepIndex == 0;
    }

    private void updateStepUi() {
        if (isStepOne(index)) {
            tvCenterInstruction.setText(STEP1_CENTER_TEXT);
            tvCenterInstruction.setVisibility(View.VISIBLE);
            tvStep.setText("请观察是否有正常反应与呼吸。若患者无反应，请立即开始胸外按压。");
            btnNext.setText("患者无反应");
            applyButtonStyleForDefault();
            setGuideOverlayVisible(false);
            return;
        }
        tvCenterInstruction.setVisibility(View.GONE);
        tvStep.setText(steps[index]);
        if (isCompressionStep(index)) {
            btnNext.setText("附近AED");
            applyButtonStyleForAed();
        } else {
            btnNext.setText("下一步");
            applyButtonStyleForDefault();
        }
        setGuideOverlayVisible(true);
    }

    private void applyButtonStyleForAed() {
        btnNext.setBackgroundResource(R.drawable.bg_button_green);
        updateButtonSize(AED_BUTTON_HEIGHT_DP, AED_BUTTON_TEXT_SP);
    }

    private void applyButtonStyleForDefault() {
        btnNext.setBackgroundResource(R.drawable.bg_button_primary);
        updateButtonSize(DEFAULT_BUTTON_HEIGHT_DP, DEFAULT_BUTTON_TEXT_SP);
    }

    private void updateButtonSize(int heightDp, float textSp) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) btnNext.getLayoutParams();
        params.height = dp(heightDp);
        btnNext.setLayoutParams(params);
        btnNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private void setGuideOverlayVisible(boolean visible) {
        fadeView(cprOverlayView, visible);
        fadeView(tvArHint, visible);
    }

    private void fadeView(View view, boolean visible) {
        if (view == null) {
            return;
        }
        if (visible) {
            if (view.getVisibility() != View.VISIBLE) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0f);
            }
            view.animate().alpha(1f).setDuration(GUIDE_FADE_DURATION_MS).start();
        } else {
            view.animate()
                    .alpha(0f)
                    .setDuration(GUIDE_FADE_DURATION_MS)
                    .withEndAction(() -> view.setVisibility(View.INVISIBLE))
                    .start();
        }
    }

    private void startBeatMode() {
        beatEnabled = true;
        beatHandler.removeCallbacks(beatRunnable);
        beatHandler.removeCallbacks(secondBeatToneRunnable);
        beatHandler.post(beatRunnable);
    }

    private void stopBeatMode() {
        beatEnabled = false;
        beatHandler.removeCallbacks(beatRunnable);
        beatHandler.removeCallbacks(secondBeatToneRunnable);
    }

    private void triggerBeatFeedback() {
        if (cprOverlayView != null) {
            cprOverlayView.onBeat(BEAT_INTERVAL_MS);
        }
        playSingleBeatTone();
        // "咚咚"双击效果：主拍 + 短延迟次拍
        beatHandler.removeCallbacks(secondBeatToneRunnable);
        beatHandler.postDelayed(secondBeatToneRunnable, DOUBLE_BEAT_GAP_MS);
    }

    private void playSingleBeatTone() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 70);
        }
    }
}
