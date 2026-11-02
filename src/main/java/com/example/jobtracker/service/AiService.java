package com.example.jobtracker.service;

import com.example.jobtracker.config.Config;
import com.example.jobtracker.http.ApiException;
import com.example.jobtracker.model.Models.Application;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Google Gemini API: follow-up email draft banata hai. Key environment variable se aati hai.
public final class AiService {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String followUpEmail(Application a, String applicantName) {
        String key = Config.geminiKey();
        if (key.isBlank()) {
            throw new ApiException(503, "AI configured nahi hai. GEMINI_API_KEY environment variable set karo");
        }

        String prompt = "Write a short, polite follow-up email (under 120 words) to a recruiter. "
                + "I applied for the role of " + a.jobTitle() + " at " + a.company()
                + " on " + a.appliedOn() + ". Current status: " + a.status() + ". "
                + "First line must be 'Subject: ...', then a blank line, then the email body. "
                + "The applicant's name is " + applicantName + ". Sign the email with exactly this name. "
                + "Never use placeholders like [Your Name].";

        // Gemini request format: contents -> parts -> text
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject generation = new JsonObject();
        generation.addProperty("maxOutputTokens", 1024);
        JsonObject root = new JsonObject();
        root.add("contents", contents);
        root.add("generationConfig", generation);

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + Config.geminiModel() + ":generateContent"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new ApiException(502, "AI service ne error diya, status: " + res.statusCode());
            }
            return extractText(res.body());
        } catch (IOException e) {
            throw new ApiException(502, "AI service se connect nahi ho paya");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "AI request interrupt ho gayi");
        }
    }

    // Response: candidates[0].content.parts[] -> text (thought wale parts chhod do)
    private static String extractText(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new ApiException(502, "AI se khali jawab aaya");
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null || !content.has("parts")) {
                throw new ApiException(502, "AI ka jawab samajh nahi aaya");
            }
            StringBuilder text = new StringBuilder();
            for (JsonElement el : content.getAsJsonArray("parts")) {
                JsonObject part = el.getAsJsonObject();
                boolean isThought = part.has("thought") && part.get("thought").getAsBoolean();
                if (!isThought && part.has("text")) {
                    text.append(part.get("text").getAsString());
                }
            }
            String result = text.toString().trim();
            if (result.isEmpty()) {
                throw new ApiException(502, "AI se khali jawab aaya");
            }
            return result;
        } catch (RuntimeException e) {
            if (e instanceof ApiException) {
                throw e;
            }
            throw new ApiException(502, "AI ka jawab samajh nahi aaya");
        }
    }
}
