package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.server.EmailService;

public final class Services {

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

    private Services() {}

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
