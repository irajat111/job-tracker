package com.example.jobtracker.dao;

import com.example.jobtracker.db.Database;
import com.example.jobtracker.model.Models.Resume;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

// Resume ka metadata DB mein, file disk par (uploads/ folder mein).
public final class ResumeDao {

    // Ek user ka ek hi resume: naya upload ho to purana replace (UPSERT)
    public void save(Resume r) throws SQLException {
        String sql = """
            INSERT INTO resumes (user_id, filename, content_type, size, stored_path, uploaded_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                filename = excluded.filename,
                content_type = excluded.content_type,
                size = excluded.size,
                stored_path = excluded.stored_path,
                uploaded_at = excluded.uploaded_at""";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, r.userId());
            ps.setString(2, r.filename());
            ps.setString(3, r.contentType());
            ps.setLong(4, r.size());
            ps.setString(5, r.storedPath());
            ps.setLong(6, r.uploadedAt());
            ps.executeUpdate();
        }
    }

    public Optional<Resume> find(long userId) throws SQLException {
        String sql = "SELECT * FROM resumes WHERE user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Resume(
                        rs.getLong("user_id"),
                        rs.getString("filename"),
                        rs.getString("content_type"),
                        rs.getLong("size"),
                        rs.getString("stored_path"),
                        rs.getLong("uploaded_at")));
            }
        }
    }

    public void delete(long userId) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM resumes WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }
}
