package com.example.jobtracker.dao;

import com.example.jobtracker.db.Database;
import com.example.jobtracker.model.Models.Application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Applications ka SQL. Dashboard ka GROUP BY aur reminders ka WHERE yahin hain.
public final class ApplicationDao {

    public long insert(Application a) throws SQLException {
        String sql = """
            INSERT INTO applications (user_id, company, job_title, status, applied_on, follow_up_on, job_url, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.userId());
            ps.setString(2, a.company());
            ps.setString(3, a.jobTitle());
            ps.setString(4, a.status());
            ps.setString(5, a.appliedOn());
            ps.setString(6, a.followUpOn());
            ps.setString(7, a.jobUrl());
            ps.setString(8, a.notes());
            ps.setLong(9, System.currentTimeMillis() / 1000);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<Application> findById(long id) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM applications WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Application> list(long userId, String status) throws SQLException {
        String sql = status == null
                ? "SELECT * FROM applications WHERE user_id = ? ORDER BY id DESC"
                : "SELECT * FROM applications WHERE user_id = ? AND status = ? ORDER BY id DESC";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            if (status != null) {
                ps.setString(2, status);
            }
            return collect(ps);
        }
    }

    public void update(Application a) throws SQLException {
        String sql = """
            UPDATE applications SET company = ?, job_title = ?, status = ?, applied_on = ?,
                   follow_up_on = ?, job_url = ?, notes = ? WHERE id = ?""";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.company());
            ps.setString(2, a.jobTitle());
            ps.setString(3, a.status());
            ps.setString(4, a.appliedOn());
            ps.setString(5, a.followUpOn());
            ps.setString(6, a.jobUrl());
            ps.setString(7, a.notes());
            ps.setLong(8, a.id());
            ps.executeUpdate();
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM applications WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // Dashboard: SQL GROUP BY
    public Map<String, Integer> countByStatus(long userId) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        String sql = "SELECT status, COUNT(*) AS n FROM applications WHERE user_id = ? GROUP BY status";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("status"), rs.getInt("n"));
                }
            }
        }
        return out;
    }

    // Reminders: follow-up ki date aaj ya usse pehle ki, aur abhi bhi active status
    public List<Application> dueReminders(long userId, String today) throws SQLException {
        String sql = """
            SELECT * FROM applications
            WHERE user_id = ? AND follow_up_on IS NOT NULL AND follow_up_on <= ?
              AND status IN ('APPLIED', 'INTERVIEW')
            ORDER BY follow_up_on""";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, today);
            return collect(ps);
        }
    }

    private static List<Application> collect(PreparedStatement ps) throws SQLException {
        List<Application> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    private static Application map(ResultSet rs) throws SQLException {
        return new Application(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("company"),
                rs.getString("job_title"),
                rs.getString("status"),
                rs.getString("applied_on"),
                rs.getString("follow_up_on"),
                rs.getString("job_url"),
                rs.getString("notes"));
    }
}
