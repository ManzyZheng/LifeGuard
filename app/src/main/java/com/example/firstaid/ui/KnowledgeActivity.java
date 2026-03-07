package com.example.firstaid.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;

public class KnowledgeActivity extends AppCompatActivity {

    private TextView tvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge);

        tvContent = findViewById(R.id.tvKnowledgeContent);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnCpr = findViewById(R.id.btnLearnCpr);
        Button btnAed = findViewById(R.id.btnLearnAed);
        Button btnAirway = findViewById(R.id.btnLearnAirway);
        Button btnBleeding = findViewById(R.id.btnLearnBleeding);

        btnBack.setOnClickListener(v -> finish());
        btnCpr.setOnClickListener(v -> tvContent.setText("CPR：先判断意识和呼吸，再进行高质量胸外按压。"));
        btnAed.setOnClickListener(v -> tvContent.setText("AED：开机后按语音提示贴片并避免接触患者。"));
        btnAirway.setOnClickListener(v -> tvContent.setText("气道异物：鼓励咳嗽，无效时进行腹部冲击法。"));
        btnBleeding.setOnClickListener(v -> tvContent.setText("止血包扎：直接压迫出血点，必要时使用止血带。"));
    }
}
