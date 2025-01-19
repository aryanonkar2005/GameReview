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
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            try {
                findPreference("version").setSummary(String.valueOf(requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).getLongVersionCode()));
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            findPreference("dev").setOnPreferenceClickListener((preference) -> {
                startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://aryanonkar-portfolio.vercel.app/")));
                return true;
            });
            findPreference("theme").setOnPreferenceChangeListener(((preference, newValue) -> {
                if(!((ListPreference)findPreference("theme")).getValue().equalsIgnoreCase(newValue.toString())) {
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(requireContext());
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