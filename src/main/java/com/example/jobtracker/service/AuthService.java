package com.example.jobtracker.service;

import com.example.jobtracker.dao.TokenDao;
import com.example.jobtracker.dao.UserDao;
import com.example.jobtracker.http.ApiException;
import com.example.jobtracker.model.Models.Otp;
import com.example.jobtracker.model.Models.RefreshToken;
import com.example.jobtracker.model.Models.User;
import com.example.jobtracker.security.JwtUtil;
import com.example.jobtracker.security.PasswordHasher;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

// Register, OTP verify, login, refresh rotation, logout. Business rules yahin hain.
public final class AuthService {

    public record Tokens(String accessToken, String refreshToken, String email, String role) {}

    public static final long REFRESH_TTL_SECONDS = 7L * 24 * 3600;
    private static final long OTP_TTL_SECONDS = 5 * 60;

    private final UserDao users;
    private final TokenDao tokens;
    private final JwtUtil jwt;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserDao users, TokenDao tokens, JwtUtil jwt) {
        this.users = users;
        this.tokens = tokens;
        this.jwt = jwt;
    }

    // Pehli baar app chalne par admin user bana do (demo ke liye)
    public void ensureAdmin() throws SQLException {
        if (users.findByEmail("admin@example.com").isPresent()) {
            return;
        }
        long id = users.insert("admin@example.com", "Admin", PasswordHasher.hash("Admin@1234"), "ADMIN");
        users.markVerified(id);
        System.out.println("Admin ready -> admin@example.com / Admin@1234");
    }

    public void register(String email, String name, String password) throws SQLException {
        if (users.findByEmail(email).isPresent()) {
            throw new ApiException(409, "Ye email pehle se registered hai");
        }
        users.insert(email, name, PasswordHasher.hash(password), "USER");
        sendOtp(email);
    }

    public void verifyOtp(String email, String code) throws SQLException {
        Otp otp = tokens.latestOtp(email)
                .orElseThrow(() -> new ApiException(422, "OTP nahi mila, dobara register karo"));
        if (otp.expiresAt() < now()) {
            throw new ApiException(422, "OTP expire ho gaya");
        }
        if (!PasswordHasher.verify(code, otp.codeHash())) {
            throw new ApiException(422, "Galat OTP");
        }
        User u = users.findByEmail(email).orElseThrow(() -> new ApiException(404, "User nahi mila"));
        users.markVerified(u.id());
        tokens.deleteOtps(email);
    }

    public Tokens login(String email, String password) throws SQLException {
        // Email galat ho ya password, dono par same message (hacker ko pata na chale)
        User u = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(401, "Email ya password galat hai"));
        if (!PasswordHasher.verify(password, u.passwordHash())) {
            throw new ApiException(401, "Email ya password galat hai");
        }
        if (!u.verified()) {
            throw new ApiException(403, "Pehle email verify karo");
        }
        return issue(u);
    }

    // Rotation: purana refresh token band, naya milega.
    // Agar revoked token dobara aaye to chori maana jaata hai, us user ke saare sessions band.
    public Tokens refresh(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            throw new ApiException(401, "Refresh token nahi mila");
        }
        RefreshToken rt = tokens.findRefresh(token)
                .orElseThrow(() -> new ApiException(401, "Refresh token invalid hai"));
        if (rt.revoked()) {
            tokens.revokeAllForUser(rt.userId());
            throw new ApiException(401, "Session compromised ho sakta hai, dobara login karo");
        }
        if (rt.expiresAt() < now()) {
            throw new ApiException(401, "Refresh token expire ho gaya, dobara login karo");
        }
        tokens.revokeRefresh(token);
        User u = users.findById(rt.userId()).orElseThrow(() -> new ApiException(401, "User nahi mila"));
        return issue(u);
    }

    public void logout(String token) throws SQLException {
        if (token != null && !token.isBlank()) {
            tokens.revokeRefresh(token);
        }
    }

    public User findById(long id) throws SQLException {
        return users.findById(id).orElseThrow(() -> new ApiException(404, "User nahi mila"));
    }

    public List<User> listUsers() throws SQLException {
        return users.findAll();
    }

    private Tokens issue(User u) throws SQLException {
        String refresh = newToken();
        tokens.saveRefresh(refresh, u.id(), now() + REFRESH_TTL_SECONDS);
        return new Tokens(jwt.create(u.id(), u.role()), refresh, u.email(), u.role());
    }

    // OTP sirf hash store hota hai. Demo ke liye console mein dikhaya jaata hai (email ki jagah).
    private void sendOtp(String email) throws SQLException {
        String code = String.format("%06d", random.nextInt(1_000_000));
        tokens.deleteOtps(email);
        tokens.saveOtp(email, PasswordHasher.hash(code), now() + OTP_TTL_SECONDS, now());
        System.out.println("DEV MODE: OTP for " + email + " is " + code);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
