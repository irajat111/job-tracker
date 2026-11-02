package com.example.jobtracker.http;

// Ek route: method, URL pattern (segments), login chahiye ya nahi, aur handler.
public record Route(String method, String[] segments, boolean auth, Handler handler) {}
