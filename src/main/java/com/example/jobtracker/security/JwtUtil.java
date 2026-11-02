package com.example.jobtracker.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

// JWT (HS256) khud se: header.payload.signature
// Signature = HMAC-SHA256(header.payload, secret). Payload badla to signature mismatch.
public final class JwtUtil {

    public record Claims(long userId, String role) {}

    private final byte[] key;
    private final long ttlSeconds;

    public JwtUtil(String secret, long ttlSeconds) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String create(long userId, String role) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String header = enc("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = enc("{\"sub\":\"" + userId + "\",\"role\":\"" + role + "\",\"exp\":" + exp + "}");
        String data = header + "." + payload;
        return data + "." + sign(data);
    }

    public Optional<Claims> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String expected = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject p = JsonParser.parseString(json).getAsJsonObject();
            if (Instant.now().getEpochSecond() > p.get("exp").getAsLong()) {
                return Optional.empty();
            }
            return Optional.of(new Claims(
                    Long.parseLong(p.get("sub").getAsString()),
                    p.get("role").getAsString()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String enc(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
