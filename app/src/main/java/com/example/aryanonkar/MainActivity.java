package com.example.aryanonkar;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
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
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            if (clipboardManager.hasPrimaryClip() && clipboardManager.getPrimaryClipDescription().hasMimeType("text/plain")) {
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence clipboardText = clipData.getItemAt(0).getText();
                    if (clipboardText != null) {
                        Pattern pattern = Pattern.compile("https://www.chess.com/([a-zA-Z0-9\\-]+)/game/([a-zA-Z0-9\\-]+)");
                        Matcher matcher = pattern.matcher(clipboardText);
                        String game_url = null;
                        while (matcher.find()) game_url = matcher.group();
                        if (game_url != null) {
                            ((TextInputEditText) findViewById(R.id.urlInp)).setText(clipboardText);
                            Toast.makeText(this, "Game URL pasted from clipboard", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }, 1);

        findViewById(R.id.reviewBtn).setOnClickListener((event) -> {
            if (((TextInputEditText) findViewById(R.id.urlInp)).getText().toString().isBlank()) {
                ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError("This field is required");
                return;
            }
            String raw_url_inp = Objects.requireNonNull(((TextInputEditText) findViewById(R.id.urlInp)).getText()).toString();

            Pattern pattern = Pattern.compile("https://www.chess.com/([a-zA-Z0-9\\-]+)/game/([a-zA-Z0-9\\-]+)");
            Matcher matcher = pattern.matcher(raw_url_inp);
            String game_url = null;
            while (matcher.find()) game_url = matcher.group();

            if (game_url == null) {
                if (!Pattern.matches("^(https?|ftp)://[^\\s/$.?#].\\S*$", raw_url_inp))
                    ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError("Invalid URL");
                else
                    Snackbar.make(this, findViewById(android.R.id.content), "This app can only review games played on chess.com", Snackbar.LENGTH_LONG).show();
                return;
            }

            ((TextInputLayout) findViewById(R.id.urlInpLayout)).setError(null);
            ((TextInputLayout) findViewById(R.id.urlInpLayout)).setErrorEnabled(false);
            findViewById(R.id.urlInp).setEnabled(false);
            findViewById(R.id.reviewBtn).setEnabled(false);
            hideKeyboard();
            findViewById(R.id.spinnerCont).setVisibility(View.VISIBLE);
            findViewById(R.id.chessIconCont).setVisibility(View.GONE);
            findViewById(R.id.statusCont).setVisibility(View.GONE);
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(240, TimeUnit.SECONDS) // Connection timeout
                    .readTimeout(240, TimeUnit.SECONDS)    // Read timeout
                    .writeTimeout(240, TimeUnit.SECONDS).build();

            // Build the request
            Request request = new Request.Builder()
                    .url("https://chess-review-api.onrender.com/game-review/?game-url=" + game_url)
                    .post(RequestBody.create("", null))
                    .build();

            // Make the API call asynchronously
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Handle failure
                    Log.e("API Failure", "Request failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        findViewById(R.id.urlInp).setEnabled(true);
                        findViewById(R.id.reviewBtn).setEnabled(true);
                        findViewById(R.id.spinnerCont).setVisibility(View.GONE);
                        ((ImageView) findViewById(R.id.statusIcon)).setImageResource(R.drawable.error_icon);
                        ((TextView) findViewById(R.id.statusTxt)).setTextColor(getColor(R.color.errorRed));
                        ((TextView) findViewById(R.id.statusTxt)).setText("Failed to send your request");
                        findViewById(R.id.statusCont).setVisibility(View.VISIBLE);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        // Handle success
                        String responseData = response.body().string();
                        Log.d("API Response", "Response Data: " + responseData);

                        // If you need to update the UI, do it on the main thread
                        runOnUiThread(() -> {
                            findViewById(R.id.urlInp).setEnabled(true);
                            findViewById(R.id.reviewBtn).setEnabled(true);
                            ((TextInputEditText) findViewById(R.id.urlInp)).setText("");
                            findViewById(R.id.spinnerCont).setVisibility(View.GONE);
                            ((ImageView) findViewById(R.id.statusIcon)).setImageResource(R.drawable.check_circle_icon);
                            ((TextView) findViewById(R.id.statusTxt)).setTextColor(getColor(R.color.successGreen));
                            ((TextView) findViewById(R.id.statusTxt)).setText("Game reviewed\nsuccessfully");
                            findViewById(R.id.statusCont).setVisibility(View.VISIBLE);
                        });
                    } else {
                        // Handle error
                        Log.e("API Error", "Response Code: " + response.code());
                        runOnUiThread(() -> {
                            findViewById(R.id.urlInp).setEnabled(true);
                            findViewById(R.id.reviewBtn).setEnabled(true);
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