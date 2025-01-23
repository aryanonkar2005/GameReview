package com.example.aryanonkar;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    long version;
    ClipboardManager clipboardManager;
    boolean blockedAccess = false;
    AlertDialog block_dialog;
    String game_url = null;
    SharedPreferences pref;
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
        if (!pref.getBoolean("isReviewing", false))
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
        startActivity(new Intent(this, HelpActivity.class));
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
    protected void onResume() {
        super.onResume();
        if (!this.getPackageManager().canRequestPackageInstalls()) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle("Grant permission to install updates")
                    .setMessage("This app wasn’t installed from the Play Store, so it needs permission to install updates from unknown sources. To grant this permission, tap the 'Settings' button below. This will take you to the 'Install Unknown Apps' page. Once there, turn on the switch next to 'Allow from this source'.")
                    .setPositiveButton("Settings", (d, e) -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        intent.setData(Uri.parse("package:" + this.getPackageName()));
                        this.startActivity(intent);
                    })
                    .setNegativeButton("Exit", (d, e) -> {
                        System.exit(0);
                    });
            AlertDialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
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

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                100);

        pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.edit().putBoolean("isReviewing", false).apply();
        pref.edit().putBoolean("onSuccSnackDSA", false).apply();

        if(LocalDate.parse(pref.getString("remind-later-clicked-on", "01-Jan-2000"), DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)).isBefore(LocalDate.now())) {
            FirebaseUtils.getFirebaseDb().getReference("latest-version").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    UpdateApp.CheckForUpdates(MainActivity.this, MainActivity.this);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }

        File apkFile = new File(getFilesDir(), "latest.apk");
        if (pref.getLong("version-code", 0) < version) {
            if (apkFile.exists()) apkFile.delete();
            pref.edit().putLong("version-code", version).apply();
        }

        if (pref.getString("theme", "system").equalsIgnoreCase("light"))
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if (pref.getString("theme", "system").toString().equalsIgnoreCase("dark"))
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
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
                    viewToHide.setVisibility(View.GONE);
                } else {
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
                            Pattern pattern = Pattern.compile("https://www\\.chess\\.com/[a-z0-9/]+\\S");
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

        findViewById(R.id.openInApp).setOnClickListener((e) -> {
            String redirect_url;
            String[] subdirs = game_url.substring(22).split("/");
            if (subdirs[0].equals("game")) {
                redirect_url = "https://www.chess.com/analysis/game/" + subdirs[1] + "/" + subdirs[2];
            } else {
                redirect_url = "https://www.chess.com/analysis/game/" + subdirs[0] + "/" + subdirs[2];
            }
            startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(redirect_url)));
        });

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

            Pattern pattern = Pattern.compile("https://www\\.chess\\.com/[a-z0-9/]+\\S");
            Matcher matcher = pattern.matcher(raw_url_inp);
            if (matcher.find()) game_url = matcher.group();

            if (game_url == null) {
                if (raw_url_inp.contains("http"))
                    Snackbar.make(this, findViewById(android.R.id.content), "This app can only review games played on chess.com", Snackbar.LENGTH_LONG).show();
                else
                    ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError("Invalid URL");
                return;
            }

            if (pref.getBoolean("confirm", false)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Are you sure?")
                        .setMessage("Are you sure you want to review this game? This is just to make sure that you haven't hit the review game button by mistake.")
                        .setPositiveButton("Yes", (d, w) -> review_game())
                        .setNegativeButton("No", (d, w) -> d.dismiss())
                        .show();
            } else review_game();
        });
        FirebaseUtils.getFirestore().collection("users")
                .document(Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID))
                .addSnapshotListener((snapshot, error) -> {
                    if (block_dialog != null && block_dialog.isShowing()) {
                        block_dialog.dismiss();
                        block_dialog = null;
                    }
                    if (!snapshot.exists()) {
                        showEnterAccessCodeDialog();
                    } else if (snapshot.get("Blocked", boolean.class)) {
                        showBlockedDialog();
                        blockedAccess = true;
                    }
                });
    }

    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    public void showEnterAccessCodeDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        TextInputLayout inpLayout = dialogView.findViewById(R.id.inpLayout);
        TextInputEditText inpEditText = dialogView.findViewById(R.id.inp);
        LinearLayout inpCont = dialogView.findViewById(R.id.inpCont);
        final float scale = getResources().getDisplayMetrics().density;
        inpCont.setPadding((int) (24 * scale), 0, (int) (24 * scale), 0);
        inpEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) inpEditText.setLetterSpacing(0.25F);
                else inpEditText.setLetterSpacing(0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Enter access code")
                .setView(dialogView)
                .setMessage("To get access code contact Aryan Onkar.")
                .setPositiveButton("Submit", null)
                .setNegativeButton("Exit", (dialog, which) -> System.exit(0));
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!isInternetAvailable(this)) {
                Toast.makeText(this, "No internet. Try again", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.setTitle("Verifying access code...");
            dialog.setMessage("Please have patience as this process can take up to 30 seconds.");
            inpLayout.setEnabled(false);
            inpCont.setPadding((int) (24 * scale), (int) (16 * scale), (int) (24 * scale), 0);

            FirebaseUtils.getFirebaseDb().getReference("unused-access-code").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String code = "null";
                    boolean hasCodeExpired = false;
                    if (snapshot.getValue() != null) {
                        HashMap<Integer, String> map = (HashMap<Integer, String>) snapshot.getValue();
                        code = String.valueOf(map.keySet().stream().findFirst().get());
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a", Locale.ENGLISH);
                        LocalDateTime givenDateTime = LocalDateTime.parse(map.get(map.keySet().iterator().next()).toString(), formatter);
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime threeMinsAgo = now.minusMinutes(3);
                        hasCodeExpired = givenDateTime.isBefore(threeMinsAgo);
                    }
                    if (inpEditText.getText().toString().contentEquals(code)) {
                        if (!hasCodeExpired) {
                            Toast.makeText(MainActivity.this, "OTP was correct", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                            View unameDialogView = LayoutInflater.from(MainActivity.this).inflate(R.layout.username_input_dialog, null);
                            TextInputEditText unameInpEditText = unameDialogView.findViewById(R.id.unameInp);
                            TextInputLayout unameInpLayout = unameDialogView.findViewById(R.id.unameInpLayout);
                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MainActivity.this);
                            builder.setTitle("Enter your full name")
                                    .setMessage("Must be less than 32 characters.")
                                    .setView(unameDialogView)
                                    .setNegativeButton("Exit", (d, e) -> System.exit(0))
                                    .setPositiveButton("Submit", null);
                            AlertDialog unameDialog = builder.create();
                            unameDialog.setCancelable(false);
                            unameDialog.setCanceledOnTouchOutside(false);
                            unameDialog.show();
                            unameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                                if (unameInpEditText.getText().toString().trim().isEmpty()) {
                                    unameInpLayout.setError("This field is required");
                                    return;
                                }
                                if (!isInternetAvailable(getApplicationContext())) {
                                    Toast.makeText(getApplicationContext(), "No internet. Try again", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                FirebaseUtils.getFirebaseDb().getReference("unused-access-code").removeValue();
                                String androidId = Settings.Secure.getString(MainActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
                                FirebaseUtils.getFirestore().collection("users").document(androidId).set(new HashMap<String, Object>(Map.of(
                                        "Username", unameInpEditText.getText().toString().trim(), "Blocked", false, "Device model", Build.MODEL,
                                        "Last game reviewed on", ": No game reviewed till now", "Games reviewed till now", 0)));
                                unameDialog.dismiss();
                            });
                        } else {
                            dialog.setTitle("Enter access code");
                            dialog.setMessage("To get access code contact Aryan Onkar.");
                            inpLayout.setEnabled(true);
                            inpLayout.setError("This OTP has expired");
                            inpCont.setPadding((int) (24 * scale), 0, (int) (24 * scale), 0);
                        }
                    } else {
                        dialog.setTitle("Enter access code");
                        dialog.setMessage("To get access code contact Aryan Onkar.");
                        inpLayout.setEnabled(true);
                        inpLayout.setError("Incorrect access code");
                        inpCont.setPadding((int) (24 * scale), 0, (int) (24 * scale), 0);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    inpLayout.setError("Server error");
                }
            });
        });
    }

    public void showBlockedDialog() {
        block_dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                .setTitle("Access denied")
                .setCancelable(false)
                .setMessage("You have been denied access to this application." +
                        " This could be due to abuse or misuse of this application." +
                        " Contact developer for more information.")
                .setPositiveButton("Exit", (d, e) -> System.exit(0)).create();
        block_dialog.setCanceledOnTouchOutside(false);
        block_dialog.show();
    }

    public void display_error(String err, String log) {
        devlog = log;
        runOnUiThread(() -> {
            pref.edit().putBoolean("isReviewing", false).apply();
            findViewById(R.id.urlInp).setEnabled(true);
            findViewById(R.id.reviewBtn).setEnabled(true);
            findViewById(R.id.pasteBtn).setEnabled(true);
            findViewById(R.id.spinnerCont).setVisibility(View.GONE);
            ((ImageView) findViewById(R.id.statusIcon)).setImageResource(R.drawable.error_icon);
            ((TextView) findViewById(R.id.statusTxt)).setTextColor(getColor(R.color.errorRed));
            ((TextView) findViewById(R.id.statusTxt)).setText(err);
            findViewById(R.id.openInApp).setVisibility(View.GONE);
            findViewById(R.id.statusCont).setVisibility(View.VISIBLE);
        });
    }

    public void review_game() {
        pref.edit().putBoolean("isReviewing", true).apply();
        ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError(null);
        ((TextInputLayout) findViewById(R.id.urlInpLayout)).setErrorEnabled(false);
        findViewById(R.id.urlInp).setEnabled(false);
        findViewById(R.id.reviewBtn).setEnabled(false);
        findViewById(R.id.pasteBtn).setEnabled(false);
        hideKeyboard();
        ((TextView) findViewById(R.id.spinnerTxt)).setText("Sending review request...");
        findViewById(R.id.spinnerCont).setVisibility(View.VISIBLE);
        findViewById(R.id.chessIconCont).setVisibility(View.GONE);
        findViewById(R.id.statusCont).setVisibility(View.GONE);

        String androidId = Settings.Secure.getString(MainActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
        FirebaseUtils.getFirestore().collection("users").document(androidId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                display_error("Access denied.\nUser not registered.", "Contact developer to get yourself registered");
                showEnterAccessCodeDialog();
                return;
            }
            if (snapshot.get("Blocked", boolean.class)) {
                display_error("Access denied by developer", "You have been temporarily denied access to this application." +
                        "This could be due to abuse or misuse of this application." +
                        "Contact developer for more information.");
                showBlockedDialog();
                return;
            }
            OkHttpClient client = new OkHttpClient.Builder() // Set connection timeout to 30 seconds
                    .readTimeout(60, TimeUnit.SECONDS)     // Set write timeout to 60 seconds
                    .build();

            FormBody body = new FormBody.Builder()
                    .add("game-url", game_url)
                    .build();

            Request request = new Request.Builder()
                    .url("https://chessgr-api.up.railway.app/review-game")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    display_error("Failed to send your request", e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.body() != null)
                        devlog = "Response code: " + response.code() + "\nResponse body: " + response.body().string();
                    if (response.isSuccessful() || response.body().toString().contains("check if it works")) {
                        runOnUiThread(() -> {
                            String androidId1 = Settings.Secure.getString(MainActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
                            FirebaseUtils.getFirestore().collection("users").document(androidId1).get().addOnSuccessListener((s) -> {
                                FirebaseUtils.getFirestore().collection("users").document(androidId1)
                                        .update("Last game reviewed on", LocalDateTime.now().format(DateTimeFormatter.ofPattern(" dd-MMM-yyyy 'at' hh:mm a", Locale.ENGLISH)), "Games reviewed till now", ((Long) s.get("Games reviewed till now")) + 1);
                            });
                            pref.edit().putBoolean("isReviewing", false).apply();
                            findViewById(R.id.urlInp).setEnabled(true);
                            findViewById(R.id.reviewBtn).setEnabled(true);
                            findViewById(R.id.pasteBtn).setEnabled(true);
                            ((TextInputEditText) findViewById(R.id.urlInp)).setText("");
                            findViewById(R.id.spinnerCont).setVisibility(View.GONE);
                            ((ImageView) findViewById(R.id.statusIcon)).setImageResource(R.drawable.check_circle_icon);
                            ((TextView) findViewById(R.id.statusTxt)).setTextColor(getColor(R.color.successGreen));
                            ((TextView) findViewById(R.id.statusTxt)).setText("Game reviewed\nsuccessfully");
                            findViewById(R.id.openInApp).setVisibility(View.VISIBLE);
                            findViewById(R.id.statusCont).setVisibility(View.VISIBLE);
                            if (pref.getBoolean("onSuccSnackDSA", false))
                                Toast.makeText(getApplicationContext(), "Game reviewed successfully", Toast.LENGTH_LONG).show();
                            if (pref.getBoolean("redirect", false)) {
                                String redirect_url;
                                String[] subdirs = game_url.substring(22).split("/");
                                if (subdirs[0].equals("game")) {
                                    redirect_url = "https://www.chess.com/analysis/game/" + subdirs[1] + "/" + subdirs[2];
                                } else {
                                    redirect_url = "https://www.chess.com/analysis/game/" + subdirs[0] + "/" + subdirs[2];
                                }
                                startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(redirect_url)));
                            }
                        });
                        if (!pref.getBoolean("redirect", false)) {
                            if (!pref.getBoolean("onSuccSnackDSA", false)) {
                                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "Now open this game on chess.com's website or mobile app and click their light green colored game review button. This time they won't ask you to purchase a platinum or diamond subscription to review this game.", Snackbar.LENGTH_INDEFINITE);
                                View snackbarView = snackbar.getView();
                                TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
                                textView.setMaxLines(10);
                                textView.setEllipsize(null);
                                textView.setSingleLine(false);
                                snackbar.setAction("Don't show again", (e) -> {
                                    pref.edit().putBoolean("onSuccSnackDSA", true).apply();
                                });
                                snackbar.show();
                            }
                        }
                    } else {
                        display_error("Server Error", devlog);
                    }
                }
            });
        }).addOnFailureListener(e -> {
            display_error("Cannot verify authority", e.getMessage());
        });
    }
}