package com.example.firstaid.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;

public class AedNavigationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aed_navigation);

        TextView tvAed = findViewById(R.id.tvAedInfo);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnNav = findViewById(R.id.btnNavigateAed);

        tvAed.setText("最近AED：城市中心广场东门（约 320m）");
        btnBack.setOnClickListener(v -> finish());
        btnNav.setOnClickListener(v -> {
            Uri uri = Uri.parse("geo:0,0?q=AED");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });
    }
}
