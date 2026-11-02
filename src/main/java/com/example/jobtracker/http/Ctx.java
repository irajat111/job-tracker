package com.example.jobtracker.http;

import com.example.jobtracker.config.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Ek request ka context: body, URL params, cookies, logged-in user, aur response bhejne ke helper.
public final class Ctx {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final HttpExchange ex;
    private final Map<String, String> params;
    private long userId;
    private String role;
    private String rawBody;

    public Ctx(HttpExchange ex, Map<String, String> params) {
        this.ex = ex;
        this.params = params;
    }

    // ---- request ----

    public void setUser(long userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public long userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    public String remoteIp() {
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    public long pathId(String name) {
        try {
            return Long.parseLong(params.get(name));
        } catch (NumberFormatException e) {
            throw new ApiException(400, "ID number hona chahiye");
        }
    }

    public String query(String name) {
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) {
            return null;
        }
        for (String part : raw.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv[0].equals(name)) {
                return kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    public String cookie(String name) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    public JsonObject body() {
        if (rawBody == null) {
            try (InputStream in = ex.getRequestBody()) {
                rawBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ApiException(400, "Request body padh nahi paaye");
            }
        }
        if (rawBody.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement el = JsonParser.parseString(rawBody);
            if (!el.isJsonObject()) {
                throw new ApiException(400, "JSON object bhejo");
            }
            return el.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ApiException(400, "JSON format galat hai");
        }
    }

    // ---- response ----

    public void json(int status, Object body) throws IOException {
        sendBytes(status, "application/json; charset=utf-8", GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    // Raw body bytes (file upload ke liye). limit se badi file par 413.
    public byte[] readBytes(int limit) {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(limit + 1);
            if (data.length > limit) {
                throw new ApiException(413, "File 5 MB se badi hai");
            }
            return data;
        } catch (IOException e) {
            throw new ApiException(400, "File padh nahi paaye");
        }
    }

    public void setHeader(String name, String value) {
        ex.getResponseHeaders().set(name, value);
    }

    public void noContent() throws IOException {
        sendBytes(204, "application/json", new byte[0]);
    }

    public void sendBytes(int status, String contentType, byte[] bytes) throws IOException {
        writeBytes(ex, status, contentType, bytes);
    }

    // Set-Cookie header: HttpOnly (JS nahi padh sakta), SameSite=Strict (CSRF se bachav)
    public void setCookie(String name, String value, long maxAgeSeconds, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value)
                .append("; Path=").append(path)
                .append("; HttpOnly; SameSite=Strict; Max-Age=").append(maxAgeSeconds);
        if (Config.cookieSecure()) {
            sb.append("; Secure");
        }
        ex.getResponseHeaders().add("Set-Cookie", sb.toString());
    }

    public static void writeBytes(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            if (bytes.length > 0) {
                os.write(bytes);
            }
        }
    }

    public static void writeError(HttpExchange ex, int status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        if (fields != null && !fields.isEmpty()) {
            body.put("fields", fields);
        }
        try {
            writeBytes(ex, status, "application/json; charset=utf-8",
                    GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // response shuru ho chuka ho to kuch nahi kar sakte
        }
    }
}
