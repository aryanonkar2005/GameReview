package com.example.aryanonkar;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    ClipboardManager clipboardManager = null;
    String devlog = "Nothing to show";

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        try {
            updatePasteBtn();
        } catch (Exception ignored) {
        }
    }

    public void updatePasteBtn() {
        if (clipboardManager.hasPrimaryClip() && clipboardManager.getPrimaryClipDescription().hasMimeType("text/plain")) {
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                String clipboardText = clipData.getItemAt(0).getText().toString().trim();
                if (!clipboardText.isEmpty())
                    enablePasteBtn();
                else disablePasteBtn();
            } else disablePasteBtn();
        } else disablePasteBtn();
    }

    public void enablePasteBtn() {
        if (findViewById(R.id.spinnerCont).getVisibility() == View.GONE)
            findViewById(R.id.pasteBtn).setEnabled(true);
    }

    public void disablePasteBtn() {
        findViewById(R.id.pasteBtn).setEnabled(false);
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
    }

    public void openSettingsPage(MenuItem menuItem) {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    public void openHelpPage(MenuItem menuItem) {

    }

    public static boolean isGestureNavEnabled(Context context) {
        try {
            int mode = Settings.Secure.getInt(context.getContentResolver(), "navigation_mode");
            return mode == 2;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

        if(pref.getString("theme","system").equalsIgnoreCase("light")) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if(pref.getString("theme","system").toString().equalsIgnoreCase("dark")) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        if (isGestureNavEnabled(this)) {
            findViewById(R.id.line).setVisibility(View.GONE);
        } else {
            findViewById(R.id.line).setVisibility(View.VISIBLE);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            View rootView = findViewById(android.R.id.content);
            final View viewToHide = findViewById(R.id.chessIconGrandparent);

            rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Rect r = new Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                if (keypadHeight > screenHeight * 0.15) {
                    // Keyboard is open
                    viewToHide.setVisibility(View.GONE);
                } else {
                    // Keyboard is closed
                    viewToHide.setVisibility(View.VISIBLE);
                }
            });
            clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            updatePasteBtn();
            ClipboardManager.OnPrimaryClipChangedListener clipboardListener = this::updatePasteBtn;
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
            findViewById(R.id.pasteBtn).setOnClickListener((e) -> {
                if (clipboardManager.hasPrimaryClip() && clipboardManager.getPrimaryClipDescription().hasMimeType("text/plain")) {
                    ClipData clipData = clipboardManager.getPrimaryClip();
                    if (clipData != null && clipData.getItemCount() > 0) {
                        String clipText = clipData.getItemAt(0).getText().toString().trim();
                        if (!clipText.isEmpty()) {
                            ((TextInputEditText) findViewById(R.id.urlInp)).setText(clipText);
                            ((TextInputEditText) findViewById(R.id.urlInp)).setSelection(clipText.length());
                            Toast.makeText(this, "Pasted last copied text from clipboard", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
            if (pref.getBoolean("autopaste", false)) {
                if (clipboardManager.hasPrimaryClip() && clipboardManager.getPrimaryClipDescription().hasMimeType("text/plain")) {
                    ClipData clipData = clipboardManager.getPrimaryClip();
                    if (clipData != null && clipData.getItemCount() > 0) {
                        String clipboardText = clipData.getItemAt(0).getText().toString().trim();
                        if (!clipboardText.isEmpty()) {
                            Pattern pattern = Pattern.compile("https://www.chess.com/([a-zA-Z0-9\\-]+)/game/([a-zA-Z0-9\\-]+)");
                            Matcher matcher = pattern.matcher(clipboardText);
                            String game_url = null;
                            if (matcher.find()) game_url = matcher.group();
                            if (game_url != null) {
                                ((TextInputEditText) findViewById(R.id.urlInp)).setText(clipboardText);
                                Toast.makeText(this, "Game URL pasted from clipboard", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
        }, 1);

        ((TextInputEditText) findViewById(R.id.urlInp)).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0 && Character.isWhitespace(s.charAt(0))) s.delete(0, 1);
            }
        });

        findViewById(R.id.statusCont).setOnLongClickListener((e) -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Developer Logs")
                    .setMessage(devlog)
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
            return false;
        });

        findViewById(R.id.reviewBtn).setOnClickListener((event) -> {
            if (((TextInputEditText) findViewById(R.id.urlInp)).getText().toString().isBlank()) {
                ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError("This field is required");
                return;
            }
            String raw_url_inp = Objects.requireNonNull(((TextInputEditText) findViewById(R.id.urlInp)).getText()).toString();

            Pattern pattern = Pattern.compile("https://www.chess.com/([a-zA-Z0-9\\-]+)/game/([a-zA-Z0-9\\-]+)");
            Matcher matcher = pattern.matcher(raw_url_inp);
            String game_url = null;
            if (matcher.find()) game_url = matcher.group();

            if (game_url == null) {
                if (raw_url_inp.contains("http"))
                    Snackbar.make(this, findViewById(android.R.id.content), "This app can only review games played on chess.com", Snackbar.LENGTH_LONG).show();
                else
                    ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError("Invalid URL");
                return;
            }

            ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError(null);
            ((TextInputLayout) findViewById(R.id.urlInpLayout)).setErrorEnabled(false);
            findViewById(R.id.urlInp).setEnabled(false);
            findViewById(R.id.reviewBtn).setEnabled(false);
            findViewById(R.id.pasteBtn).setEnabled(false);
            hideKeyboard();
            findViewById(R.id.spinnerCont).setVisibility(View.VISIBLE);
            findViewById(R.id.chessIconCont).setVisibility(View.GONE);
            findViewById(R.id.statusCont).setVisibility(View.GONE);
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(240, TimeUnit.SECONDS) // Connection timeout
                    .readTimeout(240, TimeUnit.SECONDS)    // Read timeout
                    .writeTimeout(240, TimeUnit.SECONDS).build();

            Request request = new Request.Builder()
                    .url("https://chess-review-api.onrender.com/game-review/?game-url=" + game_url)
                    .post(RequestBody.create("", null))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    devlog = e.getMessage();
                    runOnUiThread(() -> {
                        findViewById(R.id.urlInp).setEnabled(true);
                        findViewById(R.id.reviewBtn).setEnabled(true);
                        findViewById(R.id.pasteBtn).setEnabled(true);
                        findViewById(R.id.spinnerCont).setVisibility(View.GONE);
                        ((ImageView) findViewById(R.id.statusIcon)).setImageResource(R.drawable.error_icon);
                        ((TextView) findViewById(R.id.statusTxt)).setTextColor(getColor(R.color.errorRed));
                        ((TextView) findViewById(R.id.statusTxt)).setText("Failed to send your request");
                        findViewById(R.id.statusCont).setVisibility(View.VISIBLE);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.body() != null)
                        devlog = "Response code: " + response.code() + "\nResponse body: " + response.body().string();
                    if (response.isSuccessful() && response.body() != null) {
                        runOnUiThread(() -> {
                            findViewById(R.id.urlInp).setEnabled(true);
                            findViewById(R.id.reviewBtn).setEnabled(true);
                            findViewById(R.id.pasteBtn).setEnabled(true);
                            ((TextInputEditText) findViewById(R.id.urlInp)).setText("");
                            findViewById(R.id.spinnerCont).setVisibility(View.GONE);
                            ((ImageView) findViewById(R.id.statusIcon)).setImageResource(R.drawable.check_circle_icon);
                            ((TextView) findViewById(R.id.statusTxt)).setTextColor(getColor(R.color.successGreen));
                            ((TextView) findViewById(R.id.statusTxt)).setText("Game reviewed\nsuccessfully");
                            findViewById(R.id.statusCont).setVisibility(View.VISIBLE);
                        });
                    } else {
                        runOnUiThread(() -> {
                            findViewById(R.id.urlInp).setEnabled(true);
                            findViewById(R.id.reviewBtn).setEnabled(true);
                            findViewById(R.id.pasteBtn).setEnabled(true);
                            findViewById(R.id.spinnerCont).setVisibility(View.GONE);
                            ((ImageView) findViewById(R.id.statusIcon)).setImageResource(R.drawable.error_icon);
                            ((TextView) findViewById(R.id.statusTxt)).setTextColor(getColor(R.color.errorRed));
                            ((TextView) findViewById(R.id.statusTxt)).setText("Server Error");
                            findViewById(R.id.statusCont).setVisibility(View.VISIBLE);
                        });
                    }
                }
            });
        });
    }
}