package com.example.aryanonkar;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.edit().putBoolean("isReviewing", false).apply();
        pref.edit().putBoolean("onSuccSnackDSA", false).apply();

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

        findViewById(R.id.openInApp).setOnClickListener((e) -> {
            String redirect_url = game_url.substring(0, 21) + "/analysis/game/" + game_url.substring(22, game_url.indexOf("/game/") + 1) + game_url.substring(32);
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

            Pattern pattern = Pattern.compile("https://www.chess.com/([a-zA-Z0-9\\-]+)/game/([a-zA-Z0-9\\-]+)");
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
            dialog.setTitle("Verifying access code...");
            dialog.setMessage("Please have patience as this process can take up to 30 seconds.");
            inpLayout.setEnabled(false);
            inpCont.setPadding((int) (24 * scale), (int) (16 * scale), (int) (24 * scale), 0);
            FirebaseUtils.getFirebaseDb().getReference("unused-access-code").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String code;
                    if (snapshot.getValue() != null)
                        code = String.valueOf(((HashMap<Integer, String>) snapshot.getValue()).keySet().stream().findFirst());
                    else
                        code = "null";
                    if (inpEditText.getText().toString().contentEquals(code)) {
                        Toast.makeText(MainActivity.this, "OTP was correct", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        View unameDialogView = LayoutInflater.from(MainActivity.this).inflate(R.layout.username_input_dialog, null);
                        TextInputEditText unameInpEditText = unameDialogView.findViewById(R.id.unameInp);
                        AlertDialog unameDialog = new MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle("Enter your full name")
                                .setMessage("Must be less than 32 characters.")
                                .setView(unameDialogView)
                                .setNegativeButton("Exit", (d, e) -> System.exit(0))
                                .setPositiveButton("Submit", (d, e) -> {
                                    String androidId = Settings.Secure.getString(MainActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
                                    FirebaseUtils.getFirestore().collection("users").document(androidId).set(new HashMap<String, Object>(Map.of(
                                            "Username", unameInpEditText.getText().toString().trim(), "Blocked", false, "Device model", Build.MODEL,
                                            "Last game reviewed on", "No game reviewed till now", "Games reviewed today",
                                            0, "Games reviewed till now", 0,
                                            "History", new HashMap<String, String>())));
                                }).create();
                        unameDialog.setCancelable(false);
                        unameDialog.setCanceledOnTouchOutside(false);
                        unameDialog.show();
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
        FirebaseUtils.getFirestore().collection("users").document(androidId).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot snapshot) {
                if (!snapshot.exists()) {
                    display_error("Access denied.\nUser not registered.", "Contact developer to get yourself registered");
                    showEnterAccessCodeDialog();
                }
                if (snapshot.get("Blocked", boolean.class)) {
                    display_error("Access denied by developer", "You have been temporarily denied access to this application." +
                            "This could be due to abuse or misuse of this application." +
                            "Contact developer for more information.");
                    showBlockedDialog();
                    return;
                }
                OkHttpClient client = new OkHttpClient();
                String encodedUrl = null;
                try {
                    encodedUrl = URLEncoder.encode(game_url, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    display_error("Unable to send your request", e.getMessage());
                }
                String bodyContent = "action=send_message&message=" + encodedUrl;
                RequestBody body = RequestBody.create(
                        bodyContent,
                        MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8")
                );
                Request request = new Request.Builder()
                        .url("https://analysis-chess.io.vn/wp-admin/admin-ajax.php")
                        .post(body)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        display_error("Failed to send your request", e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String log = "Nothing to show";
                        if (response.body() != null)
                            log = "Response code: " + response.code() + "\nResponse body: " + response.body().string();
                        if (response.isSuccessful() && response.body() != null && !log.contains("\"success\":false")) {
                            runOnUiThread(() -> ((TextView) findViewById(R.id.spinnerTxt)).setText("Request sent successfully\nInitializing game review..."));
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                runOnUiThread(() -> ((TextView) findViewById(R.id.spinnerTxt)).setText("Reviewing...\n(25% completed)"));
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    runOnUiThread(() -> ((TextView) findViewById(R.id.spinnerTxt)).setText("Reviewing...\n(75% completed)"));
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        runOnUiThread(() -> {
                                            String androidId = Settings.Secure.getString(MainActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
                                            FirebaseUtils.getFirestore().collection("users").document(androidId).get().addOnSuccessListener((s) -> {
                                                HashMap<String, String> historyMap = ((HashMap<String, String>) s.get("History"));
                                                historyMap.put(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a")), game_url);
                                                FirebaseUtils.getFirestore().collection("users").document(androidId)
                                                        .update("History", historyMap, "Last game reviewed on", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a")), "Games reviewed till now", ((Long) s.get("Games reviewed till now")) + 1);
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
                                                String redirect_url = game_url.substring(0, 21) + "/analysis/game/" + game_url.substring(22, game_url.indexOf("/game/") + 1) + game_url.substring(32);
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
                                    }, 1000);
                                }, 1500);
                            }, 2500);
                        } else {
                            display_error("Server Error", log);
                        }
                    }
                });
            }
        }).addOnFailureListener(e -> {
            display_error("Cannot verify authority", e.getMessage());
        });
    }
}