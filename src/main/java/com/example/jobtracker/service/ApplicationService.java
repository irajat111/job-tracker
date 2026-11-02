package com.example.jobtracker.service;

import com.example.jobtracker.dao.ApplicationDao;
import com.example.jobtracker.http.ApiException;
import com.example.jobtracker.model.Models.Application;
import com.example.jobtracker.model.Models.Dashboard;
import com.example.jobtracker.validation.Errors;
import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Job applications ka business logic: validation, ownership check, dashboard, reminders.
public final class ApplicationService {

    public static final List<String> STATUSES = List.of("APPLIED", "INTERVIEW", "OFFER", "REJECTED");

    private final ApplicationDao dao;

    public ApplicationService(ApplicationDao dao) {
        this.dao = dao;
    }

    public Application create(long userId, JsonObject b) throws SQLException {
        Errors e = new Errors();
        String company = e.text(b, "company", true, 100);
        String jobTitle = e.text(b, "jobTitle", true, 100);
        String status = e.status(b, "status", STATUSES);
        String appliedOn = e.date(b, "appliedOn");
        String followUpOn = e.date(b, "followUpOn");
        String jobUrl = e.url(b, "jobUrl");
        String notes = e.text(b, "notes", false, 1000);
        e.throwIfAny();

        Application a = new Application(
                0,
                userId,
                company,
                jobTitle,
                status != null ? status : "APPLIED",
                appliedOn != null ? appliedOn : LocalDate.now().toString(),
                followUpOn,
                blank(jobUrl),
                blank(notes));

        long id = dao.insert(a);
        return dao.findById(id).orElseThrow(() -> new ApiException(500, "Insert ke baad record nahi mila"));
    }

    public List<Application> list(long userId, String status) throws SQLException {
        String filter = null;
        if (status != null && !status.isBlank()) {
            filter = status.trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(filter)) {
                throw new ApiException(422, "status filter galat hai, allowed: " + STATUSES);
            }
        }
        return dao.list(userId, filter);
    }

    public Application get(long userId, long id) throws SQLException {
        return owned(userId, id);
    }

    // PATCH: jo field body mein aayi hai sirf wahi badlo, baaki jaisi thi waisi rahe
    public Application update(long userId, long id, JsonObject b) throws SQLException {
        Application cur = owned(userId, id);
        Errors e = new Errors();

        String company = b.has("company") ? keep(e.text(b, "company", true, 100), cur.company()) : cur.company();
        String jobTitle = b.has("jobTitle") ? keep(e.text(b, "jobTitle", true, 100), cur.jobTitle()) : cur.jobTitle();
        String status = b.has("status") ? keep(e.status(b, "status", STATUSES), cur.status()) : cur.status();
        String appliedOn = b.has("appliedOn") ? keep(e.date(b, "appliedOn"), cur.appliedOn()) : cur.appliedOn();
        // Empty string bheja to date clear ho jayegi
        String followUpOn = b.has("followUpOn") ? e.date(b, "followUpOn") : cur.followUpOn();
        String jobUrl = b.has("jobUrl") ? blank(e.url(b, "jobUrl")) : cur.jobUrl();
        String notes = b.has("notes") ? blank(e.text(b, "notes", false, 1000)) : cur.notes();
        e.throwIfAny();

        Application next = new Application(cur.id(), userId, company, jobTitle, status,
                appliedOn, followUpOn, jobUrl, notes);
        dao.update(next);
        return next;
    }

    public void delete(long userId, long id) throws SQLException {
        owned(userId, id);
        dao.delete(id);
    }

    // Dashboard: har status ka count (0 bhi dikhega), total, aur aaj ke reminders
    public Dashboard dashboard(long userId) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : STATUSES) {
            counts.put(s, 0);
        }
        counts.putAll(dao.countByStatus(userId));
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int due = dao.dueReminders(userId, LocalDate.now().toString()).size();
        return new Dashboard(total, counts, due);
    }

    public List<Application> reminders(long userId) throws SQLException {
        return dao.dueReminders(userId, LocalDate.now().toString());
    }

    // Ownership: 404 agar record hi nahi, 403 agar record hai par tumhara nahi
    private Application owned(long userId, long id) throws SQLException {
        Application a = dao.findById(id).orElseThrow(() -> new ApiException(404, "Application nahi mili"));
        if (a.userId() != userId) {
            throw new ApiException(403, "Ye application tumhari nahi hai");
        }
        return a;
    }

    private static String keep(String value, String old) {
        return value == null ? old : value;
    }

    private static String blank(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
