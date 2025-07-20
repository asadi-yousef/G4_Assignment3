package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;


public class SimpleServer extends AbstractServer {
	private static final Object subscribersLock = new Object();
	private static ArrayList<SubscribedClient> SubscribersList = new ArrayList<>();
	private static Catalog catalog = new Catalog(new ArrayList<>());
	private static final ReentrantReadWriteLock catalogLock = new ReentrantReadWriteLock();

	// Use ConcurrentHashMap for thread-safe cache
	private static ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
	private static SessionFactory sessionFactory;

	public SimpleServer(int port) {
		super(port);
		catalog.setFlowers(getListFromDB(Product.class));
	}

	@Override
	protected void clientConnected(ConnectionToClient client) {
		super.clientConnected(client);
		synchronized (subscribersLock) {
			SubscribersList.add(new SubscribedClient(client));
		}
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
			else if (msgString.startsWith("editProduct")) {
				handleProductEdit((Message) msg);
			}
			else if (msgString.startsWith("remove client")) {
				handleClientRemoval(client);
				System.out.println("removed subscribed client");
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

			// First save to database
			session.save(user);
			session.flush();
			tx.commit();

			// Only update cache after successful database save
			userCache.put(user.getUsername(), user);

			Message message = new Message("registered", null, null);
			client.sendToClient(message);
		} catch (Exception e) {
			tx.rollback();
			// Don't update cache if database save failed
			try {
				Message errorMessage = new Message("registration_failed", null, null);
				client.sendToClient(errorMessage);
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
			e.printStackTrace();
		}
	}

	private void handleCatalogRequest(ConnectionToClient client) {
		catalogLock.readLock().lock();
		try {
			Message message = new Message("catalog", catalog, null);
			client.sendToClient(message);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			catalogLock.readLock().unlock();
		}
	}

	private void handleProductEdit(Message msg) {
		String msgString = msg.getMessage();
		String[] parts = msgString.split(":");
		if (parts.length == 3) {
			try {
				Product product = (Product)(msg.getObject());
				int productId = Integer.parseInt(parts[2]);
				double newPrice = product.getPrice();
				String newProductName = product.getName();
				String newType = product.getType();
				String newImagePath = product.getImagePath();

				// Update database first
				boolean updateSuccess = updateProduct(productId,newProductName,newPrice,newType,newImagePath);

				if (updateSuccess) {
					// Update catalog with write lock only if database update succeeded
					catalogLock.writeLock().lock();
					try {
						this.catalog.setFlowers(getListFromDB(Product.class));
						Message message = new Message(msgString, null, null);
						sendToAllClients(message);
					} finally {
						catalogLock.writeLock().unlock();
					}
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
		try {
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
					// Fixed: Use consistent Message object instead of raw string
					Message message = new Message("incorrect", null, null);
					client.sendToClient(message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			try {
				Message errorMessage = new Message("authentication_error", null, null);
				client.sendToClient(errorMessage);
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}

	public boolean updateProduct(int productId,String newProductName, double newPrice,String newType, String newImagePath) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			Product product = session.get(Product.class, productId);
			if (product != null) {
				product.setPrice(newPrice);
				product.setName(newProductName);
				product.setType(newType);
				product.setImagePath(newImagePath);
				session.update(product);
				tx.commit();
				return true; // Return success status
			} else {
				System.out.println("Flower not found with ID: " + productId);
				tx.rollback();
				return false;
			}
		} catch (Exception e) {
			if (tx != null) tx.rollback();
			e.printStackTrace();
			return false;
		}
	}

	public void sendToAllClients(Message message) {
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