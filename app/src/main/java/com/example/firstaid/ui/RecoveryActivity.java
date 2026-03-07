package com.example.firstaid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.MainActivity;
import com.example.firstaid.R;

public class RecoveryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recovery);

        Button btnTopBack = findViewById(R.id.btnTopBack);
        CheckBox cbContact = findViewById(R.id.cbNotifyContact);
        Button btnSave = findViewById(R.id.btnSaveRecovery);
        Button btnHome = findViewById(R.id.btnBackHome);

        btnTopBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> {
            if (cbContact.isChecked()) {
                Toast.makeText(this, "已通知紧急联系人（示例流程）", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "急救记录已保存。", Toast.LENGTH_SHORT).show();
            }
        });

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
