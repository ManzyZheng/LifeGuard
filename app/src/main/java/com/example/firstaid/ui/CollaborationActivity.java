package com.example.firstaid.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;

public class CollaborationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collaboration);

        Button btnBack = findViewById(R.id.btnTopBack);
        TextView tvTask = findViewById(R.id.tvTaskAssign);
        btnBack.setOnClickListener(v -> finish());
        tvTask.setText("任务分配建议：\n1. 一人持续胸外按压\n2. 一人前往最近AED点位\n3. 一人与急救中心保持通话");
    }
}
