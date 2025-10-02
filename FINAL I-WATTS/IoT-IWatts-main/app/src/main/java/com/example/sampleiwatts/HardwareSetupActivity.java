package com.example.sampleiwatts;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class HardwareSetupActivity extends AppCompatActivity {

    private static final String HARDWARE_IP = "192.168.4.1"; // Default ESP32 AP IP
    private static final String HARDWARE_AP_SSID = "PowerLogger";

    private EditText wifiSsidEditText;
    private EditText wifiPasswordEditText;
    private Button submitButton;
    private Button backButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private TextView connectionStatusTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hardware_setup);

        initializeViews();
        setupClickListeners();

        // Check if still connected to hardware AP
        checkHardwareConnection();
    }

    private void initializeViews() {
        wifiSsidEditText = findViewById(R.id.wifiSsidEditText);
        wifiPasswordEditText = findViewById(R.id.wifiPasswordEditText);
        submitButton = findViewById(R.id.submitButton);
        backButton = findViewById(R.id.backButton);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);

        progressBar.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitWiFiCredentials();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void checkHardwareConnection() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID();

            if (ssid != null) {
                ssid = ssid.replace("\"", "");
            }

            if (HARDWARE_AP_SSID.equals(ssid)) {
                connectionStatusTextView.setText("✓ Connected to " + HARDWARE_AP_SSID);
                connectionStatusTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                submitButton.setEnabled(true);
            } else {
                connectionStatusTextView.setText("✗ Not connected to hardware device");
                connectionStatusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                submitButton.setEnabled(false);

                showConnectionDialog();
            }
        }
    }

    private void showConnectionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Hardware Not Connected")
                .setMessage("Please connect to the PowerLogger WiFi network first:\n\n" +
                        "1. Go to WiFi settings\n" +
                        "2. Connect to 'PowerLogger' network\n" +
                        "3. Use password: admin123\n" +
                        "4. Return to this app")
                .setPositiveButton("OK", null)
                .setNegativeButton("Go Back", (dialog, which) -> finish())
                .show();
    }

    private void submitWiFiCredentials() {
        String wifiSsid = wifiSsidEditText.getText().toString().trim();
        String wifiPassword = wifiPasswordEditText.getText().toString().trim();

        if (wifiSsid.isEmpty()) {
            Toast.makeText(this, "Please enter WiFi SSID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (wifiPassword.isEmpty()) {
            Toast.makeText(this, "Please enter WiFi password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress and disable button
        progressBar.setVisibility(View.VISIBLE);
        submitButton.setEnabled(false);
        statusTextView.setText("Configuring hardware device...");

        // Send credentials to hardware device
        new SendCredentialsTask().execute(wifiSsid, wifiPassword);
    }

    private class SendCredentialsTask extends AsyncTask<String, Void, Boolean> {
        private String errorMessage = "";

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                String ssid = params[0];
                String password = params[1];

                // Build the URL with GET parameters to match ESP32 code
                // ESP32 expects: GET /set?user=SSID&pass=PASSWORD
                String urlString = "http://" + HARDWARE_IP + "/set?user=" +
                        URLEncoder.encode(ssid, "UTF-8") +
                        "&pass=" + URLEncoder.encode(password, "UTF-8");

                System.out.println("Sending GET request to ESP32: " + urlString);

                // Create HTTP connection
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Set request method to GET (not POST)
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000); // 10 seconds
                connection.setReadTimeout(10000); // 10 seconds

                // Get response
                int responseCode = connection.getResponseCode();
                System.out.println("Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String responseText = response.toString();
                    System.out.println("ESP32 Response: " + responseText);

                    // Check if response contains the success message from ESP32
                    return responseText.contains("Username and Password Updated") ||
                            responseText.contains("WiFi Config") ||
                            responseCode == 200;
                } else {
                    errorMessage = "HTTP Error: " + responseCode;
                    return false;
                }

            } catch (Exception e) {
                errorMessage = "Connection failed: " + e.getMessage();
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.GONE);
            submitButton.setEnabled(true);

            if (success) {
                statusTextView.setText("✓ Configuration sent successfully!");
                statusTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

                showSuccessDialog();
            } else {
                statusTextView.setText("✗ Failed to configure device");
                statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

                Toast.makeText(HardwareSetupActivity.this,
                        "Setup failed: " + errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Setup Complete!")
                .setMessage("WiFi credentials have been sent to the hardware device.\n\n" +
                        "Next steps:\n" +
                        "1. Manually restart the hardware device\n" +
                        "2. Wait for it to connect to your WiFi\n" +
                        "3. Connect your phone back to your home WiFi\n" +
                        "4. Return to the app and login")
                .setPositiveButton("Return to Login", (dialog, which) -> {
                    // Go back to login activity
                    Intent intent = new Intent(HardwareSetupActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkHardwareConnection();
    }
}