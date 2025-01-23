package com.example.aryanonkar;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> {
            getOnBackPressedDispatcher();
            getOnBackPressedDispatcher().onBackPressed();
        });
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        SharedPreferences.OnSharedPreferenceChangeListener listener;
        SharedPreferences pref;

        @Override
        public void onDestroy() {
            super.onDestroy();
            pref.unregisterOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            pref = PreferenceManager.getDefaultSharedPreferences(requireContext());
            listener = (sharedPreferences, key) -> {
                if(key.equals("update")) findPreference("update").setSummary("Last checked on " + sharedPreferences.getString("update", "N/A"));
            };
            pref.registerOnSharedPreferenceChangeListener(listener);
            try {
                findPreference("update").setSummary("Last checked on " + pref.getString("update", "N/A"));
                findPreference("release").setSummary(requireContext().getPackageManager().getApplicationInfo(requireContext().getPackageName(), PackageManager.GET_META_DATA).metaData.getString("released-on"));
                findPreference("version").setSummary(requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).getLongVersionCode() + ".0");
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            findPreference("dev").setOnPreferenceClickListener((preference) -> {
                startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://aryanonkar-portfolio.vercel.app/")));
                return true;
            });
            findPreference("update").setOnPreferenceClickListener((preference) -> {
                if(!MainActivity.isInternetAvailable(requireContext())){
                    Snackbar.make(getActivity().findViewById(android.R.id.content),"Check your internet connection and try again",Snackbar.LENGTH_SHORT).show();
                    return true;
                }
                Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show();
                FirebaseUtils.getFirebaseDb().getReference("latest-version").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        File apkFile = new File(requireContext().getFilesDir(), "latest.apk");
                        long version;
                        try {
                            version = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).getLongVersionCode();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        if(apkFile.exists() || snapshot.getValue(Integer.class) > version){
                            UpdateApp.CheckForUpdates(requireContext(), getActivity());
                        }else{
                            Toast.makeText(requireContext(), "No update available", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
                return true;
            });
            findPreference("theme").setOnPreferenceChangeListener(((preference, newValue) -> {
                if(!((ListPreference)findPreference("theme")).getValue().equalsIgnoreCase(newValue.toString())) {
                    boolean isAppDark = ((requireContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
                    boolean isSystemDark = ((UiModeManager) requireContext().getSystemService(Context.UI_MODE_SERVICE)).getNightMode() == UiModeManager.MODE_NIGHT_YES;
                    boolean isNewThemeDiff;
                    if(newValue.toString().equalsIgnoreCase("system")){
                        isNewThemeDiff = isAppDark ^ isSystemDark;
                    }else{
                        boolean isNewValueDark = newValue.toString().equalsIgnoreCase("dark");
                        isNewThemeDiff = isAppDark ^ isNewValueDark;
                    }
                    if(pref.getBoolean("isReviewing", false) && isNewThemeDiff) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Warning")
                                .setMessage("Changing theme will cancel the ongoing game-review process. Do you still want to continue?")
                                .setPositiveButton("Yes", (d, w) -> {
                                    ((ListPreference) findPreference("theme")).setValue(newValue.toString().toLowerCase());
                                    if (newValue.toString().equalsIgnoreCase("light"))
                                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                                    else if (newValue.toString().equalsIgnoreCase("dark"))
                                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                                    else
                                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                                })
                                .setNegativeButton("No", (d, w) -> {
                                    d.dismiss();
                                }).show();
                    }else{
                        if (newValue.toString().equalsIgnoreCase("light"))
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        else if (newValue.toString().equalsIgnoreCase("dark"))
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        else
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                        return true;
                    }
                }
                return false;
            }));
        }
    }
}