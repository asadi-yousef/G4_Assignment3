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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;


public class SimpleServer extends AbstractServer {
	// Thread-safe collection for subscribers
	private static final CopyOnWriteArrayList<SubscribedClient> subscribersList = new CopyOnWriteArrayList<>();

	private static volatile Catalog catalog = new Catalog(new ArrayList<>());
	private static final ReentrantReadWriteLock catalogLock = new ReentrantReadWriteLock();

	// Use ConcurrentHashMap for thread-safe cache
	private static final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();

	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("my-persistence-unit");

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
				else if(msgString.startsWith("add_to_cart")) {
					handleAddToCart(message, client);
				}
				else if(msgString.startsWith("request_cart")) {
					handleCartRequest(message, client);
				}
				// ADD THE NEW PROFILE HANDLERS HERE
				else if(msgString.equals("request_customer_data")) {
					handleCustomerDataRequest(message, client, session);
				}
				else if(msgString.equals("update_profile")) {
					handleProfileUpdate(message, client, session);
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

// ADD THESE TWO NEW METHODS TO YOUR CLASS:

	private void handleCustomerDataRequest(Message message, ConnectionToClient client, Session session) {
		try {
			// Check if the message has a payload - it should be the User object
			Object payload = message.getObject();
			User user = null;

			// Handle different payload types
			if (payload instanceof User) {
				user = (User) payload;
			} else if (payload instanceof java.util.List) {
				java.util.List<?> list = (java.util.List<?>) payload;
				if (!list.isEmpty() && list.get(0) instanceof User) {
					user = (User) list.get(0);
				}
			}

			if (user == null) {
				client.sendToClient(new Message("error", "Invalid user data", null));
				return;
			}

			// Since Customer extends User, we need to get the Customer by the same ID
			Customer customer = null;

			if (user instanceof Customer) {
				// User is already a Customer, just cast it
				customer = (Customer) user;
			} else {
				// User is not a Customer, try to find Customer with same ID
				customer = session.get(Customer.class, user.getId());
			}

			Message response = new Message("customer_data_response", customer, null);
			client.sendToClient(response);

		} catch (Exception e) {
			e.printStackTrace();
			try {
				client.sendToClient(new Message("error", "Failed to get customer data: " + e.getMessage(), null));
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}

	private void handleProfileUpdate(Message message, ConnectionToClient client, Session session) {
		try {
			Object payload = message.getObject();
			User user = null;
			Customer customer = null;

			// Handle different payload types
			if (payload instanceof User) {
				user = (User) payload;
				if (user instanceof Customer) {
					customer = (Customer) user;
				}
			} else if (payload instanceof java.util.List) {
				java.util.List<?> list = (java.util.List<?>) payload;
				if (!list.isEmpty()) {
					if (list.get(0) instanceof User) {
						user = (User) list.get(0);
					}
					if (list.size() > 1 && list.get(1) instanceof Customer) {
						customer = (Customer) list.get(1);
					}
				}
			}

			if (user == null) {
				client.sendToClient(new Message("profile_update_failed", "Invalid user data", null));
				return;
			}

			// Start transaction
			session.beginTransaction();

			// Update user
			session.merge(user);

			// Update customer if exists
			if (customer != null) {
				session.merge(customer);
			}

			// Commit transaction
			session.getTransaction().commit();

			Message response = new Message("profile_updated_success", null, null);
			client.sendToClient(response);

		} catch (Exception e) {
			// Rollback transaction on error
			if (session.getTransaction() != null && session.getTransaction().isActive()) {
				session.getTransaction().rollback();
			}
			e.printStackTrace();
			try {
				client.sendToClient(new Message("profile_update_failed", "Database update failed: " + e.getMessage(), null));
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

	private void handleAddToCart(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();

		try {
			em.getTransaction().begin();

			Long productId = (Long) message.getObject();
			User user = (User) message.getObjectList().get(0);

			Product product = em.find(Product.class, productId);

			Cart cart = em.createQuery(
							"SELECT c FROM Cart c WHERE c.user.id = :uid", Cart.class)
					.setParameter("uid", user.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);

			if (cart == null) {
				cart = new Cart(user);
				em.persist(cart);
			}

			CartItem item = cart.getItems().stream()
					.filter(ci -> ci.getProduct().getId().equals(productId))
					.findFirst()
					.orElse(null);

			if (item != null) {
				item.setQuantity(item.getQuantity() + 1);
			} else {
				item = new CartItem(product, cart, 1);
				em.persist(item);
				cart.getItems().add(item);
			}

			em.getTransaction().commit();

			client.sendToClient(new Message("cart_updated", null, null));

		} catch (Exception e) {
			e.printStackTrace();
			try {
				client.sendToClient(new Message("error", "Failed to add to cart", null));
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} finally {
			em.close();
		}
	}



	private void handleCartRequest(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();

		try {
			User user = (User) message.getObjectList().get(0);

			Cart cart = em.createQuery(
							"SELECT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.user.id = :uid",
							Cart.class)
					.setParameter("uid", user.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);

			if (cart == null) {
				cart = new Cart(user);
				em.getTransaction().begin();
				em.persist(cart);
				em.getTransaction().commit();
			}

			client.sendToClient(new Message("cart_data", cart, null));

		} catch (Exception e) {
			e.printStackTrace();
			try {
				client.sendToClient(new Message("error", "Failed to load cart", null));
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} finally {
			em.close();
		}
	}





	private Cart getOrCreateCartForUser(User user) {
		EntityManager em = emf.createEntityManager();
		Cart cart;

		try {
			em.getTransaction().begin();

			cart = em.createQuery(
							"SELECT c FROM Cart c WHERE c.user.id = :userId", Cart.class)
					.setParameter("userId", user.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);

			if (cart == null) {
				cart = new Cart(user);
				em.persist(cart);
			}

			em.getTransaction().commit();
		} finally {
			em.close();
		}
		return cart;
	}

	private void addProductToCart(Cart cart, Long productId) {
		EntityManager em = emf.createEntityManager();

		try {
			em.getTransaction().begin();

			Product product = em.find(Product.class, productId);
			Cart managedCart = em.find(Cart.class, cart.getId());

			CartItem item = managedCart.getItems().stream()
					.filter(ci -> ci.getProduct().getId().equals(productId))
					.findFirst()
					.orElse(null);

			if (item != null) {
				item.setQuantity(item.getQuantity() + 1);
			} else {
				item = new CartItem(product, managedCart, 1);
				em.persist(item);
				managedCart.getItems().add(item);
			}

			em.getTransaction().commit();
		} finally {
			em.close();
		}
	}

	private Cart getCartByUserId(Long userId) {
		EntityManager em = emf.createEntityManager();
		Cart cart;

		try {
			cart = em.createQuery(
							"SELECT c FROM Cart c WHERE c.user.id = :userId", Cart.class)
					.setParameter("userId", userId)
					.getSingleResult();
		} finally {
			em.close();
		}
		return cart;
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