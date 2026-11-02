package com.example.jobtracker.model;

import java.util.Map;

// Data ke records (immutable). Ek file mein sab, taaki imports saaf rahein.
public final class Models {

    private Models() {}

    public record User(long id, String email, String passwordHash, String role, boolean verified, String name) {}

    public record Otp(String codeHash, long expiresAt) {}

    public record RefreshToken(String token, long userId, long expiresAt, boolean revoked) {}

    public record Application(long id, long userId, String company, String jobTitle, String status,
                              String appliedOn, String followUpOn, String jobUrl, String notes) {}

    public record Dashboard(int total, Map<String, Integer> byStatus, int dueReminders) {}

    public record Resume(long userId, String filename, String contentType, long size, String storedPath, long uploadedAt) {}
}
