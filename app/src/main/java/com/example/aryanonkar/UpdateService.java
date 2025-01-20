package com.example.aryanonkar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateService extends Service {
    NotificationCompat.Builder builder;
    NotificationManager manager;

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("update", "Updates", NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        new Thread(this::downloadAndInstallApk).start();
        return START_NOT_STICKY;
    }

    public Notification createNotification() {
        builder = new NotificationCompat.Builder(this, "update")
                .setContentTitle("Downloading update...")
                .setOngoing(true)
                .setSmallIcon(R.drawable.download_icon)
                .setProgress(100, 0, false);
        return builder.build();
    }

    public void downloadAndInstallApk() {
        DbxClientV2 dbx = new DbxClientV2(new DbxRequestConfig("ChessGR"), "sl.u.AFfyET64_XNepQ0LNdrs9Y3xtG8vIEy6x3x-_JsUx7d1Qywf1Bw_cO3tDlDGqeu84rp1m2QPOKj4xqJZwILLrDcSi2pEofXFBDCWdlU4KssrdMVPy2-vJbrakX5uPiw9wVbZUsNf3nPgVMlo3fO7m6mHwsCJnFLY2OLOV88utLACfjjfbUgrQ1MMKHzjEtXzGmYfCKSiOCli0HyMnSpLN9QYb_5V_Xodn0_yNosgs9gDglpycPFsDzPYzIri9roHkl0K6s40SYXgEcGmDyx0euKo0Tt0OfwqeLPcufjLtabXSVljFfMirXqOdSKPqx8V9etlZvRMhkcp2mOy9r80T_3FHeMW0uYl8-c2LM-xSAINF_8R4AmWt0XddF4MHEAxwNj3k8IRPweSBt9PDrAkW8q3kK8IuvpPhJtLgpf_0oBd3vvI7luXWM0msvrxMplxG86B6MkBuKSf8_LBlTqyjNdEaSWZi9xQUWfeRfD1piCQeLy1MwWg9Z1Z_Om2-ccrpkS7FJuurjb8Egej5CMrMnlJKow8BK4yFnY7c7l4JJOwqBq6Uh2kzIJ2ns2qD0aE_-a87uBahVVSTfnhJTTztYBsENdTc_r_QelzFnsn166T-5Ew5LVmVqGUnOVCuNpFfapACmii9iVFmMIsI0NQR50QNwjj6BSxTokEh4IzdnPEkQXFi1neGxTef7tytjzP7amDAuP0IDAhpfeOGZN30LbWdDn9Szz9PXYoLbC4E36QAkusRry3XBu4MLszrCc41_oAgxM48c3gwhLL_5vT_JfProxOAampgKJNXtb3DpdDxLJKsFoIsfOA0oFg3wkWrEW0HaHEud7YRNCglCEmUvcbAVjwKHAzPiZp4cSE2G90cmr5O5ZgyrCaWHeM27ihQkHLVewP-AeAEknBcGO1BDlKDTOtvFVIuQhBRobStVnSZdUf_mbOIa1GRV3Ab4ktsT8tPjIlEws8vPpev4z0w97xXavhkItr0kF0UjxZyWHrW4gxqNdky4F9_OeZ1ftg_Kw4No2WWT9wnDyylbKoEhWeLj-4CQaYvEWwn6vj_hsb0zIqEeCzsVDM3sO6_khLot1k6dcqJqmpGp2KE9fNBibt2kpxYKU98QKWSDxdi5M5dV9F3rYF01WNPpad4oXkuUxhK8v4fUYzsH-Rkcc1kRvwQZDXjXKUe9vt-GLQwBerUe4kSsYan35ptfR-OcFDJciIsDQrSPAajmKshrl-IJi-dfukk_s3_0Kuv66Su9Z3-N7geFCujD8FPkHs_2BY1nOeT6uWOk7gHvnmbfY4Iben1LKG830bmi3gQBTFAx6Kok9Aa7SC_QzeAngdAib9rNilzvf4t4n5KyfaC3XjoIgnSuqgb6X5jLmMqIf7T3RyJ9kH8u6Puj7pq5HBqcF3KNaIjEUk2C7lR1nqrPGt54waT_6RJEf_H8DGfYszvk9Pfg");
        String downloadLink = null;
        try {
            downloadLink = dbx.files().getTemporaryLink("/ChessGR/latest.apk").getLink();
        } catch (DbxException e) {
            throw new RuntimeException(e);
        }
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(downloadLink).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("Download", "Download failed", e);
                File apkFile = new File(UpdateService.this.getFilesDir(), "latest.apk");
                if(apkFile.exists()) apkFile.delete();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    long totalBytes = response.body().contentLength();
                    InputStream inputStream = response.body().byteStream();
                    File file = new File(getFilesDir(), "latest.apk");
                    FileOutputStream fileOutputStream = new FileOutputStream(file);

                    byte[] buffer = new byte[4096];
                    long totalBytesRead = 0;
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        totalBytesRead += bytesRead;
                        int progress = (int) ((totalBytesRead * 100) / totalBytes);
                        if (progress == 100) {
                            manager.cancel(1);
                        }
                        builder.setProgress(100, progress, false);
                        manager.notify(1, builder.build());
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }

                    fileOutputStream.flush();
                    inputStream.close();
                    fileOutputStream.close();
                    if(UpdateService.this.getPackageManager().canRequestPackageInstalls()) {
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
