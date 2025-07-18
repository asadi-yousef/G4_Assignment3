package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Employee;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;


public class App {

    private static SessionFactory getSessionFactory() throws HibernateException {
        Configuration configuration = new Configuration();
        String password = "1@2@3@4_5Tuf";
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");

        configuration.setProperty("hibernate.connection.password", password);
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");

        configuration.addAnnotatedClass(Product.class);
        configuration.addAnnotatedClass(Customer.class);
        configuration.addAnnotatedClass(User.class);
        configuration.addAnnotatedClass(Employee.class);

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
            Long count = (Long) session.createQuery("select count(f.id) from Product f").uniqueResult();

            if (count == 0) {
            Product product1 = new Product("Roses", "Flower", 19.99,"/il/cshaifasweng/OCSFMediatorExample/client/images/roses.png");
            session.save(product1);session.flush();
            Product product2 = new Product("Tulips", "Flower", 14.50, "/il/cshaifasweng/OCSFMediatorExample/client/images/white tulip.png");
            session.save(product2); session.flush();
            Product product3 = new Product("Pretty in Pink Lilies", "Flower", 17.25,"/il/cshaifasweng/OCSFMediatorExample/client/images/lilies.png");
            session.save(product3); session.flush();
            Product product4 = new Product("Sunflower Bouquet", "Flower", 12.00,"/il/cshaifasweng/OCSFMediatorExample/client/images/sunflower.png");
            session.save(product4); session.flush();
            Product product5 =new Product("Orange Carnations Bouquet","Flower",25.75,"/il/cshaifasweng/OCSFMediatorExample/client/images/carnations.png");
            session.save(product5); session.flush();
            Product productPot1 = new Product("Purple Orchid Pot", "Brown", 9.99, "/il/cshaifasweng/OCSFMediatorExample/client/images/orchids.png");
            session.save(productPot1); session.flush();
            Product productPot2 = new Product("Ceramic Pot", "White", 14.49, "/il/cshaifasweng/OCSFMediatorExample/client/images/ceramic.png");
            session.save(productPot2); session.flush();
            Product productPot3 = new Product("Plastic Pot", "Green", 4.75, "/il/cshaifasweng/OCSFMediatorExample/client/images/plastic.png");
            session.save(productPot3); session.flush();
            } else {
                System.out.println("Flower table already contains data. Skipping insert.");
            }
            count = (Long) session.createQuery("select count(f.id) from User f").uniqueResult();
            System.out.println(count);
            if (count == 0) {
                Customer customer = new Customer("Yosef","yosef","yosef2005",true,
                        false,"111111111","assdiyousef@gmail.com",
                        "0549946411","bb","aaaaa","aaa");
                session.save(customer); session.flush();
            }else {
                System.out.println("Customer table already contains data. Skipping insert.");
            }
            tx.commit();
            List<User> users = server.getListFromDB(User.class);
            for (User user : users) {
                if (user instanceof Employee) {
                    System.out.println("Employee: " + ((Employee) user).getRole());
                } else if (user instanceof Customer) {
                    System.out.println("Customer: " + user.getUsername());
                } else {
                    System.out.println("Base User: " + user.getUsername());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        server.initCaches();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            HibernateUtil.shutdown();
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        }));

    }
}