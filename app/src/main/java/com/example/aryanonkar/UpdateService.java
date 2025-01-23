package com.example.aryanonkar;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateService extends Service {

    Intent openAppIntent;
    PendingIntent openAppPendingIntent;
    Call downloadCall;
    NotificationCompat.Builder builder;
    NotificationManager manager;
    private static final String CANCEL_ACTION = "CANCEL_DOWNLOAD";

    private final List<Handler> handlerGroup = new ArrayList<>();

    public void addHandler(Handler handler) {
        handlerGroup.add(handler);
    }

    public void destroyHandlers() {
        for (Handler handler : handlerGroup) {
            handler.removeCallbacksAndMessages(null);
        }
        handlerGroup.clear();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("update", "Updates", NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
        registerReceiver(cancelReceiver, new IntentFilter(CANCEL_ACTION));
        openAppIntent = new Intent(UpdateService.this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        openAppPendingIntent = PendingIntent.getActivity(
                UpdateService.this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        new Thread(this::downloadAndInstallApk).start();
        return START_NOT_STICKY;
    }

    public Notification createNotification() {
        Intent cancelIntent = new Intent(CANCEL_ACTION);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder = new NotificationCompat.Builder(this, "update")
                .setContentTitle("Downloading update...")
                .setOngoing(true)
                .setSmallIcon(R.drawable.download_icon)
                .addAction(R.drawable.cancel_icon, "Cancel", cancelPendingIntent)
                .setProgress(100, 0, false);
        destroyHandlers();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (downloadCall != null) {
                downloadCall.cancel();
            }
            File apkFile = new File(UpdateService.this.getFilesDir(), "latest.apk");
            if (apkFile.exists()) apkFile.delete();
            manager.cancel(1);
            Notification notification = new NotificationCompat.Builder(UpdateService.this, "update")
                    .setSmallIcon(R.drawable.raw_error_icon)
                    .setContentTitle("Download failed")
                    .setContentText("Check your internet connection and try again.")
                    .setContentIntent(openAppPendingIntent)
                    .setAutoCancel(true)
                    .build();
            manager.notify(3, notification);
            stopForeground(false);
            stopSelf();
        }, 15000);
        addHandler(handler);
        return builder.build();
    }

    private final BroadcastReceiver cancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            destroyHandlers();
            if (downloadCall != null) {
                downloadCall.cancel();
            }
            File apkFile = new File(UpdateService.this.getFilesDir(), "latest.apk");
            if (apkFile.exists()) apkFile.delete();
            manager.cancel(1);
            Notification notification = new NotificationCompat.Builder(context, "update")
                    .setSmallIcon(R.drawable.download_icon)
                    .setContentTitle("Download canceled")
                    .setContentText("You stopped ChessGR from downloading this update.")
                    .setContentIntent(openAppPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build();
            manager.notify(2, notification);
            stopForeground(false);
            stopSelf();
        }
    };

    public void downloadAndInstallApk() {
        FirebaseUtils.getFirebaseDb().getReference("latest-version-download-link").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String downloadLink = null;
                if (snapshot.exists()) downloadLink = snapshot.getValue(String.class);
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(downloadLink).build();
                downloadCall = client.newCall(request);
                downloadCall.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            long totalBytes = response.body().contentLength();
                            InputStream inputStream = response.body().byteStream();
                            File file = new File(getFilesDir(), "latest.apk");
                            FileOutputStream fileOutputStream = new FileOutputStream(file);
                            byte[] buffer = new byte[8192];
                            long totalBytesRead = 0;
                            int bytesRead;
                            int lastProgress = -1;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                totalBytesRead += bytesRead;
                                int progress = (int) ((totalBytesRead * 100) / totalBytes);
                                if (progress > lastProgress + 4) {
                                    lastProgress = progress;
                                    builder.setProgress(100, progress, false);
                                    manager.notify(1, builder.build());
                                    if (progress > 96) {
                                        destroyHandlers();
                                        manager.cancel(1);

                                        Notification notification = new NotificationCompat.Builder(UpdateService.this, "update")
                                                .setSmallIcon(R.drawable.download_icon)
                                                .setContentTitle("Update downloaded successfully")
                                                .setContentText("Open the app to install this update.")
                                                .setContentIntent(openAppPendingIntent)
                                                .setAutoCancel(true)
                                                .build();
                                        manager.notify(4, notification);

                                    }else {
                                        destroyHandlers();
                                        Handler handler = new Handler(Looper.getMainLooper());
                                        handler.postDelayed(() -> {
                                            try {
                                                fileOutputStream.flush();
                                                inputStream.close();
                                                fileOutputStream.close();
                                            } catch (Exception ignored) {
                                            }
                                            if (downloadCall != null) {
                                                downloadCall.cancel();
                                            }
                                            File apkFile = new File(UpdateService.this.getFilesDir(), "latest.apk");
                                            if (apkFile.exists()) apkFile.delete();
                                            manager.cancel(1);
                                            Notification notification = new NotificationCompat.Builder(UpdateService.this, "update")
                                                    .setSmallIcon(R.drawable.raw_error_icon)
                                                    .setContentTitle("Download failed")
                                                    .setContentText("Check your internet connection and try again.")
                                                    .setContentIntent(openAppPendingIntent)
                                                    .setAutoCancel(true)
                                                    .build();
                                            manager.notify(3, notification);
                                            stopForeground(false);
                                            stopSelf();
                                        }, 10000);
                                        addHandler(handler);
                                    }
                                }
                                fileOutputStream.write(buffer, 0, bytesRead);
                            }
                            fileOutputStream.flush();
                            inputStream.close();
                            fileOutputStream.close();
                            if (UpdateService.this.getPackageManager().canRequestPackageInstalls()) {
                                Uri apkUri = FileProvider.getUriForFile(UpdateService.this, getPackageName() + ".provider", file);
                                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apkUri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                            stopForeground(true);
                            stopSelf();
                        }
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(cancelReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
