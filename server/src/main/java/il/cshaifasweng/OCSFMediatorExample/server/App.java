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
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");

        configuration.setProperty("hibernate.connection.password", password);
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");

        configuration.addAnnotatedClass(Flower.class);

        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();

        return configuration.buildSessionFactory(serviceRegistry);
    }

    public static void main(String[] args) throws IOException {
        // Prompt for password and build custom SessionFactory
        SessionFactory sessionFactory = getSessionFactory();

        // Set it into HibernateUtil
        HibernateUtil.setSessionFactory(sessionFactory);

        // Start server
        SimpleServer server = new SimpleServer(3000);
        server.listen();


        // Test DB logic
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            // Check if the Flower table is empty
            Long count = (Long) session.createQuery("select count(f.id) from Flower f").uniqueResult();

            if (count == 0) {
            Flower flower1 = new Flower("Roses", "Flower", 19.99,"/il/cshaifasweng/OCSFMediatorExample/client/images/roses.png");
            session.save(flower1);session.flush();
            Flower flower2 = new Flower("Tulips", "Flower", 14.50, "/il/cshaifasweng/OCSFMediatorExample/client/images/white tulip.png");
            session.save(flower2); session.flush();
            Flower flower3 = new Flower("Pretty in Pink Lilies", "Flower", 17.25,"/il/cshaifasweng/OCSFMediatorExample/client/images/lilies.png");
            session.save(flower3); session.flush();
            Flower flower4 = new Flower("Sunflower Bouquet", "Flower", 12.00,"/il/cshaifasweng/OCSFMediatorExample/client/images/sunflower.png");
            session.save(flower4); session.flush();
            Flower flower5=new Flower("Orange Carnations Bouquet","Flower",25.75,"/il/cshaifasweng/OCSFMediatorExample/client/images/carnations.png");
            session.save(flower5); session.flush();
            Flower flowerPot1 = new Flower("Purple Orchid Pot", "Brown", 9.99, "/il/cshaifasweng/OCSFMediatorExample/client/images/orchids.png");
            session.save(flowerPot1); session.flush();
            Flower flowerPot2 = new Flower("Ceramic Pot", "White", 14.49, "/il/cshaifasweng/OCSFMediatorExample/client/images/ceramic.png");
            session.save(flowerPot2); session.flush();
            Flower flowerPot3 = new Flower("Plastic Pot", "Green", 4.75, "/il/cshaifasweng/OCSFMediatorExample/client/images/plastic.png");
            session.save(flowerPot3); session.flush();
            } else {
                System.out.println("Flower table already contains data. Skipping insert.");
            }

            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            HibernateUtil.shutdown();
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        }));

    }
}