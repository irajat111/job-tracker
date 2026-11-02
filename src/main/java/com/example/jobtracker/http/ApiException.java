package com.example.jobtracker.http;

import java.util.Map;

// Ek hi exception jo HTTP status code aur message le jaata hai.
// Dispatcher ise JSON response mein badal deta hai.
public final class ApiException extends RuntimeException {

    private final int status;
    private final Map<String, String> fields;

    public ApiException(int status, String message) {
        this(status, message, Map.of());
    }

    public ApiException(int status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields;
    }

    public int status() {
        return status;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
