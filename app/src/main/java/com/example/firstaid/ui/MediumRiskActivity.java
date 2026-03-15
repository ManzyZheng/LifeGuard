package com.example.firstaid.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.firstaid.R;
import com.example.firstaid.logic.RiskPopupCoordinator;
import com.example.firstaid.model.RiskLevel;
import com.example.firstaid.service.BackgroundDetectionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MediumRiskActivity extends AppCompatActivity {

    public static final String EXTRA_CONFIRMED_SAFE = "extra_confirmed_safe";
    private static final long MEDIUM_RISK_COUNTDOWN_MS = 60_000L;
    private static final long HIGH_RISK_DELAY_MS = 5_000L;
    private CountDownTimer timer;
    private CountDownTimer highRiskTimer;
    private TextToSpeech tts;
    private TextView tvVoiceStatus;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private final Handler voiceHandler = new Handler(Looper.getMainLooper());
    private boolean enableVoiceListening = false;
    private boolean highRiskTransitioning = false;
    private boolean riskReceiverRegistered = false;

    private final Runnable restartListeningTask = new Runnable() {
        @Override
        public void run() {
            startVoiceListening();
        }
    };
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
            if (!highRiskTransitioning && level == RiskLevel.HIGH) {
                openEmergencyModeImmediate();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!RiskPopupCoordinator.tryEnter(RiskLevel.MEDIUM)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_medium_risk);

        TextView tvTimer = findViewById(R.id.tvMediumTimer);
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
        Button btnSafe = findViewById(R.id.btnMediumSafe);
        Button btnHelp = findViewById(R.id.btnMediumHelp);

        btnSafe.setOnClickListener(v -> finishAsSafe());
        btnHelp.setOnClickListener(v -> startHighRiskCountdownThenEmergency());

        vibrateAlert();
        initSpeechRecognition();
        initTts();
        timer = new CountDownTimer(MEDIUM_RISK_COUNTDOWN_MS, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.medium_risk_timer_format, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                startHighRiskCountdownThenEmergency();
            }
        };
        timer.start();
    }

    private void vibrateAlert() {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINA);
                tts.speak(getString(R.string.medium_risk_tts_prompt), TextToSpeech.QUEUE_FLUSH, null, "medium-risk");
                // Start listening after TTS prompt to avoid capturing own speech output.
                voiceHandler.postDelayed(this::startVoiceListening, 1500L);
            }
        });
    }

    private void initSpeechRecognition() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        boolean speechRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(this);
        boolean recognizerIntentAvailable = hasRecognizerIntentService();
        if (!speechRecognizerAvailable && !recognizerIntentAvailable) {
            tvVoiceStatus.setText(R.string.medium_risk_voice_no_service);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            tvVoiceStatus.setText(R.string.medium_risk_voice_no_permission);
            return;
        }

        enableVoiceListening = true;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvVoiceStatus.setText(R.string.medium_risk_voice_listening);
            }

            @Override
            public void onBeginningOfSpeech() {
                tvVoiceStatus.setText(R.string.medium_risk_voice_recognizing);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // no-op
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // no-op
            }

            @Override
            public void onEndOfSpeech() {
                tvVoiceStatus.setText(R.string.medium_risk_voice_analyzing);
            }

            @Override
            public void onError(int error) {
                tvVoiceStatus.setText(R.string.medium_risk_voice_no_valid_reply);
                scheduleVoiceListeningRestart();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (containsEmergencyIntent(texts)) {
                    tvVoiceStatus.setText(R.string.medium_risk_voice_recognized_help);
                    startHighRiskCountdownThenEmergency();
                } else {
                    tvVoiceStatus.setText(R.string.medium_risk_voice_retry);
                    scheduleVoiceListeningRestart();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // no-op
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // no-op
            }
        });

    }

    private boolean hasRecognizerIntentService() {
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                PackageManager.MATCH_DEFAULT_ONLY
        );
        return handlers != null && !handlers.isEmpty();
    }

    private void startVoiceListening() {
        if (!enableVoiceListening || speechRecognizer == null || recognizerIntent == null) {
            return;
        }
        speechRecognizer.cancel();
        speechRecognizer.startListening(recognizerIntent);
    }

    private void scheduleVoiceListeningRestart() {
        if (!enableVoiceListening) {
            return;
        }
        voiceHandler.removeCallbacks(restartListeningTask);
        voiceHandler.postDelayed(restartListeningTask, 1200L);
    }

    private boolean containsEmergencyIntent(ArrayList<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return false;
        }
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String normalized = text.replace(" ", "");
            if (normalized.contains("需要急救")
                    || normalized.contains("我要急救")
                    || normalized.contains("需要帮助")
                    || normalized.contains("快救我")
                    || normalized.contains("救命")) {
                return true;
            }
        }
        return false;
    }

    private void finishAsSafe() {
        notifyBackgroundServiceUserSafe();
        Intent data = new Intent();
        data.putExtra(EXTRA_CONFIRMED_SAFE, true);
        setResult(RESULT_OK, data);
        finish();
    }

    private void startHighRiskCountdownThenEmergency() {
        if (highRiskTransitioning) {
            return;
        }
        highRiskTransitioning = true;
        enableVoiceListening = false;
        voiceHandler.removeCallbacks(restartListeningTask);
        if (timer != null) {
            timer.cancel();
        }
        if (highRiskTimer != null) {
            highRiskTimer.cancel();
        }
        highRiskTimer = new CountDownTimer(HIGH_RISK_DELAY_MS, 1_000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000L;
                tvVoiceStatus.setText(getString(R.string.medium_risk_voice_enter_emergency_in, seconds));
            }

            @Override
            public void onFinish() {
                tvVoiceStatus.setText(R.string.medium_risk_voice_entering_emergency);
                startActivity(new Intent(MediumRiskActivity.this, EmergencyModeActivity.class));
                finish();
            }
        };
        highRiskTimer.start();
    }

    private void openEmergencyModeImmediate() {
        if (highRiskTransitioning) {
            return;
        }
        highRiskTransitioning = true;
        enableVoiceListening = false;
        voiceHandler.removeCallbacks(restartListeningTask);
        if (timer != null) {
            timer.cancel();
        }
        if (highRiskTimer != null) {
            highRiskTimer.cancel();
        }
        tvVoiceStatus.setText(R.string.medium_risk_voice_immediate_emergency);
        startActivity(new Intent(MediumRiskActivity.this, EmergencyModeActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!riskReceiverRegistered) {
            IntentFilter filter = new IntentFilter(BackgroundDetectionService.ACTION_RISK_UPDATE);
            ContextCompat.registerReceiver(this, riskReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
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
        enableVoiceListening = false;
        voiceHandler.removeCallbacks(restartListeningTask);
        if (timer != null) {
            timer.cancel();
        }
        if (highRiskTimer != null) {
            highRiskTimer.cancel();
        }
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        RiskPopupCoordinator.release(RiskLevel.MEDIUM);
    }

    private void notifyBackgroundServiceUserSafe() {
        Intent intent = new Intent(this, BackgroundDetectionService.class);
        intent.setAction(BackgroundDetectionService.ACTION_USER_CONFIRMED_SAFE);
        // Called from visible Activity; use regular service start for this control action.
        startService(intent);
    }
}
