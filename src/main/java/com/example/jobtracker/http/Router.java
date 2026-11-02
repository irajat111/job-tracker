package com.example.jobtracker.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// URL aur method ko handler se match karta hai.
// "/api/applications/:id" mein ":id" ek variable hai (5 ya 12 kuch bhi ho sakta hai).
public final class Router {

    private final List<Route> routes = new ArrayList<>();

    public void add(String method, String pattern, boolean auth, Handler handler) {
        routes.add(new Route(method, segments(pattern), auth, handler));
    }

    public Route find(String method, String path, Map<String, String> params) {
        String[] actual = segments(path);
        boolean pathMatched = false;
        for (Route r : routes) {
            Map<String, String> found = match(r.segments(), actual);
            if (found == null) {
                continue;
            }
            pathMatched = true;
            if (r.method().equals(method)) {
                params.putAll(found);
                return r;
            }
        }
        if (pathMatched) {
            throw new ApiException(405, "Is URL par ye method allowed nahi hai");
        }
        throw new ApiException(404, "Ye URL nahi mila");
    }

    public static String[] segments(String path) {
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.isEmpty() || p.equals("/")) {
            return new String[0];
        }
        return p.substring(1).split("/");
    }

    private static Map<String, String> match(String[] pattern, String[] actual) {
        if (pattern.length != actual.length) {
            return null;
        }
        Map<String, String> found = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i].startsWith(":")) {
                found.put(pattern[i].substring(1), actual[i]);
            } else if (!pattern[i].equals(actual[i])) {
                return null;
            }
        }
        return found;
    }
}
