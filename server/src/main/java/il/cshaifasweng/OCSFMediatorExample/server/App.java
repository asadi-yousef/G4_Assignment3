package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.IOException;
import java.util.Scanner;

public class App {

    private static SessionFactory buildSessionFactoryFromCfg() {
        Configuration cfg = new Configuration().configure();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            try {
                // Ask for MySQL password
                System.out.print("Please enter your MySQL password: ");
                String pw = scanner.nextLine();
                if (pw != null && !pw.isEmpty()) {
                    cfg.setProperty("hibernate.connection.password", pw);
                }

        // Register ALL annotated entities explicitly
        cfg.addAnnotatedClass(Product.class);
        cfg.addAnnotatedClass(Customer.class);
        cfg.addAnnotatedClass(User.class);
        cfg.addAnnotatedClass(Employee.class);
        cfg.addAnnotatedClass(Cart.class);
        cfg.addAnnotatedClass(CartItem.class);
        cfg.addAnnotatedClass(Branch.class);
        cfg.addAnnotatedClass(CreditCard.class);
        cfg.addAnnotatedClass(Subscription.class);
        cfg.addAnnotatedClass(Order.class);
        cfg.addAnnotatedClass(OrderItem.class);
        cfg.addAnnotatedClass(Report.class);
        cfg.addAnnotatedClass(OrdersReport.class);
        cfg.addAnnotatedClass(IncomeReport.class);
        cfg.addAnnotatedClass(ComplaintsReport.class);
        cfg.addAnnotatedClass(OrderRequest.class);
        cfg.addAnnotatedClass(CustomBouquet.class);
        cfg.addAnnotatedClass(CustomBouquetItem.class);
        // 👇 IMPORTANT: Complaint must be mapped
        cfg.addAnnotatedClass(Complaint.class);
        cfg.addAnnotatedClass(Notification.class);
        cfg.addAnnotatedClass(Budget.class);

                ServiceRegistry registry = new StandardServiceRegistryBuilder()
                        .applySettings(cfg.getProperties())
                        .build();

                // Attempt to create SessionFactory (will fail if wrong password)
                SessionFactory sessionFactory = cfg.buildSessionFactory(registry);

                System.out.println("✅ Connected to database successfully!");
                return sessionFactory;

            } catch (Exception e) {
                System.err.println("❌ Failed to connect to database: " + e.getMessage());
                System.err.println("Please try again.");
                try {
                    Thread.sleep(1000); // Small delay before retry
                } catch (InterruptedException ignored) {}
            }
        }
    }


    public static void main(String[] args) throws IOException {
        // Build SessionFactory and make it available globally
        SessionFactory sessionFactory = buildSessionFactoryFromCfg();
        HibernateUtil.setSessionFactory(sessionFactory);

        // Start the server (port 3000 as in your code)
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
                    Employee employee = new Employee("214734501","yosef","asadi", "yosef", "yosef2005", "netmanager", null, true);
                    session.save(employee);
                    session.flush();
                    Employee employee1 = new Employee("214732341","naaman","kopty", "_Naaman", "naaman1702", "systemadmin", null, true);
                    session.save(employee1);
                    session.flush();
                    Employee employee2 = new Employee("214396675","silin","michael", "silin", "silin", "customerservice", null, true);
                    session.save(employee2);
                    session.flush();
                    Employee e = new Employee("212735468","renata","haiek","renata","123","driver",null,true);
                    session.save(e);
                    session.flush();
                    Session session1 = sessionFactory.openSession();
                    Transaction tx1 = session1.beginTransaction();
                    Branch haifaBranch = session1.get(Branch.class, 1L);
                    Employee employee3 = new Employee("212744916", "Renata_", "Haiek", "Renata_", "renata2002", "branchmanager", haifaBranch, true);
                    employee3.setBranch(haifaBranch);
                    session1.save(employee3);
                    tx1.commit();
                } else {
                    System.out.println("User table already contains data. Skipping insert.");
                }

                Long branchCount = session.createQuery(
                        "select count(b.id) from Branch b", Long.class
                ).getSingleResult();

                if (branchCount == null || branchCount == 0L) {
                    Branch branch1 = new Branch("Haifa");
                    session.save(branch1);
                    Branch branch2 = new Branch("Tel-Aviv");
                    session.flush();
                    session.save(branch2);
                    session.flush();
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


        try {
            server.listen();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🔒 Logging out all users...");

            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = session.beginTransaction();
                try {
                    int updated = session.createQuery(
                            "update User u set u.isLoggedIn = false"
                    ).executeUpdate();
                    tx.commit();
                    System.out.println("✅ Logged out " + updated + " users.");
                } catch (Exception e) {
                    if (tx != null) tx.rollback();
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            HibernateUtil.shutdown();
            try {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
            } catch (Throwable ignored) {}
        }));

    }
}
