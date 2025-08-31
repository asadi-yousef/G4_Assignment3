package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.HibernateException;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class App {

    private static org.hibernate.SessionFactory buildSessionFactoryFromCfg() {
        org.hibernate.cfg.Configuration cfg = new org.hibernate.cfg.Configuration().configure(); // loads hibernate.cfg.xml

        // Optional: allow runtime password override
        System.out.print("Please enter your MySQL password: ");
        String pw = new java.util.Scanner(System.in).nextLine();
        if (pw != null && !pw.isEmpty()) {
            cfg.setProperty("hibernate.connection.password", pw);
        }

        // Register ALL annotated entities (belt & suspenders with XML <mapping/>)
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Product.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Customer.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.User.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Employee.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Cart.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.CartItem.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Branch.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.CreditCard.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Subscription.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Order.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.OrderItem.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.Report.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.OrdersReport.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.IncomeReport.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.ComplaintsReport.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.OrderRequest.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.CustomBouquet.class);
        cfg.addAnnotatedClass(il.cshaifasweng.OCSFMediatorExample.entities.CustomBouquetItem.class);

        org.hibernate.service.ServiceRegistry registry =
                new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
                        .applySettings(cfg.getProperties())
                        .build();

        return cfg.buildSessionFactory(registry);
    }


    public static void main(String[] args) throws IOException {
        // Build SessionFactory from hibernate.cfg.xml
        SessionFactory sessionFactory = buildSessionFactoryFromCfg();

        // Make it available to the rest of the app
        HibernateUtil.setSessionFactory(sessionFactory);

        // Start server on port 3000 (this is your app server port, not MySQL)
        SimpleServer server = new SimpleServer(3000);

        // ---- Seed DB if empty (single transaction, commit will flush) ----
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                Long productCount = session.createQuery(
                        "select count(p.id) from Product p", Long.class
                ).getSingleResult();

                if (productCount == null || productCount == 0L) {
                    Product product1 = new Product("Roses", "Flower", 19.99, "Red",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/roses.png");
                    Product product2 = new Product("Tulips", "Flower", 14.50, "Purple",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/white tulip.png");
                    Product product3 = new Product("Pretty in Pink Lilies", "Bouquet", 17.25, "Pink",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/lilies.png");
                    Product product4 = new Product("Sunflower Bouquet", "Bouquet", 12.00, "Yellow",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/sunflower.png");
                    Product product5 = new Product("Orange Carnations Bouquet", "Bouquet", 25.75, "Orange",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/carnations.png");
                    Product productPot1 = new Product("Purple Orchid Pot", "pot", 9.99, "Purple",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/orchids.png");
                    Product productPot2 = new Product("Ceramic Pot", "pot", 14.49, "Black",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/ceramic.png");
                    Product productPot3 = new Product("Plastic Pot", "pot", 4.75, "Brown",
                            "/il/cshaifasweng/OCSFMediatorExample/client/images/plastic.png");

                    session.save(product1);
                    session.save(product2);
                    session.save(product3);
                    session.save(product4);
                    session.save(product5);
                    session.save(productPot1);
                    session.save(productPot2);
                    session.save(productPot3);
                } else {
                    System.out.println("Product table already contains data. Skipping insert.");
                }

                Long userCount = session.createQuery(
                        "select count(u.id) from User u", Long.class
                ).getSingleResult();
                System.out.println("Users in DB: " + userCount);

                if (userCount == null || userCount == 0L) {
                    Employee employee = new Employee("yosef", "yosef", "yosef2005", "Manager", null, true);
                    session.save(employee);
                } else {
                    System.out.println("User table already contains data. Skipping insert.");
                }

                Long branchCount = session.createQuery(
                        "select count(b.id) from Branch b", Long.class
                ).getSingleResult();

                if (branchCount == null || branchCount == 0L) {
                    session.save(new Branch("Haifa"));
                    session.save(new Branch("Tel Aviv"));
                } else {
                    System.out.println("Branch table already contains data. Skipping insert.");
                }

                tx.commit(); // flush happens automatically on commit
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // ---- End seed ----

        // Warm caches and start listening
        server.initCaches();
        server.listen();

        // Clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            HibernateUtil.shutdown();
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        }));
    }
}
