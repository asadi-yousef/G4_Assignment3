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

            Flower flower1 = new Flower("Rose", "Red", 19.99,null);
            session.save(flower1);session.flush();
            Flower flower2 = new Flower("Tulip", "Yellow", 14.50,null);
            session.save(flower2); session.flush();
            Flower flower3 = new Flower("Lily", "White", 17.25,null);
            session.save(flower3); session.flush();
            Flower flower4 = new Flower("Sunflower", "Golden", 12.00,null);
            session.save(flower4); session.flush();
            Flower flower5=new Flower("Orchid","Purple",25.75,null);
            session.save(flower5); session.flush();
            Flower flowerPot1 = new Flower("Terracotta Pot", "Brown", 9.99, null);
            session.save(flowerPot1); session.flush();
            Flower flowerPot2 = new Flower("Ceramic Pot", "White", 14.49, null);
            session.save(flowerPot2); session.flush();
            Flower flowerPot3 = new Flower("Plastic Pot", "Green", 4.75, null);
            session.save(flowerPot3); session.flush();

            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(HibernateUtil::shutdown));
    }
}