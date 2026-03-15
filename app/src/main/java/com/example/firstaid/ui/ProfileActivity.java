package com.example.firstaid.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.MainActivity;
import com.example.firstaid.R;

public class ProfileActivity extends AppCompatActivity {

    private EditText etName;
    private EditText etAge;
    private EditText etBloodType;
    private EditText etAllergy;
    private EditText etDisease;
    private EditText etContactName;
    private EditText etContactPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        etBloodType = findViewById(R.id.etBloodType);
        etAllergy = findViewById(R.id.etAllergy);
        etDisease = findViewById(R.id.etDisease);
        etContactName = findViewById(R.id.etContactName);
        etContactPhone = findViewById(R.id.etContactPhone);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnSave = findViewById(R.id.btnSaveProfile);
        View navHome = findViewById(R.id.navHome);
        View navKnowledge = findViewById(R.id.navKnowledge);
        View navAed = findViewById(R.id.navAed);

        loadData();
        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveData());
        navHome.setOnClickListener(v -> openPage(MainActivity.class));
        navKnowledge.setOnClickListener(v -> openPage(KnowledgeActivity.class));
        navAed.setOnClickListener(v -> openPage(AedNavigationActivity.class));
    }

    private void openPage(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void loadData() {
        SharedPreferences sp = getSharedPreferences("firstaid_profile", MODE_PRIVATE);
        etName.setText(sp.getString("name", ""));
        etAge.setText(sp.getString("age", ""));
        etBloodType.setText(sp.getString("blood", ""));
        etAllergy.setText(sp.getString("allergy", ""));
        etDisease.setText(sp.getString("disease", ""));
        etContactName.setText(sp.getString("contact_name", ""));
        etContactPhone.setText(sp.getString("contact_phone", ""));
    }

    private void saveData() {
        SharedPreferences.Editor editor = getSharedPreferences("firstaid_profile", MODE_PRIVATE).edit();
        editor.putString("name", etName.getText().toString().trim());
        editor.putString("age", etAge.getText().toString().trim());
        editor.putString("blood", etBloodType.getText().toString().trim());
        editor.putString("allergy", etAllergy.getText().toString().trim());
        editor.putString("disease", etDisease.getText().toString().trim());
        editor.putString("contact_name", etContactName.getText().toString().trim());
        editor.putString("contact_phone", etContactPhone.getText().toString().trim());
        editor.apply();
        Toast.makeText(this, R.string.profile_saved_toast, Toast.LENGTH_SHORT).show();
    }
}
