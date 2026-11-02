package com.example.jobtracker.dao;

import com.example.jobtracker.db.Database;
import com.example.jobtracker.model.Models.Otp;
import com.example.jobtracker.model.Models.RefreshToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

// OTP aur refresh tokens ka SQL
public final class TokenDao {

    public void saveOtp(String email, String codeHash, long expiresAt, long createdAt) throws SQLException {
        String sql = "INSERT INTO otp_tokens (email, code_hash, expires_at, created_at) VALUES (?, ?, ?, ?)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, codeHash);
            ps.setLong(3, expiresAt);
            ps.setLong(4, createdAt);
            ps.executeUpdate();
        }
    }

    public Optional<Otp> latestOtp(String email) throws SQLException {
        String sql = "SELECT code_hash, expires_at FROM otp_tokens WHERE email = ? ORDER BY id DESC LIMIT 1";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Otp(rs.getString("code_hash"), rs.getLong("expires_at")))
                        : Optional.empty();
            }
        }
    }

    public void deleteOtps(String email) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM otp_tokens WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
    }

    public void saveRefresh(String token, long userId, long expiresAt) throws SQLException {
        String sql = "INSERT INTO refresh_tokens (token, user_id, expires_at, revoked) VALUES (?, ?, ?, 0)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setLong(2, userId);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        }
    }

    public Optional<RefreshToken> findRefresh(String token) throws SQLException {
        String sql = "SELECT token, user_id, expires_at, revoked FROM refresh_tokens WHERE token = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new RefreshToken(
                                rs.getString("token"),
                                rs.getLong("user_id"),
                                rs.getLong("expires_at"),
                                rs.getInt("revoked") == 1))
                        : Optional.empty();
            }
        }
    }

    public void revokeRefresh(String token) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE refresh_tokens SET revoked = 1 WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    public void revokeAllForUser(long userId) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }
}
