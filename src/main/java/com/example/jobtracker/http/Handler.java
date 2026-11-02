package com.example.jobtracker.http;

// Ek endpoint ka kaam. Lambda ya method reference se bana sakte ho.
@FunctionalInterface
public interface Handler {
    void handle(Ctx ctx) throws Exception;
}
