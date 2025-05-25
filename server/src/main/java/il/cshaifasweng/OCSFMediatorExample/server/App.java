package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import il.cshaifasweng.OCSFMediatorExample.server.SimpleServer;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import java.io.IOException;
import java.util.Scanner;

public class App {

    private static SessionFactory getSessionFactory() throws HibernateException {
        Configuration configuration = new Configuration();
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your MySQL password : ");
        String password = scanner.nextLine();
        configuration.setProperty("hibernate.connection.password", password);

        configuration.addAnnotatedClass(Flower.class);

        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();

        return configuration.buildSessionFactory(serviceRegistry);
    }

    public static void main(String[] args) throws IOException {
        SimpleServer server = new SimpleServer(3000);
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