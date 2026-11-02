package com.example.jobtracker.controller;

import com.example.jobtracker.http.ApiException;
import com.example.jobtracker.http.Ctx;
import com.example.jobtracker.http.Router;
import com.example.jobtracker.model.Models.Application;
import com.example.jobtracker.model.Models.User;
import com.example.jobtracker.security.RateLimiter;
import com.example.jobtracker.service.AiService;
import com.example.jobtracker.service.ApplicationService;
import com.example.jobtracker.service.AuthService;
import com.example.jobtracker.validation.Errors;
import com.google.gson.JsonObject;

import java.util.Map;

// AI endpoint: AI sirf draft banata hai, save user ke haath mein hai.
public final class AiController {

    private final AiService ai;
    private final ApplicationService apps;
    private final AuthService auth;
    private final RateLimiter limiter;

    public AiController(AiService ai, ApplicationService apps, AuthService auth, RateLimiter limiter) {
        this.ai = ai;
        this.apps = apps;
        this.auth = auth;
        this.limiter = limiter;
    }

    public void registerRoutes(Router r) {
        r.add("POST", "/api/ai/followup-email", true, this::followUp);
    }

    // POST /api/ai/followup-email  body: {"applicationId": 5}
    private void followUp(Ctx ctx) throws Exception {
        JsonObject b = ctx.body();
        Errors e = new Errors();
        Long id = e.id(b, "applicationId");
        e.throwIfAny();

        // AI ka kharcha hai, isliye user ke hisaab se limit
        if (!limiter.allow("ai:" + ctx.userId())) {
            throw new ApiException(429, "AI ki limit khatam, thodi der baad try karo");
        }

        Application a = apps.get(ctx.userId(), id);

        // Email par applicant ka naam (jo apply kar raha hai) dikhna chahiye
        User user = auth.findById(ctx.userId());
        String applicantName = (user.name() == null || user.name().isBlank()) ? user.email() : user.name();

        ctx.json(200, Map.of("text", ai.followUpEmail(a, applicantName)));
    }
}
