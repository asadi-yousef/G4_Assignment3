package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;

import jakarta.persistence.criteria.Order;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;


public class SimpleServer extends AbstractServer {
	private static final Object subscribersLock = new Object();
	private static ArrayList<SubscribedClient> SubscribersList = new ArrayList<>();
	private static volatile Catalog catalog = new Catalog(new ArrayList<>());
	private static final ReentrantReadWriteLock catalogLock = new ReentrantReadWriteLock();

	// Use ConcurrentHashMap for thread-safe cache
	private static ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
	private static SessionFactory sessionFactory;

	public SimpleServer(int port) {
		super(port);
		catalog.setFlowers(getListFromDB(Product.class));
	}

	public <T> List<T> getListFromDB(Class<T> entityClass) {
		List<T> resultList;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			resultList = session.createQuery("from " + entityClass.getSimpleName(), entityClass).list();
		} catch (Exception e) {
			e.printStackTrace();
			resultList = new ArrayList<>();
		}
		return resultList;
	}

	public <T, K> void loadToCache(Class<T> entityClass, ConcurrentHashMap<K,T> cache, Function<T, K> keyExtractor) {
		List<T> entities = getListFromDB(entityClass);
		for (T entity : entities) {
			cache.put(keyExtractor.apply(entity), entity);
		}
	}

	public void initCaches(){
		userCache = new ConcurrentHashMap<>();
		loadToCache(User.class, userCache, User::getUsername);
	}

	@Override
	protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
		String msgString = msg.toString();

		// Create a new session for each request to avoid sharing sessions
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			if(msg.getClass() == Message.class) {
				if(Objects.equals(((Message) msg).getMessage(), "register")) {
					handleUserRegistration((Message) msg, client, session);
				}
			}

			if (msgString.equals("request_catalog")) {
				handleCatalogRequest(client);
			}
			else if (msgString.startsWith("update_price")) {
				handlePriceUpdate(msgString);
			}
			else if (msgString.startsWith("remove client")) {
				handleClientRemoval(client);
			}
			else if (msgString.contains("check existence")) {
				handleUserAuthentication(msgString, client, session);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleUserRegistration(Message msg, ConnectionToClient client, Session session) {
		Transaction tx = session.beginTransaction();
		try {
			User user = (User) msg.getObject();
			userCache.put(user.getUsername(), user);
			session.save(user);
			session.flush();
			tx.commit();

			Message message = new Message("registered", null, null);
			client.sendToClient(message);
		} catch (Exception e) {
			tx.rollback();
			e.printStackTrace();
		}
	}

	private void handleCatalogRequest(ConnectionToClient client) {
		catalogLock.readLock().lock();
		try {
			List<Product> productList = getListFromDB(Product.class);
			Catalog currentCatalog = new Catalog(productList);
			client.sendToClient(currentCatalog);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			catalogLock.readLock().unlock();
		}
	}

	private void handlePriceUpdate(String msgString) {
		String[] parts = msgString.split(":");
		if (parts.length == 3) {
			try {
				int flowerId = Integer.parseInt(parts[1]);
				double newPrice = Double.parseDouble(parts[2]);
				updateFlowerPrice(flowerId, newPrice);

				// Update catalog with write lock
				catalogLock.writeLock().lock();
				try {
					this.catalog.setFlowers(getListFromDB(Product.class));
					sendToAllClients(catalog);
				} finally {
					catalogLock.writeLock().unlock();
				}
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
	}

	private void handleClientRemoval(ConnectionToClient client) {
		synchronized (subscribersLock) {
			SubscribersList.removeIf(subscribedClient -> subscribedClient.getClient().equals(client));
		}
	}

	private void handleUserAuthentication(String msgString, ConnectionToClient client, Session session) {
		Transaction tx = null;
		try {
			tx = session.beginTransaction();

			String tmp = msgString.substring("check existence: ".length());
			String username = tmp.split(" ")[0];
			String password = tmp.split(" ")[1];

			// Use thread-safe cache lookup
			User user = userCache.get(username);

			if(user == null) {
				Message message = new Message("incorrect", null, null);
				client.sendToClient(message);
			} else {
				if(user.getPassword().equals(password)) {
					Message message = new Message("correct", user, null);
					client.sendToClient(message);
				} else {
					System.out.println("Incorrect password");
					client.sendToClient("incorrect");
				}
			}
			tx.commit();
		} catch (Exception e) {
			if (tx != null) tx.rollback();
			e.printStackTrace();
		}
	}

	public void updateFlowerPrice(int flowerId, double newPrice) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			Product product = session.get(Product.class, flowerId);
			if (product != null) {
				product.setPrice(newPrice);
				session.update(product);
			} else {
				System.out.println("Flower not found with ID: " + flowerId);
			}

			tx.commit();
		} catch (Exception e) {
			if (tx != null) tx.rollback();
			e.printStackTrace();
		}
	}

	public void sendToAllClients(Object message) {
		synchronized (subscribersLock) {
			try {
				for (SubscribedClient subscribedClient : SubscribersList) {
					subscribedClient.getClient().sendToClient(message);
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
}