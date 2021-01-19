package com.gae.scaffolder.plugin;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.app.Activity.RESULT_OK;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

public class FCMPluginReceiver extends BroadcastReceiver {
    protected static final String SERVICE_NOTIFICATION_ACKNOWLEDGE = "notification/received/{1}/";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();

        if (bundle != null && !bundle.getBoolean("isRecall", false)) {
            String title = bundle.getString("gcm.notification.title");
            String body = bundle.getString("gcm.notification.body");
            String messageId = bundle.getString("Notification_ID");

            try {
                if(!this.isForegrounded()) {
                    this.sendNotification(context, intent, title, body, messageId);
                }

                this.sendReceivedAcknowledgement(context, messageId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        setResultCode(RESULT_OK);
    }

    private void sendNotification(Context context, Intent intent, String title, String body, String messageId) {
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder =  new NotificationCompat.Builder(context.getApplicationContext(), "tahweel_notifications");
        intent.putExtra("isRecall", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setSmallIcon(context.getApplicationInfo().icon);
        mBuilder.setContentTitle(title);
        mBuilder.setContentText(body);
        mBuilder.setPriority(Notification.PRIORITY_MAX);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "TAHWEEL_CHANNEL";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Tahweel Channel",
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
            mBuilder.setChannelId(channelId);
        }

        abortBroadcast();
        mNotificationManager.cancelAll();

        Handler handler = new Handler();
        handler.postDelayed(() -> {
            clearAbortBroadcast();
            mNotificationManager.notify(0, mBuilder.build());
        }, 1500);
    }

    private void sendReceivedAcknowledgement(Context context, String messageId) {
        SharedPreferences pref = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);

        String token = pref.getString("SESSION_TOKEN", "");
        String deviceId = pref.getString("DEVICE_ID", "");
        String baseURL = pref.getString("BASE_URL", "");

        if(token != null && !token.isEmpty()) {
            OkHttpClient okHttpClient = new OkHttpClient();
            FormBody.Builder formBodyBuilder = new FormBody.Builder();

            formBodyBuilder.add("deviceId", deviceId);
            FormBody formBody = formBodyBuilder.build();

            Request.Builder builder = new Request.Builder();
            builder = builder.addHeader("deviceId", deviceId);
            builder = builder.addHeader("Authorization", "bearer " + token);
            builder = builder.addHeader("Content-Type", "application/json");

            builder = builder.url(baseURL + SERVICE_NOTIFICATION_ACKNOWLEDGE.replace("{1}", messageId));
            builder = builder.post(formBody);

            Request request = builder.build();

            Call call = okHttpClient.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d(FCMPluginActivity.TAG, e != null ? e.getMessage() : "sendReceivedAcknowledgement");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(FCMPluginActivity.TAG, "SendReceivedAcknowledgement Success");
                }
            });
        }
    }

    public boolean isForegrounded() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }
}
