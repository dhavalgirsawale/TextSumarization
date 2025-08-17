package com.example.textprocessorapp;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpeechActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private String summaryText;
    private Spinner languageSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech);

        summaryText = getIntent().getStringExtra("SUMMARY_TEXT");
        TextView tvSummary = findViewById(R.id.tvSummary);
        tvSummary.setText(summaryText);

        tts = new TextToSpeech(this, this);

        languageSpinner = findViewById(R.id.spinnerLanguages);
        setupLanguageSpinner();

        findViewById(R.id.btnSpeak).setOnClickListener(v -> speak());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopSpeaking());
    }

    private void setupLanguageSpinner() {
        List<LangItem> languages = new ArrayList<>();
        languages.add(new LangItem("Marathi", "mr-IN"));
        languages.add(new LangItem("Hindi", "hi-IN"));
        languages.add(new LangItem("English", "en-US"));

        ArrayAdapter<LangItem> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Default to English
            tts.setLanguage(Locale.US);
        }
    }

    private void speak() {
        LangItem selectedLang = (LangItem) languageSpinner.getSelectedItem();
        Locale locale = new Locale(selectedLang.getCode().split("-")[0]);

        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(locale);
            tts.speak(summaryText, TextToSpeech.QUEUE_FLUSH, null, "tts1");
        } else {
            Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopSpeaking() {
        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private static class LangItem {
        private final String name;
        private final String code;

        public LangItem(String name, String code) {
            this.name = name;
            this.code = code;
        }

        public String getCode() { return code; }

        @Override
        public String toString() { return name; }
    }
}
