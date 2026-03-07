package com.example.firstaid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;

import java.util.Locale;

public class RiskTriggerActivity extends AppCompatActivity {

    public static final String EXTRA_HELP_REQUESTED = "extra_help_requested";
    public static final String EXTRA_TIMEOUT = "extra_timeout";

    private TextView tvCountdown;
    private CountDownTimer timer;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_risk_trigger);

        tvCountdown = findViewById(R.id.tvCountdown);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnSafe = findViewById(R.id.btnIAmFine);
        Button btnHelp = findViewById(R.id.btnNeedHelp);

        btnBack.setOnClickListener(v -> finishWithResult(false, false));
        btnSafe.setOnClickListener(v -> finishWithResult(false, false));
        btnHelp.setOnClickListener(v -> finishWithResult(true, false));

        initTts();
        startCountdown();
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINA);
                tts.speak("检测到异常情况，请确认是否需要帮助。", TextToSpeech.QUEUE_FLUSH, null, "risk-confirm");
            }
        });
    }

    private void startCountdown() {
        timer = new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText(String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                finishWithResult(false, true);
            }
        };
        timer.start();
    }

    private void finishWithResult(boolean needHelp, boolean timeout) {
        if (timer != null) {
            timer.cancel();
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_HELP_REQUESTED, needHelp);
        data.putExtra(EXTRA_TIMEOUT, timeout);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
