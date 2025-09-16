package il.cshaifasweng.OCSFMediatorExample.server;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmailService {
    private final Session session;
    private final String from;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public EmailService(String host, int port, String username, String password, boolean tls, String from) {
        this.from = from;

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(tls));
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        // ensures single-arg setSubject/setText use UTF-8
        props.put("mail.mime.charset", StandardCharsets.UTF_8.name());

        this.session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    public void sendTextAsync(String to, String subject, String body) {
        executor.submit(() -> {
            try {
                jakarta.mail.Message msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(from));
                msg.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(to));
                msg.setSubject(subject);   // 1-arg form (UTF-8 via props)
                msg.setText(body);         // 1-arg form (UTF-8 via props)
                Transport.send(msg);
            } catch (Exception e) {
                System.err.println("[EmailService] Send failed: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
