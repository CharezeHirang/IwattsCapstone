package com.example.sampleiwatts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TermsActivity extends AppCompatActivity {

    ImageView icBack;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terms);

        icBack = findViewById(R.id.ic_back);

        // Set up back button to navigate to Login Activity
        if (icBack != null) {
            icBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(TermsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            });
        }

        // Navigate to Privacy Activity after showing terms
        // You can trigger this with a button or automatically
        // For now, I'll add this as a method you can call
        openPrivacyActivity();
    }

    private void openPrivacyActivity() {
        Intent intent = new Intent(TermsActivity.this, PrivacyPolicyActivity.class);
        intent.putExtra("from_terms", true);
        startActivity(intent);
    }
}