package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PrivacyPolicyActivity extends AppCompatActivity {

    ImageView icBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_privacy_policy);

        // Initialize views
        icBack = findViewById(R.id.ic_back);

        // Set up back button click listener
        icBack.setOnClickListener(v -> navigateBack());

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
        Intent intent = new Intent(PrivacyPolicyActivity.this, fromLogin ? LoginActivity.class : TermsConditionsActivity.class);
        if (!fromLogin) {
            // keep existing flow when launched from Settings (Terms -> Privacy)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        startActivity(intent);
        finish();
    }
}
