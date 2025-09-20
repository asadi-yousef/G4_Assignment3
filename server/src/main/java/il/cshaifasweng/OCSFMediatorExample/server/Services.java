package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.server.EmailService;

public final class Services {

    private static final java.util.Properties CFG = loadProps();

    private Services() {}
    // E-mail service singleton
    public static final EmailService EMAIL = new EmailService(
            env("MAIL_HOST", "smtp.gmail.com"),
            parseInt(env("MAIL_PORT", "587"), 587),
            env("MAIL_USERNAME", ""),                     // user
            env("MAIL_PASSWORD", ""),                     // pass
            Boolean.parseBoolean(env("MAIL_TLS", "true")),
            env("MAIL_FROM", env("MAIL_USERNAME", "no-reply@example.com"))
    );

    // Optional helper for conditional sending
    public static boolean emailConfigured() {
        return !env("MAIL_USERNAME","").isBlank() && !env("MAIL_PASSWORD","").isBlank();
    }

    private static java.util.Properties loadProps() {
        var p = new java.util.Properties();

        // 1) Try classpath: src/main/resources/mail.properties
        try (var in = Services.class.getClassLoader().getResourceAsStream("mail.properties")) {
            if (in != null) {
                p.load(in);
                System.out.println("[Mail] loaded mail.properties from classpath");
                return p;
            }
        } catch (Exception e) {
            System.err.println("[Mail] failed to load from classpath: " + e);
        }

        // 2) Fallback: external file next to the JAR (optional)
        try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of("mail.properties"))) {
            p.load(in);
            System.out.println("[Mail] loaded mail.properties from working directory");
        } catch (Exception e) {
            System.err.println("[Mail] no mail.properties found on classpath or disk");
        }
        return p;
    }


    private static String env(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) v = System.getProperty(key); // optional: supports -D
        if (v == null || v.isBlank()) v = CFG.getProperty(key);    // <-- read from file
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}