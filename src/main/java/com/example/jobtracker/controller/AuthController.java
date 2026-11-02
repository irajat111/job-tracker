package com.example.jobtracker.controller;

import com.example.jobtracker.http.ApiException;
import com.example.jobtracker.http.Ctx;
import com.example.jobtracker.http.Router;
import com.example.jobtracker.model.Models.User;
import com.example.jobtracker.security.RateLimiter;
import com.example.jobtracker.service.AuthService;
import com.example.jobtracker.validation.Errors;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Auth ke HTTP endpoints. Har handler: input validate, service call, status code tay karo.
public final class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService auth;
    private final RateLimiter limiter;

    public AuthController(AuthService auth, RateLimiter limiter) {
        this.auth = auth;
        this.limiter = limiter;
    }

    public void registerRoutes(Router r) {
        r.add("POST", "/api/auth/register", false, this::register);
        r.add("POST", "/api/auth/verify-otp", false, this::verifyOtp);
        r.add("POST", "/api/auth/login", false, this::login);
        r.add("POST", "/api/auth/refresh", false, this::refresh);
        r.add("POST", "/api/auth/logout", false, this::logout);
        r.add("GET", "/api/auth/me", true, this::me);
        r.add("GET", "/api/admin/users", true, this::adminUsers);
    }

    // POST /api/auth/register -> 201
    private void register(Ctx ctx) throws Exception {
        JsonObject b = ctx.body();
        Errors e = new Errors();
        String name = e.text(b, "name", true, 100);
        String email = e.email(b, "email");
        String password = e.text(b, "password", true, 100);
        if (password != null && !password.matches("^(?=.*[A-Z])(?=.*\\d).{8,}$")) {
            e.add("password", "Password 8+ characters ka ho, ek capital letter aur ek number ho");
        }
        e.throwIfAny();

        auth.register(email, name, password);
        ctx.json(201, Map.of("message", "Register ho gaya. OTP console mein dikhega, use verify karo"));
    }

    // POST /api/auth/verify-otp -> 200 (brute force se bachne ke liye rate limit)
    private void verifyOtp(Ctx ctx) throws Exception {
        limit(ctx, "verify");
        JsonObject b = ctx.body();
        Errors e = new Errors();
        String email = e.email(b, "email");
        String code = e.text(b, "code", true, 6);
        if (code != null && !code.matches("\\d{6}")) {
            e.add("code", "OTP 6 digit ka hona chahiye");
        }
        e.throwIfAny();

        auth.verifyOtp(email, code);
        ctx.json(200, Map.of("message", "Email verify ho gaya. Ab login karo"));
    }

    // POST /api/auth/login -> access token JSON mein, refresh token HttpOnly cookie mein
    private void login(Ctx ctx) throws Exception {
        limit(ctx, "login");
        JsonObject b = ctx.body();
        Errors e = new Errors();
        String email = e.email(b, "email");
        String password = e.text(b, "password", true, 200);
        e.throwIfAny();

        AuthService.Tokens t = auth.login(email, password);
        ctx.setCookie(REFRESH_COOKIE, t.refreshToken(), AuthService.REFRESH_TTL_SECONDS, COOKIE_PATH);
        ctx.json(200, Map.of("accessToken", t.accessToken(), "email", t.email(), "role", t.role()));
    }

    // POST /api/auth/refresh -> cookie se purana refresh, naya access + naya refresh (rotation)
    private void refresh(Ctx ctx) throws Exception {
        AuthService.Tokens t = auth.refresh(ctx.cookie(REFRESH_COOKIE));
        ctx.setCookie(REFRESH_COOKIE, t.refreshToken(), AuthService.REFRESH_TTL_SECONDS, COOKIE_PATH);
        ctx.json(200, Map.of("accessToken", t.accessToken(), "email", t.email(), "role", t.role()));
    }

    // POST /api/auth/logout -> refresh revoke + cookie delete (Max-Age=0)
    private void logout(Ctx ctx) throws Exception {
        auth.logout(ctx.cookie(REFRESH_COOKIE));
        ctx.setCookie(REFRESH_COOKIE, "", 0, COOKIE_PATH);
        ctx.noContent();
    }

    // GET /api/auth/me (login chahiye)
    private void me(Ctx ctx) throws Exception {
        User u = auth.findById(ctx.userId());
        ctx.json(200, Map.of("email", u.email(), "name", u.name(), "role", u.role(), "verified", u.verified()));
    }

    // GET /api/admin/users (sirf ADMIN): login hai par role nahi -> 403
    private void adminUsers(Ctx ctx) throws Exception {
        if (!"ADMIN".equals(ctx.role())) {
            throw new ApiException(403, "Sirf admin ke liye");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : auth.listUsers()) {
            out.add(Map.<String, Object>of("email", u.email(), "role", u.role(), "verified", u.verified()));
        }
        ctx.json(200, out);
    }

    private void limit(Ctx ctx, String name) {
        if (!limiter.allow(ctx.remoteIp() + ":" + name)) {
            throw new ApiException(429, "Bahut zyada requests, thodi der baad try karo");
        }
    }
}
