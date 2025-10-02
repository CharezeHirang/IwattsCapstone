package com.example.sampleiwatts;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.content.Intent;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.w3c.dom.Text;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class CostEstimationActivity extends AppCompatActivity {
    EditText etStartingDate, etEndingDate, etBatelecRate;
    TextView tvCostView, tvKwhView, tvElectricityRate, tvTotalUsage, tvDailyCost, tvArea1,tvArea2,tvArea3, tvProjectedText, tvProjectedCost, area1_name, area2_name, area3_name;
    BarChart barChart;
    LinearLayout popDaily, popArea;
    HorizontalBarChart areaChart;
    CardView daily_card, area_card;
    ImageView closeDaily, closeArea;
    private DatabaseReference db;
    private NavigationDrawerHelper drawerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cost_estimation);

        // Enable safe swipe navigation (root content view)
        try {
            View root = findViewById(android.R.id.content);
            com.example.sampleiwatts.managers.SwipeNavigationManager.enableSwipeNavigation(this, root);
            View scroll = findViewById(R.id.popDaily);
            if (scroll == null) scroll = findViewById(R.id.popArea);
            if (scroll != null) {
                com.example.sampleiwatts.managers.SwipeNavigationManager.enableSwipeNavigationForScrollView(this, scroll);
            }
            View main = findViewById(R.id.main);
            if (main != null) {
                com.example.sampleiwatts.managers.SwipeNavigationManager.enableSwipeNavigation(this, main);
            }
        } catch (Exception ignored) { }

        // Setup navigation drawer
        drawerHelper = new NavigationDrawerHelper(this);
        drawerHelper.setupNavigationDrawer();

        // Handle intent extras for navigation tracking
        Intent intent = getIntent();
        if (intent.getBooleanExtra("from_drawer", false)) {
            String sourceActivity = intent.getStringExtra("source_activity");
            drawerHelper.setReturnToActivity(sourceActivity);
        }
        LinearLayout buttonLayout = findViewById(R.id.button);
        barChart = findViewById(R.id.dailyCost_chart);
        areaChart = findViewById(R.id.area_chart);

        daily_card = findViewById(R.id.daily_card);
        popDaily = findViewById(R.id.popDaily);
        closeDaily = findViewById(R.id.closeDaily);
        daily_card.setOnClickListener(v -> {
            if (popDaily.getVisibility() == View.GONE) {
                popDaily.setVisibility(View.VISIBLE);
            } else {
                popDaily.setVisibility(View.GONE);
            }
        });
        closeDaily.setOnClickListener(v -> {
            popDaily.setVisibility(View.GONE);
        });

        closeArea = findViewById(R.id.closeArea);
        area_card = findViewById(R.id.area_card);
        popArea = findViewById(R.id.popArea);
        area_card.setOnClickListener( v->{
            if (popArea.getVisibility()==View.GONE){
                popArea.setVisibility(View.VISIBLE);
            } else{
                popArea.setVisibility(View.GONE);
            }
        });
        closeArea.setOnClickListener(v -> {
            popArea.setVisibility(View.GONE);
        });

        area1_name = findViewById(R.id.area1_name);
        area2_name = findViewById(R.id.area2_name);
        area3_name = findViewById(R.id.area3_name);
        tvProjectedCost = findViewById(R.id.tvProjectedCost);
        tvProjectedText = findViewById(R.id.tvProjectedText);
        tvArea1 = findViewById(R.id.tvArea1);
        tvArea2 = findViewById(R.id.tvArea2);
        tvArea3 = findViewById(R.id.tvArea3);
        tvDailyCost = findViewById(R.id.tvDailyCost);
        tvTotalUsage = findViewById(R.id.tvTotalUsage);
        tvCostView = findViewById(R.id.tvTotalCost);
        tvKwhView = findViewById(R.id.tvTotalKwh);
        etBatelecRate = findViewById(R.id.etBatelecRate);
        etBatelecRate.setOnClickListener(v -> {
            updateElectricityRate();
        });

        tvElectricityRate = findViewById(R.id.tvBatelecRate);
        db = FirebaseDatabase.getInstance().getReference();
        ButtonNavigator.setupButtons(this, buttonLayout);
        etStartingDate = findViewById(R.id.etStartingDate);
        etStartingDate.setOnClickListener(v -> {
            startingDate();
        });
        etEndingDate = findViewById(R.id.etEndingDate);
        etEndingDate.setOnClickListener(v -> {
            endingDate();
        });
        method();
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Reload all data when user returns to this screen
        method(); // This calls all your fetch methods
    }



    private  void method(){
        fetchFilterDates();
        fetchTotalCost();
        fetchTotalKwh();
        fetchElectricityRate();
        fetchTotalCostForDay();
        calculateCostForAllAreas();
        calculateProjectedMonthlyCost();
        fetchArea1Name();
        fetchArea2Name();
        fetchArea3Name();
        loadDailyCostChart();
        loadAreaCostChart();

    }
    private void startingDate() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format date (yyyy-MM-dd with leading zeros)
                    String date = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                            selectedYear, selectedMonth + 1, selectedDay);
                    etStartingDate.setText(date);

                    // Save to Firebase
                    DatabaseReference costFilterRef = db.child("cost_filter_date");
                    costFilterRef.child("starting_date").setValue(date)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(CostEstimationActivity.this, "Starting date saved", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(CostEstimationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });

                    // After selecting the starting date, check if the ending date is valid
                    validateEndingDate(date);
                },
                year, month, day
        );

        // Default max date is today
        long maxDate = System.currentTimeMillis();

        // If ending date is already selected, make sure start date <= ending date
        String endingDateStr = etEndingDate.getText().toString().trim();
        if (!endingDateStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date endingDate = sdf.parse(endingDateStr);
                if (endingDate != null && endingDate.getTime() < maxDate) {
                    maxDate = endingDate.getTime();
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        datePickerDialog.getDatePicker().setMaxDate(maxDate);
        datePickerDialog.show();
    }
    private void validateEndingDate(String startingDateStr) {
        String endingDateStr = etEndingDate.getText().toString().trim();
        if (!startingDateStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date startingDate = sdf.parse(startingDateStr);
                Date endingDate = endingDateStr.isEmpty() ? null : sdf.parse(endingDateStr);

                if (startingDate != null) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(startingDate);
                    calendar.add(Calendar.DAY_OF_MONTH, 30);  // start + 30 days = 31-day inclusive window
                    Date validEndingDate = calendar.getTime();

                    // Case 1: No ending date yet → set to start+30 (inclusive 31 days)
                    if (endingDate == null) {
                        String newEnd = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(validEndingDate);
                        etEndingDate.setText(newEnd);
                        DatabaseReference costFilterRef = db.child("cost_filter_date");
                        costFilterRef.child("ending_date").setValue(newEnd)
                                .addOnSuccessListener(aVoid -> Toast.makeText(CostEstimationActivity.this, "Ending date set to 31-day window", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(CostEstimationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        return;
                    }

                    // Case 2: Ending date exists but exceeds 31-day window → clamp to start+30
                    if (endingDate.after(validEndingDate)) {
                        String newEnd = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(validEndingDate);
                        etEndingDate.setText(newEnd);
                        DatabaseReference costFilterRef = db.child("cost_filter_date");
                        costFilterRef.child("ending_date").setValue(newEnd)
                                .addOnSuccessListener(aVoid -> Toast.makeText(CostEstimationActivity.this, "Ending date adjusted to 31 days from the new starting date", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(CostEstimationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }
    private void endingDate() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format date (yyyy-MM-dd with leading zeros)
                    String date = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                            selectedYear, selectedMonth + 1, selectedDay);

                    // Validate against starting date
                    String startingDateStr = etStartingDate.getText().toString().trim();
                    if (!startingDateStr.isEmpty()) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            Date startingDate = sdf.parse(startingDateStr);
                            Date pickedDate = sdf.parse(date);

                            if (startingDate != null && pickedDate != null && pickedDate.before(startingDate)) {
                                Toast.makeText(CostEstimationActivity.this, "Ending date cannot be earlier than starting date", Toast.LENGTH_SHORT).show();
                                return; // invalid → don’t save
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }

                    etEndingDate.setText(date);

                    // Save to Firebase
                    DatabaseReference costFilterRef = db.child("cost_filter_date");
                    costFilterRef.child("ending_date").setValue(date)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(CostEstimationActivity.this, "Ending date saved", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(CostEstimationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                },
                year, month, day
        );

        // 🚀 Ending date must be today or later
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());

        // If starting date exists, force ending_date within [start, start+30]
        String startingDateStr = etStartingDate.getText().toString().trim();
        if (!startingDateStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date startingDate = sdf.parse(startingDateStr);
                if (startingDate != null) {
                    // Set minimum date for ending date as starting date
                    datePickerDialog.getDatePicker().setMinDate(startingDate.getTime());

                    // Calculate the maximum allowed ending date (31 days after the starting date)
                    Calendar maxEndDateCalendar = Calendar.getInstance();
                    maxEndDateCalendar.setTime(startingDate);
                    maxEndDateCalendar.add(Calendar.DAY_OF_MONTH, 30); // start + 30 days (inclusive 31)

                    datePickerDialog.getDatePicker().setMaxDate(maxEndDateCalendar.getTimeInMillis());
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        datePickerDialog.show();
    }
    private void fetchFilterDates() {
        // Reference to the "cost_filter_date" node
        DatabaseReference filterDateRef = db.child("cost_filter_date");

        filterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get starting_date
                String startingDate = dataSnapshot.child("starting_date").getValue(String.class);
                // Get ending_date
                String endingDate = dataSnapshot.child("ending_date").getValue(String.class);

                if (startingDate != null) {
                    etStartingDate.setText(startingDate); // your TextView for starting date
                } else {
                    etStartingDate.setText("Not set");
                }

                if (endingDate != null) {
                    etEndingDate.setText(endingDate); // your TextView for ending date
                } else {
                    etEndingDate.setText("Not set");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching dates", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchTotalCost() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                if (startingDateString == null || endingDateString == null) {
                    tvCostView.setText("₱ 0.00");
                    return;
                }

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    // Normalize dates to midnight to avoid timezone issues
                    java.util.Calendar calStart = java.util.Calendar.getInstance();
                    calStart.setTime(startingDate);
                    calStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    calStart.set(java.util.Calendar.MINUTE, 0);
                    calStart.set(java.util.Calendar.SECOND, 0);
                    calStart.set(java.util.Calendar.MILLISECOND, 0);

                    java.util.Calendar calEnd = java.util.Calendar.getInstance();
                    calEnd.setTime(endingDate);
                    calEnd.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    calEnd.set(java.util.Calendar.MINUTE, 0);
                    calEnd.set(java.util.Calendar.SECOND, 0);
                    calEnd.set(java.util.Calendar.MILLISECOND, 0);

                    Date finalStartDate = new Date(calStart.getTimeInMillis());
                    Date finalEndDate = new Date(calEnd.getTimeInMillis());

                    // Fetch hourly summaries and calculate total cost
                    DatabaseReference hourlySummariesRef = db.child("hourly_summaries");
                    hourlySummariesRef.orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double totalCost = 0.0;

                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    continue; // Skip invalid dates
                                }

                                // Check if date is within range (inclusive)
                                if (currentDate != null && !currentDate.before(finalStartDate) && !currentDate.after(finalEndDate)) {
                                    // Sum all hourly costs for this date
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyCost = hourSnapshot.child("total_cost").getValue(Double.class);
                                        if (hourlyCost != null) {
                                            totalCost += hourlyCost;
                                        }
                                    }
                                }
                            }

                            // Display the total cost
                            String formattedCost = String.format("%.2f", totalCost);
                            tvCostView.setText("₱ " + formattedCost);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Toast.makeText(CostEstimationActivity.this, "Error fetching hourly summaries", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    tvCostView.setText("₱ 0.00");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching date filter", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchTotalKwh() {
        // Reference to the cost_filter_date to get the starting and ending dates
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Fetch the starting and ending dates from Firebase
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                // Log the fetched data for debugging
                Log.d("CostEstimation", "Starting Date: " + startingDateString);
                Log.d("CostEstimation", "Ending Date: " + endingDateString);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());  // Adjust as per your Firebase format
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    Log.d("CostEstimation", "Parsed Starting Date: " + startingDate);
                    Log.d("CostEstimation", "Parsed Ending Date: " + endingDate);

                    // Now, fetch the hourly summaries within the date range
                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double cumulativeKwh = 0;

                            // Loop through the hourly summaries to accumulate the total kWh
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                Log.d("CostEstimation", "Processing Date: " + dateKey);
                                Log.d("CostEstimation", "Current Date: " + currentDate);

                                // Check if the current date is within the range of starting and ending dates
                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                    // Loop through the hourly data for the current date
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyKwh = hourSnapshot.child("total_kwh").getValue(Double.class);

                                        if (hourlyKwh != null) {
                                            Log.d("CostEstimation", "Hourly KWh for " + dateKey + ": " + hourlyKwh);
                                            cumulativeKwh += hourlyKwh;
                                        } else {
                                            Log.d("CostEstimation", "No hourly kWh for " + dateKey);
                                        }
                                    }
                                }
                            }

                            // Format and display the total kWh value
                            String formattedKwh = String.format("%.3f", cumulativeKwh);
                            Log.d("KwhTotal", "Total KWh: " + formattedKwh);
                            tvKwhView.setText(formattedKwh + " kwh");
                            tvTotalUsage.setText(formattedKwh);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void fetchElectricityRate() {
        DatabaseReference electricityRateRef = db.child("system_settings").child("electricity_rate_per_kwh");
        electricityRateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get the value from the database
                Object value = dataSnapshot.getValue();

                // Check if the value is a numeric type
                if (value instanceof Number) {
                    // Cast the value to a Number and then to a Double
                    Double electricityRatePerKwh = ((Number) value).doubleValue();

                    // Format the rate to 2 decimal places
                    String formattedRate = String.format("%.2f", electricityRatePerKwh);

                    // Update the UI
                    tvElectricityRate.setText("₱ " + formattedRate + " / kWh");
                    etBatelecRate.setText(formattedRate);
                } else {
                    // If the value is not a number, show the "not available" message
                    tvElectricityRate.setText("Electricity rate not available");
                    etBatelecRate.setText("Electricity rate not available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle database error
                Toast.makeText(CostEstimationActivity.this, "Error fetching electricity rate", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void updateElectricityRate() {
        String updateElectricityRate = etBatelecRate.getText().toString().trim();

        // Ensure the rate is not empty
        if (updateElectricityRate.isEmpty()) {
            Toast.makeText(CostEstimationActivity.this, "Electricity Rate cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Parse the rate as a double
            double rate = Double.parseDouble(updateElectricityRate);

            // Save the rate as a double to Firebase
            DatabaseReference deviceRef = db.child("system_settings");
            deviceRef.child("electricity_rate_per_kwh").setValue(rate)
                    .addOnSuccessListener(aVoid -> {
                        // Clear focus after successful update
                        etBatelecRate.clearFocus();
                        // Successfully updated the rate
                        Toast.makeText(CostEstimationActivity.this, "Electricity Rate updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        // Handle the error
                        Toast.makeText(CostEstimationActivity.this, "Error updating Electricity Rate", Toast.LENGTH_SHORT).show();
                    });
        } catch (NumberFormatException e) {
            // Handle the case where the input is not a valid number
            Toast.makeText(CostEstimationActivity.this, "Invalid Electricity Rate format", Toast.LENGTH_SHORT).show();
        }
    }
    private void fetchTotalCostForDay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        DatabaseReference hourlySummariesRef = db.child("hourly_summaries").child(currentDate);
        hourlySummariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                double totalCost = 0.0;
                for (DataSnapshot hourlySnapshot : dataSnapshot.getChildren()) {
                    Object value = hourlySnapshot.child("total_cost").getValue();
                    if (value != null) {
                        totalCost += ((Number) value).doubleValue();
                    }
                }
                tvDailyCost.setText(String.format("%.2f", totalCost));
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching data", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void calculateCostForAllAreas() {
        DatabaseReference systemSettingsRef = db.child("system_settings");
        DatabaseReference hourlySummariesRef = db.child("hourly_summaries");
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        // First, fetch the date filter (starting & ending)
        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    // Fetch the electricity rate from system_settings
                    systemSettingsRef.child("electricity_rate_per_kwh").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            Double electricityRatePerKwh = dataSnapshot.getValue(Double.class);

                            if (electricityRatePerKwh == null) {
                                Toast.makeText(CostEstimationActivity.this, "Electricity rate is not available", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            final double[] totalArea1Kwh = {0};
                            final double[] totalArea2Kwh = {0};
                            final double[] totalArea3Kwh = {0};

                            hourlySummariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                        String dateKey = dateSnapshot.getKey();
                                        Date currentDate = null;
                                        try {
                                            currentDate = dateFormatter.parse(dateKey);
                                        } catch (ParseException e) {
                                            Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                        }

                                        // ✅ Only process if within starting & ending range
                                        if (currentDate != null &&
                                                (currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                                (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                            for (DataSnapshot hourlySnapshot : dateSnapshot.getChildren()) {
                                                Double area1Kwh = hourlySnapshot.child("area1_kwh").getValue(Double.class);
                                                Double area2Kwh = hourlySnapshot.child("area2_kwh").getValue(Double.class);
                                                Double area3Kwh = hourlySnapshot.child("area3_kwh").getValue(Double.class);

                                                area1Kwh = (area1Kwh == null) ? 0 : area1Kwh;
                                                area2Kwh = (area2Kwh == null) ? 0 : area2Kwh;
                                                area3Kwh = (area3Kwh == null) ? 0 : area3Kwh;

                                                totalArea1Kwh[0] += area1Kwh;
                                                totalArea2Kwh[0] += area2Kwh;
                                                totalArea3Kwh[0] += area3Kwh;
                                            }
                                        }
                                    }

                                    double totalConsumption = totalArea1Kwh[0] + totalArea2Kwh[0] + totalArea3Kwh[0];

                                    double percentageArea1 = (totalConsumption == 0) ? 0 : (totalArea1Kwh[0] / totalConsumption) * 100;
                                    double percentageArea2 = (totalConsumption == 0) ? 0 : (totalArea2Kwh[0] / totalConsumption) * 100;
                                    double percentageArea3 = (totalConsumption == 0) ? 0 : (totalArea3Kwh[0] / totalConsumption) * 100;

                                    double totalCostArea1 = totalArea1Kwh[0] * electricityRatePerKwh;
                                    double totalCostArea2 = totalArea2Kwh[0] * electricityRatePerKwh;
                                    double totalCostArea3 = totalArea3Kwh[0] * electricityRatePerKwh;

                                    tvArea1.setText("₱ " + String.format("%.2f", totalCostArea1) + " (" + String.format("%.2f", percentageArea1) + "%)");
                                    tvArea2.setText("₱ " + String.format("%.2f", totalCostArea2) + " (" + String.format("%.2f", percentageArea2) + "%)");
                                    tvArea3.setText("₱ " + String.format("%.2f", totalCostArea3) + " (" + String.format("%.2f", percentageArea3) + "%)");
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {
                                    Toast.makeText(CostEstimationActivity.this, "Error fetching hourly data", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Toast.makeText(CostEstimationActivity.this, "Error fetching electricity rate", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (ParseException e) {
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void calculateProjectedMonthlyCost() {
        // Reference to the cost_filter_date in Firebase to get the starting and ending dates
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString   = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate   = endingDateString != null ? dateFormatter.parse(endingDateString) : null;

                    if (startingDate == null) return;
                    if (endingDate == null) endingDate = new Date();

                    // Normalize both dates to midnight to avoid timezone/hour offsets
                    java.util.Calendar calStart = java.util.Calendar.getInstance();
                    calStart.setTime(startingDate);
                    calStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    calStart.set(java.util.Calendar.MINUTE, 0);
                    calStart.set(java.util.Calendar.SECOND, 0);
                    calStart.set(java.util.Calendar.MILLISECOND, 0);

                    java.util.Calendar calEnd = java.util.Calendar.getInstance();
                    calEnd.setTime(endingDate);
                    calEnd.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    calEnd.set(java.util.Calendar.MINUTE, 0);
                    calEnd.set(java.util.Calendar.SECOND, 0);
                    calEnd.set(java.util.Calendar.MILLISECOND, 0);

                    long startMs = calStart.getTimeInMillis();
                    long endMs   = calEnd.getTimeInMillis();
                    if (endMs < startMs) {
                        long t = startMs; startMs = endMs; endMs = t;
                    }

                    long daysBetween = TimeUnit.MILLISECONDS.toDays(endMs - startMs) + 1; // inclusive
                    if (daysBetween <= 0) daysBetween = 1;

                    Date finalStartDate = new Date(startMs);
                    Date finalEndDate = new Date(endMs);

                    // Fetch the hourly cost data within [start, end]
                    DatabaseReference hourlySummariesRef = db.child("hourly_summaries");
                    final double[] cumulativeCost = {0};

                    long finalDaysBetween = daysBetween;
                    hourlySummariesRef.orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            cumulativeCost[0] = 0;
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }

                                if (currentDate != null && !currentDate.before(finalStartDate) && !currentDate.after(finalEndDate)) {
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyCost = hourSnapshot.child("total_cost").getValue(Double.class);
                                        if (hourlyCost != null) cumulativeCost[0] += hourlyCost;
                                    }
                                }
                            }

                            double dailyAverageCost = cumulativeCost[0] / (double) finalDaysBetween;
                            // Project to a fixed month length (30 days) based on the current pattern
                            long projectionDays = 31;
                            double projectedMonthlyCost = dailyAverageCost * (double) projectionDays;

                            String formattedCost = String.format("%.2f", projectedMonthlyCost);
                            tvProjectedCost.setText("₱ " + formattedCost);
                            tvProjectedText.setText("Based on the current " + finalDaysBetween + "-day consumption pattern");
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Toast.makeText(CostEstimationActivity.this, "Error fetching hourly summaries", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Toast.makeText(CostEstimationActivity.this, "Error parsing dates", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching cost filter data", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchArea1Name() {
        DatabaseReference systemSettingsRef = db.child("system_settings");

        systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String area1Name = dataSnapshot.child("area1_name").getValue(String.class);
                if (area1Name != null) {
                    area1_name.setText(area1Name);
                } else {
                    area1_name.setText("Not set");
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching area1 name", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchArea2Name() {
        DatabaseReference systemSettingsRef = db.child("system_settings");

        systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String area1Name = dataSnapshot.child("area2_name").getValue(String.class);
                if (area1Name != null) {
                    area2_name.setText(area1Name);
                } else {
                    area2_name.setText("Not set");
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching area2 name", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchArea3Name() {
        DatabaseReference systemSettingsRef = db.child("system_settings");

        systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String area1Name = dataSnapshot.child("area3_name").getValue(String.class);
                if (area1Name != null) {
                    area3_name.setText(area1Name);
                } else {
                    area3_name.setText("Not set");
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching area3 name", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void loadDailyCostChart() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");
        DatabaseReference dailySummariesRef = db.child("daily_summaries");
        DatabaseReference systemSettingsRef = db.child("system_settings"); // Reference to system_settings

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String startingDateString = snapshot.child("starting_date").getValue(String.class);
                String endingDateString   = snapshot.child("ending_date").getValue(String.class);

                final SimpleDateFormat keyFormatter   = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                final SimpleDateFormat labelFormatter = new SimpleDateFormat("MM-dd", Locale.getDefault());

                try {
                    final Date startingDate = keyFormatter.parse(startingDateString);
                    final Date endingDate   = keyFormatter.parse(endingDateString);

                    systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot settingsSnapshot) {

                            final String area1Name = settingsSnapshot.child("area1_name").getValue(String.class);
                            final String area2Name = settingsSnapshot.child("area2_name").getValue(String.class);
                            final String area3Name = settingsSnapshot.child("area3_name").getValue(String.class);

                            final String finalArea1Name = (area1Name != null) ? area1Name : "Area 1";
                            final String finalArea2Name = (area2Name != null) ? area2Name : "Area 2";
                            final String finalArea3Name = (area3Name != null) ? area3Name : "Area 3";

                            dailySummariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    TreeMap<Long, float[]> perDayCosts = new TreeMap<>();

                                    for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                        String dateKey = dateSnapshot.getKey();
                                        Date currentDate;
                                        try {
                                            currentDate = keyFormatter.parse(dateKey);
                                        } catch (ParseException e) { continue; }
                                        if (currentDate == null) continue;

                                        boolean inRange =
                                                (currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                                        (currentDate.equals(endingDate)   || currentDate.before(endingDate));
                                        if (!inRange) continue;

                                        Double c1 = dateSnapshot.child("area_breakdown/area1/cost").getValue(Double.class);
                                        Double c2 = dateSnapshot.child("area_breakdown/area2/cost").getValue(Double.class);
                                        Double c3 = dateSnapshot.child("area_breakdown/area3/cost").getValue(Double.class);

                                        float cost1 = c1 != null ? c1.floatValue() : 0f;
                                        float cost2 = c2 != null ? c2.floatValue() : 0f;
                                        float cost3 = c3 != null ? c3.floatValue() : 0f;
                                        perDayCosts.put(currentDate.getTime(), new float[]{cost1, cost2, cost3});

                                    }

                                    List<BarEntry> entries = new ArrayList<>();
                                    List<String> xLabels = new ArrayList<>();
                                    int i = 0;
                                    for (Map.Entry<Long, float[]> e : perDayCosts.entrySet()) {
                                        entries.add(new BarEntry(i, e.getValue()));
                                        xLabels.add(labelFormatter.format(new Date(e.getKey())));
                                        i++;
                                    }

                                    BarDataSet dataSet = new BarDataSet(entries, "");
                                    dataSet.setColors(new int[]{
                                            Color.YELLOW,
                                            Color.RED,
                                            Color.rgb(255, 215, 0)
                                    });
                                    dataSet.setStackLabels(new String[]{finalArea1Name, finalArea2Name, finalArea3Name});

                                    dataSet.setDrawValues(true);
                                    dataSet.setValueTextSize(10f);
                                    dataSet.setValueTextColor(getResources().getColor(R.color.brown));

                                    dataSet.setValueFormatter(new ValueFormatter() {
                                        @Override
                                        public String getBarLabel(BarEntry barEntry) {
                                            float total = 0;
                                            if (barEntry.getYVals() != null) {
                                                for (float v : barEntry.getYVals()) {
                                                    total += v;
                                                }
                                            } else {
                                                total = barEntry.getY();
                                            }
                                            return total < 5f ? "" : String.format("%.0f", total);
                                        }
                                    });

                                    BarData barData = new BarData(dataSet);
                                    barData.setBarWidth(0.5f);

                                    barChart.setData(barData);

                                    XAxis xAxis = barChart.getXAxis();
                                    xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
                                    xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                    xAxis.setGranularity(1f);
                                    xAxis.setLabelRotationAngle(0f);

                                    xAxis.setDrawGridLines(true);
                                    barChart.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
                                    xAxis.setTextColor(getResources().getColor(R.color.brown));
                                    barChart.getAxisRight().setEnabled(false);
                                    barChart.getAxisRight().setTextColor(getResources().getColor(R.color.brown));
                                    barChart.getAxisLeft().setDrawGridLines(true);

                                    barChart.getAxisLeft().setAxisMinimum(0f);
                                    barChart.getDescription().setEnabled(false);
                                    barChart.getAxisRight().setEnabled(false);
                                    barChart.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
                                    barChart.getXAxis().setGridColor(getResources().getColor(R.color.brown));
                                    barChart.setExtraOffsets(10, 10, 10, 20);

                                    Legend legend = barChart.getLegend();
                                    legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
                                    legend.setForm(Legend.LegendForm.SQUARE);
                                    legend.setTextColor(getResources().getColor(R.color.brown));

                                    barChart.setExtraBottomOffset(10f);

                                    barChart.animateY(800);
                                    barChart.invalidate();
                                }

                                @Override public void onCancelled(DatabaseError error) { }
                            });
                        }

                        @Override public void onCancelled(DatabaseError error) { }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            @Override public void onCancelled(DatabaseError error) { }
        });
    }
    private void loadAreaCostChart() {
        DatabaseReference systemSettingsRef = db.child("system_settings");
        DatabaseReference hourlySummariesRef = db.child("hourly_summaries");
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String startingDateString = snapshot.child("starting_date").getValue(String.class);
                String endingDateString = snapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    // Fetch settings (rate + names)
                    systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot settingsSnapshot) {
                            Double electricityRatePerKwh = settingsSnapshot.child("electricity_rate_per_kwh").getValue(Double.class);
                            String area1Name = settingsSnapshot.child("area1_name").getValue(String.class);
                            String area2Name = settingsSnapshot.child("area2_name").getValue(String.class);
                            String area3Name = settingsSnapshot.child("area3_name").getValue(String.class);

                            if (electricityRatePerKwh == null) {
                                Toast.makeText(CostEstimationActivity.this, "Electricity rate not available", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            final double[] totalArea1Kwh = {0};
                            final double[] totalArea2Kwh = {0};
                            final double[] totalArea3Kwh = {0};

                            hourlySummariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                        String dateKey = dateSnapshot.getKey();
                                        Date currentDate = null;
                                        try {
                                            currentDate = dateFormatter.parse(dateKey);
                                        } catch (ParseException e) {
                                            Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                        }

                                        if (currentDate != null &&
                                                (currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                                (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                            for (DataSnapshot hourlySnapshot : dateSnapshot.getChildren()) {
                                                Double area1Kwh = hourlySnapshot.child("area1_kwh").getValue(Double.class);
                                                Double area2Kwh = hourlySnapshot.child("area2_kwh").getValue(Double.class);
                                                Double area3Kwh = hourlySnapshot.child("area3_kwh").getValue(Double.class);

                                                totalArea1Kwh[0] += (area1Kwh == null ? 0 : area1Kwh);
                                                totalArea2Kwh[0] += (area2Kwh == null ? 0 : area2Kwh);
                                                totalArea3Kwh[0] += (area3Kwh == null ? 0 : area3Kwh);
                                            }
                                        }
                                    }

                                    // Convert to cost
                                    double totalCostArea1 = totalArea1Kwh[0] * electricityRatePerKwh;
                                    double totalCostArea2 = totalArea2Kwh[0] * electricityRatePerKwh;
                                    double totalCostArea3 = totalArea3Kwh[0] * electricityRatePerKwh;

                                    // ✅ Prepare Bar Entries (each cost as separate entry)
                                    ArrayList<BarEntry> entries = new ArrayList<>();
                                    entries.add(new BarEntry(0, (float) totalCostArea1));
                                    entries.add(new BarEntry(1, (float) totalCostArea2));
                                    entries.add(new BarEntry(2, (float) totalCostArea3));

                                    BarDataSet dataSet = new BarDataSet(entries, "Total Cost (₱)");
                                    dataSet.setColors(new int[]{
                                            Color.YELLOW,   // area1
                                            Color.RED,      // area2
                                            0xFFFFD700      // gold (hex color)
                                    });
                                    dataSet.setValueTextSize(14f);

                                    BarData barData = new BarData(dataSet);
                                    barData.setBarWidth(0.6f);

                                    // ✅ Setup Horizontal Chart
                                    areaChart.setData(barData);
                                    areaChart.getDescription().setEnabled(false);

                                    // Y-axis = area names
                                    XAxis xAxis = areaChart.getXAxis();
                                    xAxis.setGranularity(1f);
                                    xAxis.setLabelRotationAngle(90f);
                                    xAxis.setValueFormatter(new IndexAxisValueFormatter(
                                            new String[]{area1Name, area2Name, area3Name}
                                    ));
                                    xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                    xAxis.setDrawGridLines(false);

                                    // Left axis = cost values
                                    YAxis leftAxis = areaChart.getAxisLeft();
                                    leftAxis.setDrawGridLines(true);
                                    leftAxis.setAxisMinimum(0f);

                                    // Hide right axis
                                    areaChart.getAxisRight().setEnabled(false);

                                    areaChart.animateY(1500);
                                    areaChart.invalidate();
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    Toast.makeText(CostEstimationActivity.this, "Error fetching hourly data", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            Toast.makeText(CostEstimationActivity.this, "Error fetching settings", Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (ParseException e) {
                    Log.e("CostEstimation", "Date parse error: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(CostEstimationActivity.this, "Error fetching cost filter date", Toast.LENGTH_SHORT).show();
            }
        });
    }









}