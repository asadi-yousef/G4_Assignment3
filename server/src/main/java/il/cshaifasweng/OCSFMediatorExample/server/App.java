package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.io.IOException;

public class App {
    private static SimpleServer server;

    public static void main(String[] args) throws IOException {
        server = new SimpleServer(3000);
        server.listen();

        // Test DB logic
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            Flower flower = new Flower("Rose", "Red",6, 19.99, null);
            session.save(flower);

            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(HibernateUtil::shutdown));
    }
}