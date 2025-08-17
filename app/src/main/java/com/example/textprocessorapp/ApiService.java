package com.example.textprocessorapp;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.http.Multipart;
import retrofit2.http.Part;

public interface ApiService {
    @POST("/summarize")
    Call<SummaryResponse> summarizeText(@Body SummaryRequest request);
    @Multipart
    @POST("/upload-pdf")
    Call<ResponseBody> uploadPdf(@Part MultipartBody.Part file);

    @POST("/translate")
    Call<TranslationResponse> translateText(@Body TranslationRequest request);

    @POST("/rewrite")
    Call<RewriteResponse> rewriteText(@Body RewriteRequest request);
}

class SummaryRequest {
    String text;
    SummaryRequest(String text) { this.text = text; }
}

 class SummaryResponse {
    private String summary;
    private int original_length;
    private int summary_length;
    private String compression_ratio;

     public String getSummary() {
         return summary;
     }

     public void setSummary(String summary) {
         this.summary = summary;
     }

     public int getOriginal_length() {
         return original_length;
     }

     public void setOriginal_length(int original_length) {
         this.original_length = original_length;
     }

     public int getSummary_length() {
         return summary_length;
     }

     public void setSummary_length(int summary_length) {
         this.summary_length = summary_length;
     }

     public String getCompression_ratio() {
         return compression_ratio;
     }

     public void setCompression_ratio(String compression_ratio) {
         this.compression_ratio = compression_ratio;
     }
}
class TranslationRequest {
    String text;
    String lang; // Language codes: hi/mr/te/pa/sa/bh
    TranslationRequest(String text, String lang) {
        this.text = text;
        this.lang = lang;
    }
}

class TranslationResponse {
    String translated_text;
    public String getTranslatedText() { return translated_text; }
}
class RewriteRequest {
    String text;
    String style; // "professional", "casual", "academic"

    public RewriteRequest(String text, String style) {
        this.text = text;
        this.style = style;
    }
}

class RewriteResponse {
    String original_text;
    String rewritten_text;
    String style;

    public String getOriginalText() { return original_text; }
    public String getRewrittenText() { return rewritten_text; }
    public String getStyle() { return style; }
}
class SessionResponse {
    String session_id;

    public String getSession_id() { return session_id; }


}

