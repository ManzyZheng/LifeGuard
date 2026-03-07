package com.example.firstaid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;

public class LowRiskPopupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_low_risk_popup);

        TextView tvMessage = findViewById(R.id.tvLowRiskMessage);
        Button btnIgnore = findViewById(R.id.btnLowRiskIgnore);
        Button btnNeedHelp = findViewById(R.id.btnLowRiskNeedHelp);

        tvMessage.setText("检测到轻微异常运动，请确认是否需要帮助。");
        btnIgnore.setOnClickListener(v -> finish());
        btnNeedHelp.setOnClickListener(v -> {
            Intent intent = new Intent(this, MediumRiskActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
