package com.example.sampleiwatts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "fcm_alerts_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "🔔 FCM Message received!");
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Message ID: " + remoteMessage.getMessageId());
        Log.d(TAG, "Message Type: " + remoteMessage.getMessageType());
        Log.d(TAG, "Sent Time: " + remoteMessage.getSentTime());

        // Check if message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "📊 Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        } else {
            Log.d(TAG, "⚠️ No data payload in message");
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "📱 Message Notification Body: " + remoteMessage.getNotification().getBody());
            sendNotification(remoteMessage.getNotification().getTitle(), 
                           remoteMessage.getNotification().getBody());
        } else {
            Log.d(TAG, "⚠️ No notification payload in message");
        }
    }

    private void handleDataMessage(Map<String, String> data) {
        String title = data.get("title");
        String message = data.get("message");
        String type = data.get("type");
        
        Log.d(TAG, "Handling data message - Title: " + title + ", Message: " + message + ", Type: " + type);
        
        if (title != null && message != null) {
            // Save to database
            saveToAlertsDatabase(title, message, type);
            
            // Send local notification
            sendNotification(title, message);
        }
    }

    private void sendNotification(String title, String message) {
        Log.d(TAG, "🔔 Creating notification: " + title + " - " + message);
        
        // Create notification channel for Android O and above
        createNotificationChannel();

        // Create intent to open NotificationActivity
        Intent intent = new Intent(this, NotificationActivity.class);
        intent.putExtra("alert_title", title);
        intent.putExtra("alert_message", message);
        intent.putExtra("alert_time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
        
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title != null ? title : "I-WATTS Alert")
                .setContentText(message != null ? message : "New notification")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            int notificationId = NOTIFICATION_ID + (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, notificationBuilder.build());
            Log.d(TAG, "✅ Notification sent with ID: " + notificationId);
        } else {
            Log.e(TAG, "❌ NotificationManager is null!");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "FCM Alerts";
            String description = "Firebase Cloud Messaging notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void saveToAlertsDatabase(String title, String message, String type) {
        // Save to Firebase alerts collection
        com.google.firebase.database.DatabaseReference db = com.google.firebase.database.FirebaseDatabase.getInstance().getReference();
        com.google.firebase.database.DatabaseReference alertsRef = db.child("alerts");
        
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("type", type != null ? type : "fcm");
        data.put("title", title);
        data.put("message", message);
        data.put("time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
        data.put("read", false);
        data.put("delete", false);
        
        alertsRef.push().setValue(data);
        Log.d(TAG, "Alert saved to database");
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        
        // Send token to Firebase Database so backend can use it
        com.google.firebase.database.DatabaseReference db = com.google.firebase.database.FirebaseDatabase.getInstance().getReference();
        db.child("fcm_tokens").child(token).setValue(true);
        
        // Also save to user preferences for local use
        android.content.SharedPreferences prefs = getSharedPreferences("FCM_PREFS", Context.MODE_PRIVATE);
        prefs.edit().putString("fcm_token", token).apply();
    }
}
