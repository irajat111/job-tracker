package com.example.jobtracker.controller;

import com.example.jobtracker.http.Ctx;
import com.example.jobtracker.http.Router;
import com.example.jobtracker.model.Models.Resume;
import com.example.jobtracker.service.ResumeService;

import java.util.Map;

// Resume endpoints. Login chahiye. File raw bytes mein aati hai (body = file).
public final class ResumeController {

    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    public void registerRoutes(Router r) {
        r.add("POST", "/api/resume", true, this::upload);
        r.add("GET", "/api/resume", true, this::info);
        r.add("GET", "/api/resume/download", true, this::download);
        r.add("DELETE", "/api/resume", true, this::delete);
    }

    // POST /api/resume?filename=Sen_Resume.pdf  (body = file ke bytes) -> 201
    private void upload(Ctx ctx) throws Exception {
        String filename = ctx.query("filename");
        byte[] data = ctx.readBytes(ResumeService.MAX_BYTES);
        Resume r = resumes.upload(ctx.userId(), filename, data);
        ctx.json(201, view(r));
    }

    private void info(Ctx ctx) throws Exception {
        ctx.json(200, view(resumes.get(ctx.userId())));
    }

    // GET /api/resume/download -> file attachment ke roop mein
    private void download(Ctx ctx) throws Exception {
        Resume r = resumes.get(ctx.userId());
        ctx.setHeader("Content-Disposition", "attachment; filename=\"" + r.filename() + "\"");
        ctx.setHeader("X-Content-Type-Options", "nosniff");
        ctx.sendBytes(200, r.contentType(), resumes.read(r));
    }

    private void delete(Ctx ctx) throws Exception {
        resumes.delete(ctx.userId());
        ctx.noContent();
    }

    // File ka asli path response mein nahi jaata, sirf jaankari
    private static Map<String, Object> view(Resume r) {
        return Map.<String, Object>of(
                "filename", r.filename(),
                "contentType", r.contentType(),
                "size", r.size(),
                "uploadedAt", r.uploadedAt());
    }
}
