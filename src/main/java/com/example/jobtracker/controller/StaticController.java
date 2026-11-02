package com.example.jobtracker.controller;

import com.example.jobtracker.http.ApiException;
import com.example.jobtracker.http.Ctx;
import com.example.jobtracker.http.Router;

import java.io.IOException;
import java.io.InputStream;

// Frontend files serve karta hai. Sirf ye files milengi, koi bhi path nahi (path traversal se bachav).
public final class StaticController {

    public void registerRoutes(Router r) {
        r.add("GET", "/", false, ctx -> serve(ctx, "/static/index.html", "text/html; charset=utf-8"));
        r.add("GET", "/index.html", false, ctx -> serve(ctx, "/static/index.html", "text/html; charset=utf-8"));
        r.add("GET", "/style.css", false, ctx -> serve(ctx, "/static/style.css", "text/css; charset=utf-8"));
        r.add("GET", "/app.js", false, ctx -> serve(ctx, "/static/app.js", "application/javascript; charset=utf-8"));
    }

    private void serve(Ctx ctx, String resource, String contentType) throws IOException {
        try (InputStream in = StaticController.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ApiException(404, "File nahi mili");
            }
            ctx.sendBytes(200, contentType, in.readAllBytes());
        }
    }
}
