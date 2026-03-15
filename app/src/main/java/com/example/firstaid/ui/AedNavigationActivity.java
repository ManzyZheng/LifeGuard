package com.example.firstaid.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.MainActivity;
import com.example.firstaid.R;

public class AedNavigationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aed_navigation);

        TextView tvAed = findViewById(R.id.tvAedInfo);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnNav = findViewById(R.id.btnNavigateAed);
        View navHome = findViewById(R.id.navHome);
        View navKnowledge = findViewById(R.id.navKnowledge);
        View navProfile = findViewById(R.id.navProfile);

        tvAed.setText(R.string.aed_info_nearest);
        btnBack.setOnClickListener(v -> finish());
        btnNav.setOnClickListener(v -> {
            Uri uri = Uri.parse("geo:0,0?q=AED");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });
        navHome.setOnClickListener(v -> openPage(MainActivity.class));
        navKnowledge.setOnClickListener(v -> openPage(KnowledgeActivity.class));
        navProfile.setOnClickListener(v -> openPage(ProfileActivity.class));
    }

    private void openPage(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
