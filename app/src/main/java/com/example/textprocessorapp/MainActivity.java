package com.example.textprocessorapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
    private EditText inputText;
    private TextView resultText;
    private ApiService apiService;
    private static final int PDF_REQUEST_CODE = 1001;
    private String extractedText = "";
    private LinearLayout translationButtonsLayout;
    private ProgressBar progressBar;

    private ImageView btnCancel;
    private TextView tvUploadStatus;

    // Language codes for the 6 supported languages
    private final String[] languageCodes = {"mr", "hi", "te", "pa"};
    private final String[] languageNames = {"Marathi", "Hindi", "Telugu", "Punjabi"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupRetrofit();
        setupButtonListeners();

        findViewById(R.id.btnRewrite).setOnClickListener(v -> showRewriteDialog());
        resultText = findViewById(R.id.resultText);
        resultText.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.btnSpeech).setOnClickListener(v -> {
            if (!resultText.getText().toString().isEmpty()) {
                Intent intent = new Intent(this, SpeechActivity.class);
                intent.putExtra("SUMMARY_TEXT", resultText.getText().toString());
                startActivity(intent);
            } else {
                Toast.makeText(this, "No text to speak", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeViews() {
        inputText = findViewById(R.id.inputText);
        resultText = findViewById(R.id.resultText);
        translationButtonsLayout = findViewById(R.id.translationButtonsLayout);
        progressBar = findViewById(R.id.progressBar);
        btnCancel = findViewById(R.id.btnCancel);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);
    }

    private void setupRetrofit() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl("https://8a39-106-193-150-43.ngrok-free.app")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    private void setupButtonListeners() {
        findViewById(R.id.btnSummarize).setOnClickListener(v -> summarizeText());
        findViewById(R.id.btnWordCount).setOnClickListener(v -> countWords());
        findViewById(R.id.btnUploadPdf).setOnClickListener(v -> openFilePicker());

        btnCancel.setOnClickListener(v -> resetUI());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        startActivityForResult(intent, PDF_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PDF_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            uploadPdf(data.getData());
        }
    }

    private void uploadPdf(Uri pdfUri) {
        showLoading(true);
        try {
            String fileName = getFileName(pdfUri);
            if (fileName == null) {
                showError("Couldn't get file name");
                return;
            }

            InputStream inputStream = getContentResolver().openInputStream(pdfUri);
            if (inputStream == null) {
                showError("Couldn't open file stream");
                return;
            }

            File file = createTempFile(inputStream, fileName);
            uploadFileToServer(file);
        } catch (Exception e) {
            showError("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private File createTempFile(InputStream inputStream, String fileName) throws IOException {
        File file = new File(getCacheDir(), fileName);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
        return file;
    }

    private void uploadFileToServer(File file) {
        RequestBody requestFile = RequestBody.create(MediaType.parse("application/pdf"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

        apiService.uploadPdf(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                showLoading(false);
                if (response.isSuccessful()) {
                    handleSuccessfulUpload(response);
                } else {
                    showError("Upload failed: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                showLoading(false);
                showError("Upload error: " + t.getMessage());
            }
        });
    }

    private void handleSuccessfulUpload(Response<ResponseBody> response) {
        try {
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            extractedText = jsonResponse.getString("text");
            resultText.setText("Extracted Text: " + extractedText);
            showUploadStatus(true);
        } catch (Exception e) {
            showError("Error parsing response");
        }
    }

    private void summarizeText() {
        String text = getInputText(); // Gets text from EditText or PDF
        if (text.isEmpty()) {
            showError("No text provided");
            return;
        }

        showLoading(true);
        apiService.summarizeText(new SummaryRequest(text)).enqueue(new Callback<SummaryResponse>() {
            @Override
            public void onResponse(Call<SummaryResponse> call, Response<SummaryResponse> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    SummaryResponse summary = response.body();

                    // Handle different summary types
                    if (summary.getSummary().startsWith("[Too Short]")) {
                        resultText.setTextColor(Color.GRAY);
                        resultText.setText(summary.getSummary());
                    }
                    else if (summary.getSummary().startsWith("[Key Points]")) {
                        resultText.setTextColor(Color.BLUE);
                        resultText.setText(summary.getSummary());
                    }
                    else {
                        resultText.setTextColor(Color.BLACK);
                        String result = String.format(
                                "Summary (%d → %d words, %s reduced):\n\n%s",
                                summary.getOriginal_length(),
                                summary.getSummary_length(),
                                summary.getCompression_ratio(),
                                summary.getSummary()
                        );
                        resultText.setText(result);
                    }
                    handleSuccessfulSummary(summary);
                }
            }

            @Override
            public void onFailure(Call<SummaryResponse> call, Throwable t) {

            }
        });
    }

    private void handleSuccessfulSummary(SummaryResponse response) {
        resultText.setText("Summary: " + response.getSummary());
        setupLanguageButtons();
        translationButtonsLayout.setVisibility(View.VISIBLE);
    }

    private void setupLanguageButtons() {
        translationButtonsLayout.removeAllViews();

        for (int i = 0; i < languageCodes.length; i++) {
            Button button = new Button(this);
            button.setText(languageNames[i]);
            int finalI = i;
            button.setOnClickListener(v -> translateText(languageCodes[finalI]));
            translationButtonsLayout.addView(button);
        }
    }

    private void translateText(String langCode) {
        String textToTranslate = getCleanTextForTranslation();
        showLoading(true);

        apiService.translateText(new TranslationRequest(textToTranslate, langCode))
                .enqueue(new Callback<TranslationResponse>() {
                    @Override
                    public void onResponse(Call<TranslationResponse> call, Response<TranslationResponse> response) {
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            resultText.setText(languageNames[Arrays.asList(languageCodes).indexOf(langCode)]
                                    + ": " + response.body().getTranslatedText());
                        } else {
                            showError("Translation failed");
                        }
                    }

                    @Override
                    public void onFailure(Call<TranslationResponse> call, Throwable t) {
                        showLoading(false);
                        showError("Translation error");
                    }
                });
    }

    private String getCleanTextForTranslation() {
        return resultText.getText().toString()
                .replace("Summary: ", "")
                .replace("Extracted Text: ", "");
    }

    private String getInputText() {
        String text = inputText.getText().toString().trim();
        return !text.isEmpty() ? text : extractedText;
    }

    private void countWords() {
        String text = getInputText();
        if (text.isEmpty()) {
            Toast.makeText(this, "No text to analyze", Toast.LENGTH_SHORT).show();
        } else {
            startActivity(new Intent(this, WordCountActivity.class)
                    .putExtra("FULL_TEXT", text));
        }
    }

    private void resetUI() {
        extractedText = "";
        resultText.setText("");
        showUploadStatus(false);
        translationButtonsLayout.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        resultText.setText(message);
    }

    private void showUploadStatus(boolean show) {
        tvUploadStatus.setVisibility(show ? View.VISIBLE : View.GONE);
        btnCancel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    private void displaySummary(String text) {
        SpannableString spannable = new SpannableString(text);

        // Make headings bold
        if (text.contains("Key Points:")) {
            int start = text.indexOf("Key Points:");
            spannable.setSpan(new StyleSpan(Typeface.BOLD),
                    start, start + 11,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        resultText.setText(spannable);
    }

    // Add this method to show style selection dialog
    private void showRewriteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Writing Style");

        String[] styles = new String[]{
                "Professional",
                "Casual",
                "Academic",
                "Simplified"
        };

        builder.setItems(styles, (dialog, which) -> {
            String style = styles[which].toLowerCase();
            rewriteText(style);
        });

        builder.show();
    }

    // Add this method to handle API call
    private void rewriteText(String style) {
        String text = getInputText();
        if (text.isEmpty()) {
            showError("No text to rewrite");
            return;
        }

        showLoading(true);
        apiService.rewriteText(new RewriteRequest(text, style))
                .enqueue(new Callback<RewriteResponse>() {
                    @Override
                    public void onResponse(Call<RewriteResponse> call, Response<RewriteResponse> response) {
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            displayRewriteResult(response.body());
                        } else {
                            showError("Rewrite failed");
                        }
                    }

                    @Override
                    public void onFailure(Call<RewriteResponse> call, Throwable t) {
                        showLoading(false);
                        showError("Network error: " + t.getMessage());
                    }
                });
    }

    // Add this method to display results
    private void displayRewriteResult(RewriteResponse response) {
        String result = String.format(
                "【%s Version】\n%s\n\nOriginal:\n%s",
                response.getStyle().toUpperCase(),
                response.getRewrittenText(),
                response.getOriginalText()
        );

        // Make style header bold
        SpannableString spannable = new SpannableString(result);
        spannable.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                response.getStyle().length() + 12, // Length of 【PROFESSIONAL Version】
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        resultText.setText(spannable);
    }

}