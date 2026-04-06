package com.example.firstaid.ui;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firstaid.R;

public class KnowledgeActivity extends AppCompatActivity {

    private TextView tvContent;
    private LinearLayout imageContainer;
    private static final String CPR_IMAGE_PREFIX = "knowledge_cpr_";
    private static final int CPR_IMAGE_MAX_COUNT = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge);

        tvContent = findViewById(R.id.tvKnowledgeContent);
        imageContainer = findViewById(R.id.layoutKnowledgeImages);
        Button btnBack = findViewById(R.id.btnTopBack);
        Button btnCpr = findViewById(R.id.btnLearnCpr);
        Button btnAed = findViewById(R.id.btnLearnAed);
        Button btnAirway = findViewById(R.id.btnLearnAirway);
        Button btnBleeding = findViewById(R.id.btnLearnBleeding);

        btnBack.setOnClickListener(v -> finish());
        btnCpr.setOnClickListener(v -> {
            tvContent.setText(R.string.knowledge_content_cpr);
            showCprImages();
        });
        btnAed.setOnClickListener(v -> {
            tvContent.setText(R.string.knowledge_content_aed);
            hideKnowledgeImages();
        });
        btnAirway.setOnClickListener(v -> {
            tvContent.setText(R.string.knowledge_content_airway);
            hideKnowledgeImages();
        });
        btnBleeding.setOnClickListener(v -> {
            tvContent.setText(R.string.knowledge_content_bleeding);
            hideKnowledgeImages();
        });
    }

    private void showCprImages() {
        imageContainer.removeAllViews();
        int shown = 0;
        for (int i = 1; i <= CPR_IMAGE_MAX_COUNT; i++) {
            String drawableName = CPR_IMAGE_PREFIX + i;
            int drawableId = getResources().getIdentifier(drawableName, "drawable", getPackageName());
            if (drawableId == 0) {
                continue;
            }
            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dpToPx(8);
            imageView.setLayoutParams(params);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageResource(drawableId);
            imageContainer.addView(imageView);
            shown++;
        }
        imageContainer.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    private void hideKnowledgeImages() {
        imageContainer.removeAllViews();
        imageContainer.setVisibility(View.GONE);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

}
