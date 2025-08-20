package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import java.time.LocalDateTime;


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
				else if(msgString.startsWith("remove_cart_item")) {
					handleRemoveFromCart(message, client);
				}
				else if(msgString.startsWith("request_cart")) {
					handleCartRequest(message, client);
				}
				else if(msgString.startsWith("request_orders")) {
					handleOrdersRequest(message,client);
				}
				else if(msgString.equals("request_customer_data")) {
					handleCustomerDataRequest(message, client, session);
				}
				else if(msgString.equals("update_profile")) {
					handleProfileUpdate(message, client, session);
				}
				else if(msgString.startsWith("place_order")){
					handlePlaceOrder(message, client);
				}
				else if(msgString.startsWith("update_cart_item_quantity"))
				{
					handleUpdateCartItemQuantity(message, client);
				}
				else if (msgString.startsWith("request_branches")) {
					System.out.println("request_Branches");
					handleBranchesRequest(message, client);
				}
				else if (msgString.equals("check existence")) {
					handleUserAuthentication(message, client, session);
				}

			}

			if (msgString.equals("request_catalog")) {
				handleCatalogRequest(client);
			}
			else if (msgString.startsWith("remove client")) {
				handleClientRemoval(client);
				System.out.println("removed subscribed client");
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

	private void handleBranchesRequest(Message msg, ConnectionToClient client) throws IOException {
		List<Branch> branches = getListFromDB(Branch.class);
		for (Branch branch : branches) {
			System.out.println("Branch: " + branch.getName());
		}
		client.sendToClient(new Message("Branches",branches,null));
	}
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

	private void handleUserAuthentication(Message msg, ConnectionToClient client, Session session) throws Exception {
		try {
			List<String> info = (List<String>) msg.getObject();
			String username = info.get(0);
			String password = info.get(1);

			// Use thread-safe cache lookup
			User user = userCache.get(username);

			if(user == null) {
				System.out.println("user not found");
				Message message = new Message("incorrect", null, null);
				client.sendToClient(message);
			} else {
				if(user.getPassword().equals(password)) {
					System.out.println(user.getUsername());
					System.out.println(user.getPassword());
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
				System.out.println(e.getMessage());
				Message errorMessage = new Message("authentication_error", null, null);
				client.sendToClient(errorMessage);
			} catch (IOException ioException) {
				System.out.println(ioException.getMessage());
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
			Long productId = (Long) message.getObject();
			User user = (User) message.getObjectList().get(0);

			if (!(user instanceof Customer)) {
				client.sendToClient(new Message("error", "User is not a customer", null));
				return;
			}

			em.getTransaction().begin();

			Customer customer = em.find(Customer.class, ((Customer) user).getId());
			Product product = em.find(Product.class, productId);

			if (customer == null || product == null) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Customer or product not found", null));
				return;
			}

			Cart cart = customer.getCart();
			if (cart == null) {
				cart = new Cart(customer);
				em.persist(cart);
				customer.setCart(cart);
			}

			CartItem existingItem = cart.getItems().stream()
					.filter(ci -> ci.getProduct().getId().equals(productId))
					.findFirst()
					.orElse(null);

			if (existingItem != null) {
				existingItem.setQuantity(existingItem.getQuantity() + 1);
			} else {
				CartItem newItem = new CartItem(product, cart, 1);
				cart.getItems().add(newItem);
				em.persist(newItem);
			}

			em.merge(cart);
			em.getTransaction().commit();

			// Re-fetch cart fully loaded
			em.getTransaction().begin();
			Cart refreshedCart = em.createQuery(
							"SELECT DISTINCT c FROM Cart c " +
									"LEFT JOIN FETCH c.items i " +
									"LEFT JOIN FETCH i.product " +
									"WHERE c.id = :cid", Cart.class)
					.setParameter("cid", cart.getId())
					.getSingleResult();
			em.getTransaction().commit();

			client.sendToClient(new Message("cart_data", refreshedCart, null));

		} catch (Exception e) {
			e.printStackTrace();
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			try {
				client.sendToClient(new Message("error", "Failed to add to cart", null));
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} finally {
			em.close();
		}
	}

	private void handleRemoveFromCart(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();

		try {
			Customer customer = (Customer) message.getObjectList().get(0);
			CartItem itemToRemove = (CartItem) message.getObjectList().get(1);

			em.getTransaction().begin();

			Customer managedCustomer = em.find(Customer.class, customer.getId());
			if (managedCustomer == null) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Customer not found", null));
				return;
			}

			Cart cart = managedCustomer.getCart();
			if (cart == null) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Cart not found", null));
				return;
			}

			CartItem managedItem = em.find(CartItem.class, itemToRemove.getId());
			if (managedItem == null || !cart.getItems().contains(managedItem)) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Item not found in cart", null));
				return;
			}

			// Reduce quantity instead of removing immediately
			if (managedItem.getQuantity() > 1) {
				managedItem.setQuantity(managedItem.getQuantity() - 1);
				em.merge(managedItem);
			} else {
				cart.getItems().remove(managedItem);
				em.remove(managedItem);
			}

			em.merge(cart);
			em.getTransaction().commit();

			// Re-fetch updated cart
			em.getTransaction().begin();
			Cart refreshedCart = em.createQuery(
							"SELECT DISTINCT c FROM Cart c " +
									"LEFT JOIN FETCH c.items i " +
									"LEFT JOIN FETCH i.product " +
									"WHERE c.id = :cid", Cart.class)
					.setParameter("cid", cart.getId())
					.getSingleResult();
			em.getTransaction().commit();

			client.sendToClient(new Message("cart_data", refreshedCart, null));

		} catch (Exception e) {
			e.printStackTrace();
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			try {
				client.sendToClient(new Message("error", "Failed to remove item from cart", null));
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} finally {
			em.close();
		}
	}

	private void handleUpdateCartItemQuantity(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();
		try {
			Customer customer = (Customer) message.getObjectList().get(0);
			CartItem itemToUpdate = (CartItem) message.getObjectList().get(1);
			int newQuantity = (Integer) message.getObjectList().get(2);

			em.getTransaction().begin();

			Customer managedCustomer = em.find(Customer.class, customer.getId());
			if (managedCustomer == null) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Customer not found", null));
				return;
			}

			Cart cart = managedCustomer.getCart();
			if (cart == null) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Cart not found", null));
				return;
			}

			CartItem managedItem = em.find(CartItem.class, itemToUpdate.getId());
			if (managedItem == null || !cart.getItems().contains(managedItem)) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Item not found in cart", null));
				return;
			}

			if (newQuantity <= 0) {
				cart.getItems().remove(managedItem);
				em.remove(managedItem);
			} else {
				managedItem.setQuantity(newQuantity);
				em.merge(managedItem);
			}

			em.merge(cart);
			em.getTransaction().commit();

			// Refresh cart
			em.getTransaction().begin();
			Cart refreshedCart = em.createQuery(
							"SELECT DISTINCT c FROM Cart c " +
									"LEFT JOIN FETCH c.items i " +
									"LEFT JOIN FETCH i.product " +
									"WHERE c.id = :cid", Cart.class)
					.setParameter("cid", cart.getId())
					.getSingleResult();
			em.getTransaction().commit();

			client.sendToClient(new Message("cart_data", refreshedCart, null));

		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
		} finally {
			em.close();
		}
	}


	private void handleCartRequest(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();

		try {
			User user = (User) message.getObjectList().get(0);

			if(!(user instanceof Customer)) {
				client.sendToClient(new Message("error", "User is not a customer", null));
				return;
			}

			Customer customer = em.find(Customer.class, ((Customer) user).getId());

			Cart cart = em.createQuery("SELECT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.customer.id = :uid", Cart.class)
					.setParameter("uid", customer.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);

			if (cart == null) {
				cart = new Cart(customer);
				em.getTransaction().begin();
				em.persist(cart);
				em.getTransaction().commit();
			}
///
			client.sendToClient(new Message("cart_data", cart, null));
			if(cart != null) {
				System.out.println("Cart found with id: " + cart.getId());
				System.out.println("Items count: " + cart.getItems().size());
				for(CartItem ci : cart.getItems()) {
					System.out.println("Item product id: " + ci.getProduct().getId() + ", quantity: " + ci.getQuantity());
				}
			} else {
				System.out.println("No cart found for user id " + user.getId());
			}
///

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

	private void handleOrdersRequest(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();
		try {
			User user = (User) message.getObjectList().get(0);
			if (!(user instanceof Customer)) {
				client.sendToClient(new Message("error", "User is not a customer", null));
				return;
			}
			Customer customer = em.find(Customer.class, user.getId());

			List<Order> orders = em.createQuery("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :cid", Order.class)
					.setParameter("cid", customer.getId())
					.getResultList();

			client.sendToClient(new Message("orders_data", orders, null));
		} catch (Exception e) {
			e.printStackTrace();
			try {
				client.sendToClient(new Message("error", "Failed to load orders", null));
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} finally {
			em.close();
		}
	}


	private void handlePlaceOrder(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();

		try {
			Order clientOrder = (Order) message.getObject();

			if (clientOrder == null || clientOrder.getCustomer() == null) {
				client.sendToClient(new Message("order_error", "Missing order or customer data", null));
				return;
			}

			Long customerId = clientOrder.getCustomer().getId();

			em.getTransaction().begin();

			Customer managedCustomer = em.find(Customer.class, customerId);
			if (managedCustomer == null) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("order_error", "Customer not found", null));
				return;
			}

			Cart cart = em.createQuery(
							"SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.customer.id = :cid",
							Cart.class)
					.setParameter("cid", managedCustomer.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);
///
			System.out.println("DEBUG(handlePlaceOrder): fetched cart id=" + (cart != null ? cart.getId() : "null"));
			if (cart != null) {
				System.out.println("DEBUG(handlePlaceOrder): cart items = " + cart.getItems().size());
				for (CartItem ci : cart.getItems()) {
					System.out.println("DEBUG(handlePlaceOrder): CartItem pid=" + ci.getProduct().getId() + " qty=" + ci.getQuantity());
				}
			}
///

			if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("order_error", "Cart is empty", null));
				return;
			}

			// Create new managed Order and copy data
			Order order = new Order();
			order.setCustomer(managedCustomer);
			order.setStoreLocation(clientOrder.getStoreLocation());
			order.setDelivery(clientOrder.getDelivery());
			order.setOrderDate(LocalDateTime.now());
			order.setDeliveryDateTime(clientOrder.getDeliveryDateTime());
			order.setRecipientPhone(clientOrder.getRecipientPhone());
			order.setDeliveryAddress(clientOrder.getDeliveryAddress());
			order.setNote(clientOrder.getNote());
			order.setPaymentMethod(clientOrder.getPaymentMethod());
			order.setPaymentDetails(clientOrder.getPaymentDetails());

			// Convert cart items -> order items (use managed product references)
			for (CartItem cartItem : new ArrayList<>(cart.getItems())) {
				Product product = em.find(Product.class, cartItem.getProduct().getId()); // ensure managed product
				OrderItem oi = new OrderItem();
				oi.setProduct(product);
				oi.setQuantity(cartItem.getQuantity());
				oi.setOrder(order);
				order.getItems().add(oi);
			}

			em.persist(order);
			em.flush(); // make sure order id exists

			// remove cart items (use managed instances)
			for (CartItem ci : new ArrayList<>(cart.getItems())) {
				// If orphanRemoval=true on Cart.items, removing from the collection is enough.
				cart.getItems().remove(ci);
				if (!em.contains(ci)) {
					ci = em.find(CartItem.class, ci.getId());
				}
				if (ci != null) {
					em.remove(ci);
				}
			}
			// optionally em.merge(cart) but cart is managed

			em.getTransaction().commit();

			client.sendToClient(new Message("order_placed_successfully", order, null));
		} catch (Exception e) {
			if (em.getTransaction() != null && em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			try {
				client.sendToClient(new Message("order_error", "Failed to place order: " + e.getMessage(), null));
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		} finally {
			em.close();
		}
	}






	private Cart getOrCreateCartForCustomer(Customer customer) {
		EntityManager em = emf.createEntityManager();
		Cart cart;

		try {
			em.getTransaction().begin();

			cart = em.createQuery(
							"SELECT c FROM Cart c WHERE c.customer.id = :uid", Cart.class)
					.setParameter("uid", customer.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);

			if (cart == null) {
				cart = new Cart(customer);
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

	private Cart getCartByCustomerId(Long customerId) {
		EntityManager em = emf.createEntityManager();
		Cart cart;

		try {
			cart = em.createQuery(
							"SELECT c FROM Cart c WHERE c.customer.id = :uid", Cart.class)
					.setParameter("uid", customerId)
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