package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;


public class SimpleServer extends AbstractServer {
	// Thread-safe collection for subscribers
	private static final CopyOnWriteArrayList<SubscribedClient> subscribersList = new CopyOnWriteArrayList<>();

	private static volatile Catalog catalog = new Catalog(new ArrayList<>());
	private static final ReentrantReadWriteLock catalogLock = new ReentrantReadWriteLock();

	// Use ConcurrentHashMap for thread-safe cache
	private static final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();

	public SimpleServer(int port) {
		super(port);
		initCaches(); // Initialize caches before loading catalog

		catalogLock.writeLock().lock();
		try {
			catalog.setFlowers(getListFromDB(Product.class));
			System.out.println("number of products in catalog: " + catalog.getFlowers().size());
		} finally {
			catalogLock.writeLock().unlock();
		}
	}

	@Override
	protected void clientConnected(ConnectionToClient client) {
		super.clientConnected(client);
		// CopyOnWriteArrayList is thread-safe, no synchronization needed
		subscribersList.add(new SubscribedClient(client));
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
		cache.clear(); // Clear existing entries
		for (T entity : entities) {
			cache.put(keyExtractor.apply(entity), entity);
		}
	}

	public void initCaches(){
		loadToCache(User.class, userCache, User::getUsername);
	}

	@Override
	protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
		if (msg == null) {
			return; // Guard against null messages
		}

		String msgString = msg.toString();

		// Create a new session for each request to avoid sharing sessions
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			if(msg instanceof Message) {
				Message message = (Message) msg;
				if(Objects.equals(message.getMessage(), "register")) {
					handleUserRegistration(message, client, session);
				}
				else if(msgString.startsWith("add_product")) {
					handleAddProduct(message, client, session);
				}
				else if (msgString.startsWith("editProduct")) {
					handleProductEdit(message);
				}
				else if(msgString.startsWith("delete_product")) {
					handleDeleteProduct(message, client, session);
				}
			}

			if (msgString.equals("request_catalog")) {
				handleCatalogRequest(client);
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
			// Send error response to client
			try {
				Message errorMsg = new Message("server_error", null, null);
				client.sendToClient(errorMsg);
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}

	private void handleDeleteProduct(Message msg, ConnectionToClient client, Session session) {
		Transaction tx = null;
		try{
			tx = session.beginTransaction();
			Long id = (Long) msg.getObject();
			Product product = session.get(Product.class, id);

			if (product != null) {
				session.delete(product);
				session.flush();
				tx.commit();

				// Update catalog with write lock
				catalogLock.writeLock().lock();
				try {
					catalog.getFlowers().remove(catalog.getProductById(id));
				} finally {
					catalogLock.writeLock().unlock();
				}

				sendToAllClients(msg);
			} else {
				tx.rollback();
				Message errorMsg = new Message("product_not_found", null, null);
				client.sendToClient(errorMsg);
			}
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
			}
			e.printStackTrace();
			try {
				Message errorMsg = new Message("delete_failed", null, null);
				client.sendToClient(errorMsg);
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}

	private void handleAddProduct(Message msg, ConnectionToClient client, Session session) {
		Transaction tx = null;
		try {
			tx = session.beginTransaction();
			Product product = (Product) msg.getObject();
			session.save(product);
			session.flush();
			tx.commit();

			Message message = new Message("add_product", product, null);

			// Update catalog with write lock
			catalogLock.writeLock().lock();
			try {
				catalog.getFlowers().add(product);
			} finally {
				catalogLock.writeLock().unlock();
			}

			sendToAllClients(message);
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
			}
			e.printStackTrace();
			try {
				Message errorMsg = new Message("add_failed", null, null);
				client.sendToClient(errorMsg);
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}
	private boolean checkExistence(String username) {
		if(userCache.containsKey(username)) {
			return true;
		}
		return false;
	}
	private void handleUserRegistration(Message msg, ConnectionToClient client, Session session) throws IOException {
		String username = ((User)(msg.getObject())).getUsername();
		if(checkExistence(username)) {
			Message errorMsg = new Message("user already exists", msg.getObject(), null);
			client.sendToClient(errorMsg);
		}
		else {
			Transaction tx = null;
			try {
				tx = session.beginTransaction();
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
				if (tx != null) {
					tx.rollback();
				}
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
	}

	private void handleCatalogRequest(ConnectionToClient client) {
		catalogLock.readLock().lock();
		try {
			// Create a defensive copy to avoid sharing mutable state
			Catalog catalogCopy = new Catalog(new ArrayList<>(catalog.getFlowers()));
			Message message = new Message("catalog", catalogCopy, null);
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
				boolean updateSuccess = updateProduct(productId, newProductName, newPrice, newType, newImagePath);

				if (updateSuccess) {
					// Update catalog with write lock only if database update succeeded
					catalogLock.writeLock().lock();
					try {
						catalog.setFlowers(getListFromDB(Product.class));
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
		// CopyOnWriteArrayList provides thread-safe removal
		subscribersList.removeIf(subscribedClient -> subscribedClient.getClient().equals(client));
	}

	private void handleUserAuthentication(String msgString, ConnectionToClient client, Session session) {
		try {
			String tmp = msgString.substring("check existence: ".length());
			String[] parts = tmp.split(" ");
			if (parts.length < 2) {
				Message errorMessage = new Message("invalid_request", null, null);
				client.sendToClient(errorMessage);
				return;
			}

			String username = parts[0];
			String password = parts[1];

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

	public boolean updateProduct(int productId, String newProductName, double newPrice, String newType, String newImagePath) {
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
				System.out.println("Product not found with ID: " + productId);
				if (tx != null) {
					tx.rollback();
				}
				return false;
			}
		} catch (Exception e) {
			if (tx != null) {
				tx.rollback();
			}
			e.printStackTrace();
			return false;
		}
	}

	public void sendToAllClients(Message message) {
		// CopyOnWriteArrayList provides thread-safe iteration
		// No additional synchronization needed
		try {
			for (SubscribedClient subscribedClient : subscribersList) {
				try {
					subscribedClient.getClient().sendToClient(message);
				} catch (IOException e) {
					// Remove client if send fails (client disconnected)
					subscribersList.remove(subscribedClient);
					System.out.println("Removed disconnected client");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}