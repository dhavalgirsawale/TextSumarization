package com.example.textprocessorapp;
public enum RewriteStyle {
    PROFESSIONAL("Professional"),
    CASUAL("Casual"),
    ACADEMIC("Academic"),
    SIMPLIFIED("Simplified");

    private final String displayName;

    RewriteStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    // For API calls
    public String getApiValue() {
        return name().toLowerCase();
    }
}
