package com.example.jobtracker.http;

import com.example.jobtracker.security.JwtUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// Har request yahan aati hai. Yahi tay karta hai: route kya hai, login chahiye ya nahi,
// error aaye to kaunsa status code jaaye.
public final class Dispatcher implements HttpHandler {

    private final Router router;
    private final JwtUtil jwt;

    public Dispatcher(Router router, JwtUtil jwt) {
        this.router = router;
        this.jwt = jwt;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> params = new HashMap<>();
            Route route = router.find(ex.getRequestMethod(), path, params);

            Ctx ctx = new Ctx(ex, params);
            if (route.auth()) {
                // 401: token nahi hai ya galat/expire hai
                JwtUtil.Claims claims = jwt.verify(bearer(ex))
                        .orElseThrow(() -> new ApiException(401, "Login karo ya token valid nahi hai"));
                ctx.setUser(claims.userId(), claims.role());
            }
            route.handler().handle(ctx);
        } catch (ApiException e) {
            Ctx.writeError(ex, e.status(), e.getMessage(), e.fields());
        } catch (Exception e) {
            e.printStackTrace();
            Ctx.writeError(ex, 500, "Server mein gadbad hui", Map.of());
        }
    }

    private static String bearer(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }
}
