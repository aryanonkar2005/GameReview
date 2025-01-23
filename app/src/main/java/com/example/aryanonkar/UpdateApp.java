package com.example.aryanonkar;

import static com.example.aryanonkar.MainActivity.isInternetAvailable;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class UpdateApp {

    static AlertDialog dialogToShow;
    public static void CheckForUpdates(Context context, Activity activity){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        long version;
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        File apkFile = new File(context.getFilesDir(), "latest.apk");
        if (apkFile.exists()) {
            FirebaseUtils.getFirebaseDb().getReference("latest-version").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    pref.edit().putString("update", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy 'at' hh:mm a", Locale.ENGLISH))).apply();
                    File apkFile = new File(context.getFilesDir(), "latest.apk");
                    PackageManager pm = context.getPackageManager();
                    PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_META_DATA);
                    int downloadedApkVersion = info != null ? info.versionCode : -1;
                    if (downloadedApkVersion != -1) {
                        if (snapshot.exists() && snapshot.getValue(Integer.class) > downloadedApkVersion) {
                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                                    .setTitle("Better version is available (v" + snapshot.getValue(Integer.class) + ".0)")
                                    .setMessage("You are currently using v" + version + ".0. You have already downloaded version v" + downloadedApkVersion + ".0 but a newer version v" + snapshot.getValue(Integer.class) + ".0 is now available. Would you like to:\n1. Download and install the latest version (v" + snapshot.getValue(Integer.class) + ".0)?\n2. Install the version you've already downloaded (v" + downloadedApkVersion + ".0) and update later?")
                                    .setPositiveButton("Download & install v" + snapshot.getValue(Integer.class) + ".0", null)
                                    .setNegativeButton("Install v" + downloadedApkVersion + ".0", (d, e) -> {
                                        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
                                        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apkUri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        context.startActivity(intent);
                                    })
                                    .setNeutralButton("Remind me later", (d, e) -> {
                                        pref.edit().putString("remind-later-clicked-on", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH))).apply();
                                        d.dismiss();
                                    });
                            AlertDialog dialog = builder.create();
                            if(dialogToShow != null) dialogToShow.dismiss();
                            dialogToShow = dialog;
                            dialogToShow.show();
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((v) -> {
                                if (isInternetAvailable(context)) {
                                    Intent intent = new Intent(context, UpdateService.class);
                                    context.startService(intent);
                                    dialog.dismiss();
                                } else {
                                    Snackbar.make(activity.findViewById(android.R.id.content), "Check your internet connection and try again.", Snackbar.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            if (context.getPackageManager().canRequestPackageInstalls()) {
                                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                                        .setTitle("Install update")
                                        .setMessage("Latest update has been downloaded successfully. Would you like to install it now?")
                                        .setPositiveButton("Install now", (d, e) -> {
                                            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
                                            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apkUri).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                            context.startActivity(intent);
                                        })
                                        .setNegativeButton("Remind me later", (d, e) -> {
                                            pref.edit().putString("remind-later-clicked-on", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH))).apply();
                                            d.dismiss();
                                        });
                                AlertDialog dialog = builder.create();
                                if(dialogToShow != null) dialogToShow.dismiss();
                                dialogToShow = dialog;
                                dialogToShow.show();
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        } else {
            FirebaseUtils.getFirebaseDb().getReference("latest-version").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    pref.edit().putString("update", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy 'at' hh:mm a", Locale.ENGLISH))).apply();
                    if (snapshot.exists()) {
                        if (snapshot.getValue(Integer.class) > version && context.getPackageManager().canRequestPackageInstalls()) {
                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                                    .setTitle("Update available")
                                    .setMessage("You are running an old version of ChessGR. Please update to the latest version.")
                                    .setPositiveButton("Update now", null)
                                    .setNegativeButton("Remind me later", (dialog, which) -> {
                                        pref.edit().putString("remind-later-clicked-on", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH))).apply();
                                        dialog.dismiss();
                                    });
                            AlertDialog dialog = builder.create();
                            if(dialogToShow != null) dialogToShow.dismiss();
                            dialogToShow = dialog;
                            dialogToShow.show();
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                                if (isInternetAvailable(context)) {
                                    Intent intent = new Intent(context, UpdateService.class);
                                    context.startService(intent);
                                    dialog.dismiss();
                                } else {
                                    Snackbar.make(activity.findViewById(android.R.id.content), "Check your internet connection and try again.", Snackbar.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }
    }
}
