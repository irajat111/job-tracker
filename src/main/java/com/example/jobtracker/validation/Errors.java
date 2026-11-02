package com.example.jobtracker.validation;

import com.example.jobtracker.http.ApiException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// Validation ek jagah: sab field ke errors jama karo, end mein ek hi 422 bhejo.
// Hum value return karte hain, error sirf map mein jaata hai, isliye throwIfAny() zaroor call karo.
public final class Errors {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Map<String, String> map = new LinkedHashMap<>();

    public void add(String field, String message) {
        map.putIfAbsent(field, message);
    }

    public void throwIfAny() {
        if (!map.isEmpty()) {
            throw new ApiException(422, "Validation failed", map);
        }
    }

    // Absent/null -> null. Empty string -> "" (caller decide karega). Required + empty -> error.
    public String text(JsonObject b, String field, boolean required, int max) {
        JsonElement el = b.get(field);
        if (el == null || el.isJsonNull()) {
            if (required) {
                add(field, "Ye field zaroori hai");
            }
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            add(field, "Text hona chahiye");
            return null;
        }
        String v = el.getAsString().trim();
        if (required && v.isEmpty()) {
            add(field, "Ye field khali nahi ho sakti");
            return null;
        }
        if (v.length() > max) {
            add(field, "Maximum " + max + " characters ho sakte hain");
            return null;
        }
        return v;
    }

    public String email(JsonObject b, String field) {
        String v = text(b, field, true, 254);
        if (v == null) {
            return null;
        }
        if (!EMAIL.matcher(v).matches()) {
            add(field, "Email format sahi nahi hai");
            return null;
        }
        return v.toLowerCase(Locale.ROOT);
    }

    public String date(JsonObject b, String field) {
        String v = text(b, field, false, 10);
        if (v == null || v.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(v).toString();
        } catch (DateTimeParseException e) {
            add(field, "Date YYYY-MM-DD format mein ho");
            return null;
        }
    }

    public String status(JsonObject b, String field, Collection<String> allowed) {
        String v = text(b, field, false, 20);
        if (v == null || v.isEmpty()) {
            return null;
        }
        String up = v.toUpperCase(Locale.ROOT);
        if (!allowed.contains(up)) {
            add(field, "Allowed values: " + String.join(", ", allowed));
            return null;
        }
        return up;
    }

    public String url(JsonObject b, String field) {
        String v = text(b, field, false, 300);
        if (v == null || v.isEmpty()) {
            return v;
        }
        if (!v.startsWith("https://") && !v.startsWith("http://")) {
            add(field, "URL http:// ya https:// se shuru hona chahiye");
        }
        return v;
    }

    public Long id(JsonObject b, String field) {
        JsonElement el = b.get(field);
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            add(field, "Number (ID) hona chahiye");
            return null;
        }
        return el.getAsLong();
    }
}
