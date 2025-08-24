package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;

import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.function.Function;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

public class SimpleServer extends AbstractServer {
	// Thread-safe collection for subscribers
	private static final CopyOnWriteArrayList<SubscribedClient> subscribersList = new CopyOnWriteArrayList<>();

	// Volatile reference + explicit lock to protect the in-memory catalog snapshot
	private static volatile Catalog catalog = new Catalog(new ArrayList<>());
	private static final ReentrantReadWriteLock catalogLock = new ReentrantReadWriteLock();

	// Use ConcurrentHashMap for thread-safe cache
	private static final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();

	// Per-username locks to avoid registration races without blocking unrelated usernames
	private static final ConcurrentHashMap<String, Object> usernameLocks = new ConcurrentHashMap<>();

	private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory("my-persistence-unit");

	public SimpleServer(int port) {
		super(port);
		initCaches(); // Initialize caches before loading catalog

		catalogLock.writeLock().lock();
		try {
			//catalog.setFlowers(getListFromDB(Product.class));
			catalog.setFlowers(getEnabledProductsFromDB());
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
				else if(msgString.equals("submit_complaint")){
					handleSubmitComplaint(message, client);
				}
				else if (msgString.startsWith("add_custom_bouquet_to_cart")) {
					handleAddCustomBouquetToCart((Message) msg, client);
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

	private void handleAddCustomBouquetToCart(Message message, ConnectionToClient client) {
		EntityManager em = emf.createEntityManager();
		try {
			// Payload: object = CustomBouquet (transient), objectList[0] = User
			User user = (User) message.getObjectList().get(0);
			if (!(user instanceof Customer)) {
				client.sendToClient(new Message("error", "User is not a customer", null));
				return;
			}
			CustomBouquet bouquet = (CustomBouquet) message.getObject();

			em.getTransaction().begin();

			Customer managedCustomer = em.find(Customer.class, ((Customer) user).getId());
			if (managedCustomer == null) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("error", "Customer not found", null));
				return;
			}

			// find or create cart
			Cart cart = em.createQuery(
							"SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.customer.id = :uid",
							Cart.class)
					.setParameter("uid", managedCustomer.getId())
					.getResultStream().findFirst().orElse(null);
			if (cart == null) {
				cart = new Cart(managedCustomer);
				em.persist(cart);
				managedCustomer.setCart(cart);
			}

			// Attach creator + backrefs for bouquet lines
			bouquet.setCreatedBy(managedCustomer);
			if (bouquet.getItems() != null) {
				for (CustomBouquetItem it : bouquet.getItems()) {
					it.setBouquet(bouquet);
				}
			}
			bouquet.recomputeTotalPrice();

			// Create a CartItem owning this bouquet (product is null)
			CartItem newItem = new CartItem();
			newItem.setCart(cart);
			newItem.setCustomBouquet(bouquet);
			newItem.setQuantity(1);

			// Persist via the CartItem cascade OR explicitly (both work)
			em.persist(newItem);
			cart.getItems().add(newItem);

			em.merge(cart);
			em.getTransaction().commit();

			// send back refreshed cart with all relations
			Cart refreshed = fetchCartWithEverything(em, cart.getId());
			client.sendToClient(new Message("cart_data", refreshed, null));

		} catch (Exception e) {
			e.printStackTrace();
			if (em.getTransaction().isActive()) em.getTransaction().rollback();
			try {
				client.sendToClient(new Message("error", "Failed to add custom bouquet to cart", null));
			} catch (IOException ioException) { ioException.printStackTrace(); }
		} finally {
			em.close();
		}
	}


	private void handleSubmitComplaint(Message msg, ConnectionToClient client) {
		Customer customer = (Customer) (msg.getObjectList().get(1));
		Complaint complaint = (Complaint) msg.getObjectList().get(0);
		Order order = (Order) msg.getObjectList().get(2);
		// TODO: Implementation (thread-safe patterns: per-request EntityManager / Session)
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

	@SuppressWarnings("unchecked")
	private void handleDeleteProduct(Message msg, ConnectionToClient client, Session session) {
		Transaction tx = null;
		try {
			tx = session.beginTransaction();

			Long productId = (Long) msg.getObject();
			Product p = session.get(Product.class, productId);
			if (p == null) {
				if (tx != null && tx.isActive()) tx.rollback();
				client.sendToClient(new Message("product_not_found", null, null));
				return;
			}

			// A) CartItems that reference this Product directly
			List<Long> cartItemsForProduct = session.createQuery("""
            select ci.id
              from CartItem ci
             where ci.product.id = :pid
        """, Long.class).setParameter("pid", productId).getResultList();

			// B) Bouquets containing this Product as a flower (via CustomBouquetItem)
			List<Long> bouquetIds = session.createQuery("""
            select distinct cbi.bouquet.id
              from CustomBouquetItem cbi
             where cbi.flower.id = :pid
        """, Long.class).setParameter("pid", productId).getResultList();

			// Split bouquets into:
			//  - deletable (not referenced by any OrderItem)
			//  - protected (referenced by an OrderItem → must NOT delete; only null the flower ref)
			List<Long> deletableBouquetIds = new java.util.ArrayList<>();
			List<Long> protectedBouquetIds = java.util.Collections.emptyList();
			if (!bouquetIds.isEmpty()) {
				protectedBouquetIds = session.createQuery("""
                select distinct oi.customBouquet.id
                  from OrderItem oi
                 where oi.customBouquet.id in (:bids)
            """, Long.class).setParameterList("bids", bouquetIds).getResultList();

				deletableBouquetIds.addAll(bouquetIds);
				deletableBouquetIds.removeAll(protectedBouquetIds);
			}

			// C) CartItems that reference those deletable bouquets
			List<Long> cartItemsForBouquets = java.util.Collections.emptyList();
			if (!deletableBouquetIds.isEmpty()) {
				cartItemsForBouquets = session.createQuery("""
                select ci.id
                  from CartItem ci
                 where ci.customBouquet.id in (:bids)
            """, Long.class).setParameterList("bids", deletableBouquetIds).getResultList();
			}

			// D) Delete cart items (product + bouquets)
			if (!cartItemsForProduct.isEmpty()) {
				session.createQuery("""
                delete from CartItem ci
                 where ci.id in (:ids)
            """).setParameterList("ids", cartItemsForProduct).executeUpdate();
			}
			if (!cartItemsForBouquets.isEmpty()) {
				session.createQuery("""
                delete from CartItem ci
                 where ci.id in (:ids)
            """).setParameterList("ids", cartItemsForBouquets).executeUpdate();
			}

			// E) Delete bouquet items, then bouquets (bulk HQL doesn't cascade)
			if (!deletableBouquetIds.isEmpty()) {
				session.createQuery("""
                delete from CustomBouquetItem cbi
                 where cbi.bouquet.id in (:bids)
            """).setParameterList("bids", deletableBouquetIds).executeUpdate();

				session.createQuery("""
                delete from CustomBouquet cb
                 where cb.id in (:bids)
            """).setParameterList("bids", deletableBouquetIds).executeUpdate();
			}

			// F) For protected bouquets (in orders), just null the flower FK so product delete won’t be blocked
			if (!protectedBouquetIds.isEmpty()) {
				session.createQuery("""
                update CustomBouquetItem cbi
                   set cbi.flower = null
                 where cbi.flower.id = :pid
                   and cbi.bouquet.id in (:bids)
            """).setParameter("pid", productId)
						.setParameterList("bids", protectedBouquetIds)
						.executeUpdate();
			}

			// G) Detach product from historical OrderItems (snapshots will render history)
			session.createQuery("""
            update OrderItem oi
               set oi.product = null
             where oi.product.id = :pid
        """).setParameter("pid", productId).executeUpdate();

			// H) HARD delete the product row (bypass @SoftDelete)
			session.createNativeQuery("DELETE FROM Products WHERE ID = :pid")
					.setParameter("pid", productId)
					.executeUpdate();

			tx.commit();

			// I) Update in-memory catalog cache
			catalogLock.writeLock().lock();
			try {
				catalog.getFlowers().remove(catalog.getProductById(productId));
			} finally {
				catalogLock.writeLock().unlock();
			}

			// J) Notify clients
			sendToAllClients(new Message("delete_product", productId, null)); // PrimaryController

			Map<String, Object> payload = new HashMap<>();
			payload.put("productId", productId);
			payload.put("removedCartItemIds",
					java.util.stream.Stream.concat(cartItemsForProduct.stream(), cartItemsForBouquets.stream()).collect(Collectors.toList()));
			payload.put("removedCount",
					cartItemsForProduct.size() + cartItemsForBouquets.size());
			payload.put("deletedBouquetIds", deletableBouquetIds);
			payload.put("protectedBouquetIds", protectedBouquetIds); // FYI only
			sendToAllClients(new Message("item_removed", payload, null)); // CartController

		} catch (Exception e) {
			if (tx != null && tx.isActive()) tx.rollback();
			e.printStackTrace();
			try { client.sendToClient(new Message("delete_failed", null, null)); } catch (IOException ignored) {}
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
			if (tx != null && tx.isActive()) {
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
		return userCache.containsKey(username);
	}

	private void handleUserRegistration(Message msg, ConnectionToClient client, Session session) throws IOException {
		String username = ((User)(msg.getObject())).getUsername();

		// Per-username lock to prevent race: double registration of same username
		final Object lock = usernameLocks.computeIfAbsent(username, k -> new Object());
		synchronized (lock) {
			if(checkExistence(username)) {
				Message errorMsg = new Message("user already exists", msg.getObject(), null);
				client.sendToClient(errorMsg);
				return;
			}
			Transaction tx = null;
			try {
				tx = session.beginTransaction();
				User user = (User) msg.getObject();

				// Save to database
				session.save(user);
				session.flush();
				tx.commit();

				// Publish to cache only after successful commit (happens-before via CHM)
				userCache.putIfAbsent(user.getUsername(), user);

				Message message = new Message("registered", null, null);
				client.sendToClient(message);
			} catch (Exception e) {
				if (tx != null && tx.isActive()) {
					tx.rollback();
				}
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
		try {
			Message message = new Message("catalog", catalog, null);
			client.sendToClient(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Build a filtered, defensive snapshot of the catalog under the read lock
	public List<Product> getEnabledProductsFromDB() {
		List<Product> resultList;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			// Explicit JPQL query to only return enabled products
			resultList = session.createQuery(
					"FROM Product p WHERE p.isDisabled = false", Product.class
			).getResultList();
		} catch (Exception e) {
			e.printStackTrace();
			resultList = new ArrayList<>();
		}
		return resultList;
	}


	private static boolean isFlower(Product p) {
		// Adjust the check if your "flower" typing is different
		String t = p.getType();
		return t != null && t.equalsIgnoreCase("flower");
	}

	private void handleProductEdit(Message msg) {
		String msgString = msg.getMessage();
		Product product = (Product)(msg.getObject());
		if (product != null) {
			try {
				Long productId = product.getId();
				double newPrice = product.getPrice();
				double newDiscountPercentage = product.getDiscountPercentage();
				String newProductName = product.getName();
				String newType = product.getType();
				String newImagePath = product.getImagePath();
				String newColor = product.getColor();

				// Update database first
				boolean updateSuccess = updateProduct(productId, newProductName, newColor ,newPrice, newDiscountPercentage,newType, newImagePath);

				if (updateSuccess) {
					// Replace in-memory catalog snapshot atomically
					catalogLock.writeLock().lock();
					try {
						catalog.setFlowers(getListFromDB(Product.class));
						Message message = new Message(msgString, product, null);
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
			@SuppressWarnings("unchecked")
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

	public boolean updateProduct(Long productId, String newProductName,String newColor, double newPrice, double newDiscount,String newType, String newImagePath) {
		// Keep the lock, but guarantee proper release with try/finally to avoid double-unlock paths
		catalogLock.writeLock().lock();
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			Product product = session.get(Product.class, productId);
			if (product != null) {
				product.setPrice(newPrice);
				product.setDiscountPercentage(newDiscount);
				product.setName(newProductName);
				product.setType(newType);
				product.setImagePath(newImagePath);
				product.setColor(newColor);
				session.update(product);
				tx.commit();
				return true;
			} else {
				System.out.println("Product not found with ID: " + productId);
				if (tx != null && tx.isActive()) {
					tx.rollback();
				}
				return false;
			}
		} catch (Exception e) {
			if (tx != null && tx.isActive()) {
				tx.rollback();
			}
			e.printStackTrace();
			return false;
		} finally {
			catalogLock.writeLock().unlock();
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
					.filter(ci -> ci.getProduct() != null && Objects.equals(ci.getProduct().getId(), productId))
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
			Cart refreshedCart = fetchCartWithEverything(em, cart.getId());
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
			Cart refreshedCart = fetchCartWithEverything(em, cart.getId());
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
			Cart refreshedCart = fetchCartWithEverything(em, cart.getId());
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

			Cart cart = em.createQuery(
							"SELECT c FROM Cart c WHERE c.customer.id = :uid", Cart.class)
					.setParameter("uid", customer.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);

			if (cart == null) {
				em.getTransaction().begin();
				cart = new Cart(customer);
				em.persist(cart);
				em.getTransaction().commit();
			}

			Cart hydrated = fetchCartWithEverything(em, cart.getId());

			client.sendToClient(new Message("cart_data", hydrated, null));
			if(cart != null) {
				System.out.println("Cart found with id: " + cart.getId());
				System.out.println("Items count: " + cart.getItems().size());
				for (CartItem ci : cart.getItems()) {
					if (ci.getProduct() != null) {
						System.out.println("Item product id: " + ci.getProduct().getId() + ", quantity: " + ci.getQuantity());
					} else if (ci.getCustomBouquet() != null) {
						System.out.println("Item custom bouquet, quantity: " + ci.getQuantity());
					} else {
						System.out.println("Item without product/bouquet? id=" + ci.getId());
					}
				}
			} else {
				System.out.println("No cart found for user id " + user.getId());
			}

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

			List<Order> orders = fetchOrdersWithEverything(em, customer.getId());
			client.sendToClient(new Message("orders_data", orders, null));

		} catch (Exception e) {
			e.printStackTrace();
			try { client.sendToClient(new Message("error", "Failed to load orders", null)); }
			catch (IOException ex) { ex.printStackTrace(); }
		} finally {
			em.close();
		}
	}


	/** Load orders with items (+ product + bouquet ref), then hydrate bouquet lines. */
	private List<Order> fetchOrdersWithEverything(EntityManager em, Long customerId) {
		// 1) fetch orders + items + to-one associations (safe to join-fetch)
		List<Order> orders = em.createQuery(
						"SELECT DISTINCT o FROM Order o " +
								"LEFT JOIN FETCH o.items i " +
								"LEFT JOIN FETCH i.product " +          // to-one: OK to fetch here
								"LEFT JOIN FETCH i.customBouquet cb " + // to-one: OK to fetch here
								"WHERE o.customer.id = :cid " +
								"ORDER BY o.orderDate DESC",
						Order.class)
				.setParameter("cid", customerId)
				.getResultList();

		// 2) collect bouquet ids from the items we just loaded
		List<Long> bouquetIds = orders.stream()
				.flatMap(o -> o.getItems().stream())
				.map(OrderItem::getCustomBouquet)
				.filter(Objects::nonNull)
				.map(CustomBouquet::getId)
				.distinct()
				.collect(Collectors.toList());

		if (!bouquetIds.isEmpty()) {
			// 3) hydrate bouquet -> lines (+ each line’s flower) in a second query
			em.createQuery(
							"SELECT DISTINCT cb FROM CustomBouquet cb " +
									"LEFT JOIN FETCH cb.items li " +    // second bag: fetch here
									"LEFT JOIN FETCH li.flower " +
									"WHERE cb.id IN :ids", CustomBouquet.class)
					.setParameter("ids", bouquetIds)
					.getResultList();
			// Now the cb references attached to order items have their lines initialized by the PC.
		}

		return orders;
	}


	/** Load a cart with items + product + custom bouquet (+ bouquet lines + line flower). */
	private Cart fetchCartWithEverything(EntityManager em, Long cartId) {
		// Step 1: fetch cart, cart items, products, and bouquet *references* (no bouquet lines yet)
		Cart cart = em.createQuery(
						"SELECT DISTINCT c FROM Cart c " +
								"LEFT JOIN FETCH c.items i " +
								"LEFT JOIN FETCH i.product " +
								"LEFT JOIN FETCH i.customBouquet cb " +
								"WHERE c.id = :cid", Cart.class)
				.setParameter("cid", cartId)
				.getSingleResult();

		// Collect bouquet ids present in the cart
		List<Long> bouquetIds = cart.getItems().stream()
				.map(CartItem::getCustomBouquet)
				.filter(Objects::nonNull)
				.map(CustomBouquet::getId)
				.distinct()
				.collect(Collectors.toList());

		if (!bouquetIds.isEmpty()) {
			// Step 2: initialize bouquet -> items (+ each line's flower) in *one* second query
			em.createQuery(
							"SELECT DISTINCT cb FROM CustomBouquet cb " +
									"LEFT JOIN FETCH cb.items li " +
									"LEFT JOIN FETCH li.flower " +
									"WHERE cb.id IN :ids", CustomBouquet.class)
					.setParameter("ids", bouquetIds)
					.getResultList();
			// The persistence context ensures the cb instances attached to CartItems now have their items initialized.
		}

		return cart;
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

			// 1) Fetch cart + items + product + bouquet (NO bouquet.items here)
			Cart cart = em.createQuery(
							"SELECT DISTINCT c FROM Cart c " +
									"LEFT JOIN FETCH c.items i " +
									"LEFT JOIN FETCH i.product p " +
									"LEFT JOIN FETCH i.customBouquet cb " +
									"WHERE c.customer.id = :cid", Cart.class)
					.setParameter("cid", managedCustomer.getId())
					.getResultStream()
					.findFirst()
					.orElse(null);

			if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
				em.getTransaction().rollback();
				client.sendToClient(new Message("order_error", "Cart is empty", null));
				return;
			}

			// 2) Fetch bouquet.items in a SECOND query to avoid MultipleBagFetchException
			java.util.Set<Long> bouquetIds = new java.util.HashSet<>();
			for (CartItem ci : cart.getItems()) {
				if (ci.getCustomBouquet() != null && ci.getCustomBouquet().getId() != null) {
					bouquetIds.add(ci.getCustomBouquet().getId());
				}
			}
			if (!bouquetIds.isEmpty()) {
				// One bag per query is fine
				em.createQuery(
								"SELECT DISTINCT cb FROM CustomBouquet cb " +
										"LEFT JOIN FETCH cb.items cbi " +     // (bag) fetched here
										"LEFT JOIN FETCH cbi.flower f " +
										"WHERE cb.id IN :ids", CustomBouquet.class)
						.setParameter("ids", bouquetIds)
						.getResultList(); // populates the same managed cb instances
			}

			// 3) Build managed Order and copy fields
			Order order = new Order();
			order.setCustomer(managedCustomer);
			order.setStoreLocation(clientOrder.getStoreLocation());
			order.setDelivery(clientOrder.getDelivery());
			order.setOrderDate(java.time.LocalDateTime.now());
			order.setDeliveryDateTime(clientOrder.getDeliveryDateTime());
			order.setRecipientPhone(clientOrder.getRecipientPhone());
			order.setDeliveryAddress(clientOrder.getDeliveryAddress());
			order.setNote(clientOrder.getNote());
			order.setPaymentMethod(clientOrder.getPaymentMethod());
			order.setPaymentDetails(clientOrder.getPaymentDetails());

			// 4) Convert cart items -> order items, snapshotting
			for (CartItem cartItem : new java.util.ArrayList<>(cart.getItems())) {
				OrderItem oi = new OrderItem();
				oi.setOrder(order);
				oi.setQuantity(Math.max(1, cartItem.getQuantity()));

				if (cartItem.getProduct() != null) {
					Product product = em.find(Product.class, cartItem.getProduct().getId());
					oi.setProduct(product);
					if (product != null) {
						oi.snapshotFromProduct(product); // name + price + image
						// If you want discount-aware price, use product.getSalePrice()
						// oi.setUnitPriceSnapshot(java.math.BigDecimal.valueOf(product.getSalePrice()));
					}
				} else if (cartItem.getCustomBouquet() != null) {
					// Clone the bouquet for the order (copies item snapshots, no live Product FK)
					CustomBouquet copy = cloneBouquetForOrder(cartItem.getCustomBouquet(), managedCustomer);
					oi.setCustomBouquet(copy);
					oi.snapshotFromBouquet(copy);
				}

				order.getItems().add(oi);
			}

			// 5) Persist order (cascades persist to items / order-owned bouquet)
			em.persist(order);
			em.flush();

			// 6) Clear cart safely
			for (CartItem ci : new java.util.ArrayList<>(cart.getItems())) {
				cart.getItems().remove(ci);
				CartItem managedCI = em.contains(ci) ? ci : em.find(CartItem.class, ci.getId());
				if (managedCI != null) em.remove(managedCI);
			}

			em.getTransaction().commit();
			client.sendToClient(new Message("order_placed_successfully", order, null));

		} catch (Exception e) {
			if (em.getTransaction() != null && em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			try {
				client.sendToClient(new Message("order_error", "Failed to place order: " + e.getMessage(), null));
			} catch (java.io.IOException ioException) {
				ioException.printStackTrace();
			}
		} finally {
			em.close();
		}
	}


	private CustomBouquet cloneBouquetForOrder(CustomBouquet src, Customer creator) {
		if (src == null) return null;

		CustomBouquet copy = new CustomBouquet();
		copy.setCreatedBy(creator);
		copy.setCreatedAt(src.getCreatedAt());
		copy.setInstructions(src.getInstructions());

		if (src.getItems() != null) {
			for (CustomBouquetItem it : src.getItems()) {
				CustomBouquetItem line = new CustomBouquetItem();
				line.setBouquet(copy);
				line.setFlower(null); // drop FK to Product; keep snapshots only
				line.setFlowerNameSnapshot(it.getFlowerNameSnapshot());
				line.setUnitPriceSnapshot(it.getUnitPriceSnapshot() != null ? it.getUnitPriceSnapshot() : java.math.BigDecimal.ZERO);
				line.setQuantity(Math.max(0, it.getQuantity()));
				copy.getItems().add(line);
			}
		}

		copy.recomputeTotalPrice();
		return copy;
	}


	private java.math.BigDecimal safe(java.math.BigDecimal v) {
		return (v != null) ? v : java.math.BigDecimal.ZERO;
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
		} catch (Exception e) {
			if (em.getTransaction().isActive()) em.getTransaction().rollback();
			throw e;
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
		} catch (Exception e) {
			if (em.getTransaction().isActive()) em.getTransaction().rollback();
			throw e;
		} finally {
			em.close();
		}
	}

	private Cart getCartByCustomerId(Long customerId) {
		EntityManager em = emf.createEntityManager();
		try {
			return em.createQuery(
							"SELECT c FROM Cart c WHERE c.customer.id = :uid", Cart.class)
					.setParameter("uid", customerId)
					.getSingleResult();
		} finally {
			em.close();
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
