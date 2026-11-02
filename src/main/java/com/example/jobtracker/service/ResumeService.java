package com.example.jobtracker.service;

import com.example.jobtracker.dao.ResumeDao;
import com.example.jobtracker.http.ApiException;
import com.example.jobtracker.model.Models.Resume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

// Resume upload: sirf PDF ya DOCX, 5 MB tak. File ka asli content bhi check karte hain (sirf extension nahi).
public final class ResumeService {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final Path DIR = Path.of("uploads");
    private static final String PDF_TYPE = "application/pdf";
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ResumeDao dao;

    public ResumeService(ResumeDao dao) {
        this.dao = dao;
    }

    public Resume upload(long userId, String rawName, byte[] data) throws SQLException, IOException {
        if (data.length == 0) {
            throw new ApiException(422, "File khali hai");
        }
        String name = sanitize(rawName);
        String lower = name.toLowerCase(Locale.ROOT);

        String ext;
        String type;
        if (lower.endsWith(".pdf")) {
            // PDF files "%PDF" se shuru hoti hain
            if (!startsWith(data, "%PDF".getBytes(StandardCharsets.US_ASCII))) {
                throw new ApiException(422, "File PDF nahi lag rahi");
            }
            ext = "pdf";
            type = PDF_TYPE;
        } else if (lower.endsWith(".docx")) {
            // DOCX asal mein zip file hoti hai, "PK" se shuru
            if (!startsWith(data, new byte[] {0x50, 0x4B, 0x03, 0x04})) {
                throw new ApiException(422, "File DOCX nahi lag rahi");
            }
            ext = "docx";
            type = DOCX_TYPE;
        } else {
            throw new ApiException(422, "Sirf PDF ya DOCX file upload kar sakte ho");
        }

        Files.createDirectories(DIR);
        Path target = DIR.resolve(userId + "." + ext);
        Optional<Resume> old = dao.find(userId);
        Files.write(target, data);
        if (old.isPresent() && !old.get().storedPath().equals(target.toString())) {
            Files.deleteIfExists(Path.of(old.get().storedPath()));
        }
        dao.save(new Resume(userId, name, type, data.length, target.toString(), Instant.now().getEpochSecond()));
        return dao.find(userId).orElseThrow(() -> new ApiException(500, "Save ke baad resume nahi mila"));
    }

    public Resume get(long userId) throws SQLException {
        return dao.find(userId).orElseThrow(() -> new ApiException(404, "Koi resume upload nahi hua"));
    }

    public byte[] read(Resume r) throws IOException {
        return Files.readAllBytes(Path.of(r.storedPath()));
    }

    public void delete(long userId) throws SQLException, IOException {
        Resume r = get(userId);
        Files.deleteIfExists(Path.of(r.storedPath()));
        dao.delete(userId);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    // Path traversal se bachav: folder ka hissa hata do, sirf safe characters rakho
    static String sanitize(String raw) {
        String n = (raw == null || raw.isBlank()) ? "resume" : raw.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.length() > 100) {
            n = n.substring(n.length() - 100);
        }
        return n.isBlank() ? "resume" : n;
    }
}
