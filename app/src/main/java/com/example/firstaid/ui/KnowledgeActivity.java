package com.example.firstaid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.MainActivity;
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
        View navHome = findViewById(R.id.navHome);
        View navAed = findViewById(R.id.navAed);
        View navProfile = findViewById(R.id.navProfile);

        btnBack.setOnClickListener(v -> finish());
        btnCpr.setOnClickListener(v -> tvContent.setText(R.string.knowledge_content_cpr));
        btnAed.setOnClickListener(v -> tvContent.setText(R.string.knowledge_content_aed));
        btnAirway.setOnClickListener(v -> tvContent.setText(R.string.knowledge_content_airway));
        btnBleeding.setOnClickListener(v -> tvContent.setText(R.string.knowledge_content_bleeding));
        navHome.setOnClickListener(v -> openPage(MainActivity.class));
        navAed.setOnClickListener(v -> openPage(AedNavigationActivity.class));
        navProfile.setOnClickListener(v -> openPage(ProfileActivity.class));
    }

    private void openPage(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
