package com.example.jobtracker.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// SQLite connection aur tables banana. Ye ek file hai: jobtracker.db
public final class Database {

    private static final String URL = "jdbc:sqlite:jobtracker.db";

    private Database() {}

    public static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    public static void init() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL DEFAULT '',
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'USER',
                    verified INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS otp_tokens (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL,
                    code_hash TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS refresh_tokens (
                    token TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    expires_at INTEGER NOT NULL,
                    revoked INTEGER NOT NULL DEFAULT 0
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS applications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    company TEXT NOT NULL,
                    job_title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    applied_on TEXT NOT NULL,
                    follow_up_on TEXT,
                    job_url TEXT,
                    notes TEXT,
                    created_at INTEGER NOT NULL
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_app_user_status ON applications(user_id, status)");
            st.execute("""
                CREATE TABLE IF NOT EXISTS resumes (
                    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                    filename TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    stored_path TEXT NOT NULL,
                    uploaded_at INTEGER NOT NULL
                )""");
        }
    }
}
