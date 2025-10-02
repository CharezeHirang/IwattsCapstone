package com.example.sampleiwatts;

import android.os.Bundle;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.view.View;
import android.graphics.Color;
import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NotificationActivity extends AppCompatActivity {

    private java.util.Set<String> readAlerts = new java.util.HashSet<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        
        // Back arrow behavior: return to previous screen
        ImageView backIcon = findViewById(R.id.back_icon);
        if (backIcon != null) {
            backIcon.setOnClickListener(v -> onBackPressed());
        }

        // Get the notification container from XML layout
        LinearLayout container = findViewById(R.id.notificationContainer);
        
        // Setup button click listeners
        setupButtonListeners();

        // If a single alert was passed, add it on top first
        String title = getIntent().getStringExtra("alert_title");
        String message = getIntent().getStringExtra("alert_message");
        String time = getIntent().getStringExtra("alert_time");
        if (title != null || message != null || time != null) {
            container.addView(buildAlertView(container, title, message, time, false));
        }

        // Then stream recent alerts from Firebase to show multiple entries
        DatabaseReference alertsRef = FirebaseDatabase.getInstance().getReference().child("alerts");
        alertsRef.limitToLast(50).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                container.removeAllViews();
                // Create a list to reverse the order (latest first)
                java.util.List<DataSnapshot> alertsList = new java.util.ArrayList<>();
                for (DataSnapshot alert : snapshot.getChildren()) {
                    alertsList.add(alert);
                }
                // Reverse the list to show latest notifications first
                java.util.Collections.reverse(alertsList);
                
                for (DataSnapshot alert : alertsList) {
                    String t = valueAsString(alert.child("title").getValue());
                    String m = valueAsString(alert.child("message").getValue());
                    String ti = valueAsString(alert.child("time").getValue());
                    Boolean isRead = alert.child("read").getValue(Boolean.class);
                    Boolean isDeleted = alert.child("delete").getValue(Boolean.class);
                    
                    // Only show alerts that are not deleted
                    if (isDeleted == null || !isDeleted) {
                        container.addView(buildAlertView(container, t, m, ti, isRead != null ? isRead : false));
                    }
                }
                // If there was a direct intent message and it's not in DB yet, keep it visible
        if (title != null || message != null || time != null) {
            container.addView(buildAlertView(container, title, message, time, false), 0);
        }
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private CardView buildAlertView(ViewGroup parent, String title, String message, String time, boolean isRead) {
        // Create unique identifier for this alert
        String alertId = title + "_" + time;
        
        // Create CardView
        CardView cardView = new CardView(this);
        cardView.setCardElevation(8);
        cardView.setRadius(16);
        
        // Set background color based on alert title
        String backgroundColor;
        if (title != null && title.contains("System Updates")) {
            backgroundColor = "#FFFFFF"; // White for System Updates
        } else if (title != null && title.contains("Threshold Alert")) {
            backgroundColor = "#FEF9E7"; // Light yellow for Threshold Alert
        } else if (title != null && (title.contains("Voltage Fluctuation") || 
                                    title.contains("Threshold Reached") || 
                                    title.contains("Threshold Exceeded"))) {
            backgroundColor = "#F3E787"; // Yellow for Voltage Fluctuation, threshold reached, and threshold exceeded
        } else {
            backgroundColor = "#FFFFFF"; // Default white
        }
        cardView.setCardBackgroundColor(Color.parseColor(backgroundColor));
        
        // Set transparency based on read status
        if (isRead) {
            cardView.setAlpha(0.7f);
            readAlerts.add(alertId);
        }
        
        // Add click listener to the card for reading the alert
        cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAlertDetails(title, message, time);
                readAlerts.add(alertId);
                // Mark as read by changing opacity or adding a visual indicator
                cardView.setAlpha(0.7f);
                // Update database to mark as read
                updateAlertAsRead(alertId);
            }
        });
        
        // Create main LinearLayout inside CardView
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        mainLayout.setPadding(pad, pad, pad, pad);
        
        // Create left LinearLayout for content
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        
        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title != null ? title : "Notification");
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvTitle.setTextColor(Color.parseColor("#863B17")); // Brown color
        contentLayout.addView(tvTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Message
        TextView tvMessage = new TextView(this);
        tvMessage.setText(message != null ? message : "");
        tvMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvMessage.setTextColor(Color.parseColor("#863B17")); // Brown color
        LinearLayout.LayoutParams lpMessage = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpMessage.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        contentLayout.addView(tvMessage, lpMessage);

        // Time
        TextView tvTime = new TextView(this);
        tvTime.setText(time != null ? time : "");
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvTime.setTextColor(Color.parseColor("#863B17")); // Brown color
        LinearLayout.LayoutParams lpTime = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTime.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        contentLayout.addView(tvTime, lpTime);
        
        // Add content layout to main layout
        mainLayout.addView(contentLayout);
        
        // Create delete icon
        ImageView deleteIcon = new ImageView(this);
        deleteIcon.setImageResource(R.drawable.ic_delete); // Make sure you have this drawable
        deleteIcon.setColorFilter(Color.parseColor("#863B17")); // Brown color
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(
            (int) (24 * getResources().getDisplayMetrics().density),
            (int) (24 * getResources().getDisplayMetrics().density)
        );
        lpIcon.gravity = android.view.Gravity.CENTER_VERTICAL;
        deleteIcon.setLayoutParams(lpIcon);
        
        // Add click listener to delete icon
        deleteIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteConfirmation(cardView, title, alertId);
            }
        });
        
        // Add delete icon to main layout
        mainLayout.addView(deleteIcon);
        
        // Add main layout to CardView
        cardView.addView(mainLayout);
        
        // Card spacing and margins
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCard.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
        lpCard.leftMargin = (int) (8 * getResources().getDisplayMetrics().density);
        lpCard.rightMargin = (int) (8 * getResources().getDisplayMetrics().density);
        lpCard.topMargin = (int) (5 * getResources().getDisplayMetrics().density);
        cardView.setLayoutParams(lpCard);

        return cardView;
    }

    private String valueAsString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
    
    private void showAlertDetails(String title, String message, String time) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title != null ? title : "Alert Details");
        builder.setMessage("Message: " + (message != null ? message : "No message") + 
                         "\n\nTime: " + (time != null ? time : "No time"));
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Style the OK button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(Color.parseColor("#863B17"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
    }
    
    private void showDeleteConfirmation(CardView cardView, String title, String alertId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Alert");
        builder.setMessage("Are you sure you want to delete this alert?\n\n" + 
                         (title != null ? title : "Alert"));
        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Update database to mark as deleted
                updateAlertAsDeleted(alertId);
                // Remove the card from its parent
                ViewGroup parent = (ViewGroup) cardView.getParent();
                if (parent != null) {
                    parent.removeView(cardView);
                }
                // Remove from read alerts set
                readAlerts.remove(alertId);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(Color.parseColor("#863B17"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(Color.parseColor("#CCCCCC"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#863B17"));
    }
    
    private void updateAlertAsRead(String alertId) {
        // Find the alert in Firebase and update its read status
        DatabaseReference alertsRef = FirebaseDatabase.getInstance().getReference().child("alerts");
        alertsRef.orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot alert : snapshot.getChildren()) {
                    String title = valueAsString(alert.child("title").getValue());
                    String time = valueAsString(alert.child("time").getValue());
                    String currentAlertId = title + "_" + time;
                    
                    if (currentAlertId.equals(alertId)) {
                        // Found the alert, update its read status
                        alert.getRef().child("read").setValue(true);
                        break;
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("NotificationActivity", "Error updating read status: " + error.getMessage());
            }
        });
    }
    
    private void updateAlertAsDeleted(String alertId) {
        // Find the alert in Firebase and update its delete status
        DatabaseReference alertsRef = FirebaseDatabase.getInstance().getReference().child("alerts");
        alertsRef.orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot alert : snapshot.getChildren()) {
                    String title = valueAsString(alert.child("title").getValue());
                    String time = valueAsString(alert.child("time").getValue());
                    String currentAlertId = title + "_" + time;
                    
                    if (currentAlertId.equals(alertId)) {
                        // Found the alert, update its delete status
                        alert.getRef().child("delete").setValue(true);
                        break;
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("NotificationActivity", "Error updating delete status: " + error.getMessage());
            }
        });
    }
    
    private void setupButtonListeners() {
        // Mark all as read text view
        TextView btnMarkAll = findViewById(R.id.btnMarkAll);
        if (btnMarkAll != null) {
            btnMarkAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMarkAllConfirmation();
                }
            });
        }
        
        // Clear all text view
        TextView btnClearAll = findViewById(R.id.btnClearAll);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showClearAllConfirmation();
                }
            });
        }
    }
    
    private void showMarkAllConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mark All as Read");
        builder.setMessage("Are you sure you want to mark all notifications as read?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                markAllAsRead();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(Color.parseColor("#863B17"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(Color.parseColor("#CCCCCC"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#863B17"));
    }
    
    private void showClearAllConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Clear All Notifications");
        builder.setMessage("Are you sure you want to delete all notifications? This action cannot be undone.");
        builder.setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                clearAllNotifications();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundColor(Color.parseColor("#863B17"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(Color.parseColor("#CCCCCC"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#863B17"));
    }
    
    private void markAllAsRead() {
        DatabaseReference alertsRef = FirebaseDatabase.getInstance().getReference().child("alerts");
        alertsRef.orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot alert : snapshot.getChildren()) {
                    Boolean isDeleted = alert.child("delete").getValue(Boolean.class);
                    // Only mark as read if not deleted
                    if (isDeleted == null || !isDeleted) {
                        alert.getRef().child("read").setValue(true);
                    }
                }
                // Refresh the UI to show updated read status
                recreate();
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("NotificationActivity", "Error marking all as read: " + error.getMessage());
            }
        });
    }
    
    private void clearAllNotifications() {
        DatabaseReference alertsRef = FirebaseDatabase.getInstance().getReference().child("alerts");
        alertsRef.orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot alert : snapshot.getChildren()) {
                    // Mark all alerts as deleted
                    alert.getRef().child("delete").setValue(true);
                }
                // Refresh the UI to hide deleted alerts
                recreate();
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("NotificationActivity", "Error clearing all notifications: " + error.getMessage());
            }
        });
    }
}