package il.cshaifasweng.OCSFMediatorExample.server;

import org.hibernate.SessionFactory;


public final class HibernateUtil {
    private static volatile SessionFactory sessionFactory;

    private HibernateUtil() {}

    // Set once at startup from App.buildSessionFactoryFromCfg()
    public static synchronized void setSessionFactory(SessionFactory factory) {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
        sessionFactory = factory;
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            throw new IllegalStateException(
                    "SessionFactory not initialized. Call HibernateUtil.setSessionFactory(...) at startup."
            );
        }
        return sessionFactory;
    }

    public static synchronized void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
            sessionFactory = null;
        }
    }
}
