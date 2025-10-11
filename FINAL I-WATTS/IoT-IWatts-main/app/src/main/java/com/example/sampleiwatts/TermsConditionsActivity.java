package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TermsConditionsActivity extends AppCompatActivity {

    ImageView icBack;
    TextView tvPrivacyPolicy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terms_conditions);

        // Initialize views
        icBack = findViewById(R.id.ic_back);
        tvPrivacyPolicy = findViewById(R.id.tv_privacy_policy);

        // Set up back button click listener
        icBack.setOnClickListener(v -> navigateBack());

        // Set up privacy policy click listener
        tvPrivacyPolicy.setOnClickListener(v -> {
            Intent intent = new Intent(TermsConditionsActivity.this, PrivacyPolicyActivity.class);
            startActivity(intent);
        });

        // Handle system window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        navigateBack();
    }

    private void navigateBack() {
        boolean fromLogin = getIntent().getBooleanExtra("from_login", false);
        if (fromLogin) {
            // If opened from login, navigate back to LoginActivity
            Intent intent = new Intent(TermsConditionsActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        } else {
            // If opened from Settings, just finish to return to existing Settings
            finish();
        }
    }
}
