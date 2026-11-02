package com.example.jobtracker;

import com.example.jobtracker.config.Config;
import com.example.jobtracker.controller.AiController;
import com.example.jobtracker.controller.ApplicationController;
import com.example.jobtracker.controller.AuthController;
import com.example.jobtracker.controller.ResumeController;
import com.example.jobtracker.controller.StaticController;
import com.example.jobtracker.dao.ApplicationDao;
import com.example.jobtracker.dao.ResumeDao;
import com.example.jobtracker.dao.TokenDao;
import com.example.jobtracker.dao.UserDao;
import com.example.jobtracker.db.Database;
import com.example.jobtracker.http.Dispatcher;
import com.example.jobtracker.http.Router;
import com.example.jobtracker.security.JwtUtil;
import com.example.jobtracker.security.RateLimiter;
import com.example.jobtracker.service.AiService;
import com.example.jobtracker.service.ApplicationService;
import com.example.jobtracker.service.AuthService;
import com.example.jobtracker.service.ResumeService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

// App ka starting point. Yahan sab cheezein jodi (wire) jaati hain, koi framework nahi.
public final class JobTrackerApplication {

    public static void main(String[] args) throws Exception {
        Database.init();

        // Objects khud banaye aur constructor se pass kiye: ye hi Spring ka dependency injection hai, hand-made
        JwtUtil jwt = new JwtUtil(Config.jwtSecret(), 15 * 60);
        AuthService auth = new AuthService(new UserDao(), new TokenDao(), jwt);
        auth.ensureAdmin();
        ApplicationService apps = new ApplicationService(new ApplicationDao());
        ResumeService resumes = new ResumeService(new ResumeDao());
        AiService ai = new AiService();
        RateLimiter limiter = new RateLimiter(5, 60_000);

        Router router = new Router();
        new StaticController().registerRoutes(router);
        new AuthController(auth, limiter).registerRoutes(router);
        new ApplicationController(apps).registerRoutes(router);
        new ResumeController(resumes).registerRoutes(router);
        new AiController(ai, apps, auth, limiter).registerRoutes(router);

        HttpServer server = HttpServer.create(new InetSocketAddress(Config.port()), 0);
        server.createContext("/", new Dispatcher(router, jwt));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Job Tracker chal raha hai: http://localhost:" + Config.port());
    }
}
