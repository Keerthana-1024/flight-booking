package com.flights;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlightsApplication {

    public static void main(String[] args) {
        // Load .env file for local development (Render uses actual env vars)
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();
            dotenv.entries().forEach(e -> {
                if (System.getenv(e.getKey()) == null && System.getProperty(e.getKey()) == null) {
                    System.setProperty(e.getKey(), e.getValue());
                }
            });
        } catch (Exception ignored) {
            System.out.println("No .env file found, using system environment variables.");
        }

        SpringApplication.run(FlightsApplication.class, args);
    }
}
