package com.example.jobtracker.config;

// Settings environment variables se padhte hain. Secrets code mein nahi likhte.
public final class Config {

    private Config() {}

    public static String get(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    public static String jwtSecret() {
        return get("JWT_SECRET", "dev-only-change-this-secret-key-1234567890");
    }

    public static String geminiKey() {
        return get("GEMINI_API_KEY", "AQ.Ab8RN6JpbW_o42DJkwdXQL5jXdEVMNb7SsQf0qZnrj022edf4Q");
    }

    public static String geminiModel() {
        return get("GEMINI_MODEL", "gemini-3.5-flash-lite");
    }

    public static int port() {
        return Integer.parseInt(get("PORT", "8080"));
    }

    public static boolean cookieSecure() {
        return Boolean.parseBoolean(get("COOKIE_SECURE", "false"));
    }
}
