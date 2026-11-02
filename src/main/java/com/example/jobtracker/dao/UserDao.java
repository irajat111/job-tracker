package com.example.jobtracker.dao;

import com.example.jobtracker.db.Database;
import com.example.jobtracker.model.Models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Users table ke SQL. Service ko SQL nahi dikhna chahiye, isliye ye alag class hai.
public final class UserDao {

    public Optional<User> findByEmail(String email) throws SQLException {
        return findOne("SELECT * FROM users WHERE email = ?", email);
    }

    public Optional<User> findById(long id) throws SQLException {
        return findOne("SELECT * FROM users WHERE id = ?", id);
    }

    public long insert(String email, String name, String passwordHash, String role) throws SQLException {
        String sql = "INSERT INTO users (email, name, password_hash, role, verified, created_at) VALUES (?, ?, ?, ?, 0, ?)";
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, name);
            ps.setString(3, passwordHash);
            ps.setString(4, role);
            ps.setLong(5, System.currentTimeMillis() / 1000);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void markVerified(long id) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET verified = 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public List<User> findAll() throws SQLException {
        List<User> out = new ArrayList<>();
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM users ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    private Optional<User> findOne(String sql, Object param) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getInt("verified") == 1,
                rs.getString("name"));
    }
}
