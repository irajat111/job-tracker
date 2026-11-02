package com.example.jobtracker.controller;

import com.example.jobtracker.http.Ctx;
import com.example.jobtracker.http.Router;
import com.example.jobtracker.service.ApplicationService;

// Applications, dashboard aur reminders. Sab routes login maangte hain.
public final class ApplicationController {

    private final ApplicationService apps;

    public ApplicationController(ApplicationService apps) {
        this.apps = apps;
    }

    public void registerRoutes(Router r) {
        r.add("GET", "/api/applications", true, this::list);
        r.add("POST", "/api/applications", true, this::create);
        r.add("GET", "/api/applications/:id", true, this::get);
        r.add("PATCH", "/api/applications/:id", true, this::update);
        r.add("DELETE", "/api/applications/:id", true, this::delete);
        r.add("GET", "/api/dashboard", true, this::dashboard);
        r.add("GET", "/api/reminders", true, this::reminders);
    }

    // GET /api/applications?status=INTERVIEW
    private void list(Ctx ctx) throws Exception {
        ctx.json(200, apps.list(ctx.userId(), ctx.query("status")));
    }

    // POST /api/applications -> 201
    private void create(Ctx ctx) throws Exception {
        ctx.json(201, apps.create(ctx.userId(), ctx.body()));
    }

    private void get(Ctx ctx) throws Exception {
        ctx.json(200, apps.get(ctx.userId(), ctx.pathId("id")));
    }

    // PATCH /api/applications/5 -> sirf bheji gayi fields badlega
    private void update(Ctx ctx) throws Exception {
        ctx.json(200, apps.update(ctx.userId(), ctx.pathId("id"), ctx.body()));
    }

    // DELETE /api/applications/5 -> 204 (wapas dene ko kuch nahi)
    private void delete(Ctx ctx) throws Exception {
        apps.delete(ctx.userId(), ctx.pathId("id"));
        ctx.noContent();
    }

    private void dashboard(Ctx ctx) throws Exception {
        ctx.json(200, apps.dashboard(ctx.userId()));
    }

    private void reminders(Ctx ctx) throws Exception {
        ctx.json(200, apps.reminders(ctx.userId()));
    }
}
