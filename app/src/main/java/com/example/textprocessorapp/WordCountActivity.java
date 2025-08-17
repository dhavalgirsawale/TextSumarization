package com.example.textprocessorapp;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordCountActivity extends AppCompatActivity {
    private TextView wordCountResult;
    private TextView totalWordCount;
    private EditText searchWordInput;
    private String fullText;
    private SpannableString spannableString;
    private int lastHighlightColor = Color.YELLOW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_count);

        fullText = getIntent().getStringExtra("FULL_TEXT");
        wordCountResult = findViewById(R.id.wordCountResult);
        totalWordCount = findViewById(R.id.totalWordCount);
        searchWordInput = findViewById(R.id.searchWordInput);

        spannableString = new SpannableString(fullText);
        TextView originalTextView = findViewById(R.id.originalText); 
        originalTextView.setText(spannableString);

        int totalWords = fullText.split("\\s+").length;
        totalWordCount.setText("Total words: " + totalWords);

        findViewById(R.id.btnSearch).setOnClickListener(v -> highlightAndCount());
    }

    private void highlightAndCount() {
        String searchWord = searchWordInput.getText().toString().trim();
        if (searchWord.isEmpty()) return;

        spannableString = new SpannableString(fullText);

        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(searchWord) + "\\b");
        Matcher matcher = pattern.matcher(fullText);
        int count = 0;

        while (matcher.find()) {
            count++;
            spannableString.setSpan(
                    new BackgroundColorSpan(lastHighlightColor),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        wordCountResult.setText("Word appears: " + count + " times");
        TextView originalTextView = findViewById(R.id.originalText);
        originalTextView.setText(spannableString);

        lastHighlightColor = (lastHighlightColor == Color.YELLOW) ? Color.CYAN : Color.YELLOW;
    }
}
