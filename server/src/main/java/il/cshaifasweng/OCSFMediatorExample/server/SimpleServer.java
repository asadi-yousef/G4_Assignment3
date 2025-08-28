package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;

import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimpleServer extends AbstractServer {

    // ---- Subscribers (thread-safe) ----
    private static final CopyOnWriteArrayList<SubscribedClient> subscribersList = new CopyOnWriteArrayList<>();

    // ---- In-memory catalog snapshot + lock ----
    private static volatile Catalog catalog = new Catalog(new ArrayList<>());
    private static final ReentrantReadWriteLock catalogLock = new ReentrantReadWriteLock();

    // ---- Caches + per-username locks ----
    private static final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Object> usernameLocks = new ConcurrentHashMap<>();

    public SimpleServer(int port) {
        super(port);
        initCaches(); // warm user cache before catalog

        // Initialize catalog snapshot once at startup
        catalogLock.writeLock().lock();
        try {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                catalog.setFlowers(getListFromDB(session,Product.class));
                System.out.println("number of products in catalog: " + catalog.getFlowers().size());
            }
        } finally {
            catalogLock.writeLock().unlock();
        }
    }

    @Override
    protected void clientConnected(ConnectionToClient client) {
        super.clientConnected(client);
        subscribersList.add(new SubscribedClient(client));
    }

    /* ----------------------- Generic DB helpers (session passed in) ----------------------- */

    public <T> List<T> getListFromDB(Session session, Class<T> entityClass) {
        Transaction tx = session.getTransaction().isActive() ? session.getTransaction() : session.beginTransaction();
        try {
            List<T> list = session.createQuery("from " + entityClass.getSimpleName(), entityClass).list();
            // read-only tx: commit for portability/consistency
            if (tx.isActive()) tx.commit();
            return list;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public <T, K> void loadToCache(Session session, Class<T> entityClass, ConcurrentHashMap<K, T> cache, Function<T, K> keyExtractor) {
        List<T> entities = getListFromDB(session, entityClass);
        cache.clear();
        for (T entity : entities) {
            cache.put(keyExtractor.apply(entity), entity);
        }
    }

    public void initCaches() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            loadToCache(session, User.class, userCache, User::getUsername);
        }
    }

    /* ----------------------- Request dispatch ----------------------- */

    @Override
    protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
        if (msg == null) return;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // one session per incoming message; pass it to all handlers
            if (msg instanceof Message) {
                Message m = (Message) msg;
                String key = m.getMessage();

                if ("register".equals(key)) {
                    handleUserRegistration(m, client, session);
                } else if (key != null && key.startsWith("add_product")) {
                    handleAddProduct(m, client, session);
                } else if (key != null && key.startsWith("editProduct")) {
                    handleProductEdit(m, client, session);
                } else if (key != null && key.startsWith("delete_product")) {
                    handleDeleteProduct(m, client, session);
                } else if (key != null && key.startsWith("add_to_cart")) {
                    handleAddToCart(m, client, session);
                } else if (key != null && key.startsWith("remove_cart_item")) {
                    handleRemoveFromCart(m, client, session);
                } else if (key != null && key.startsWith("request_cart")) {
                    handleCartRequest(m, client, session);
                } else if (key != null && key.startsWith("request_orders")) {
                    handleOrdersRequest(m, client, session);
                } else if ("request_customer_data".equals(key)) {
                    handleCustomerDataRequest(m, client, session);
                } else if ("update_profile".equals(key)) {
                    handleProfileUpdate(m, client, session);
                } else if (key != null && key.startsWith("place_order")) {
                    handlePlaceOrder(m, client, session);
                } else if (key != null && key.startsWith("update_cart_item_quantity")) {
                    handleUpdateCartItemQuantity(m, client, session);
                } else if (key != null && key.startsWith("request_branches")) {
                    handleBranchesRequest(m, client, session);
                } else if ("check existence".equals(key)) {
                    handleUserAuthentication(m, client, session);
                } else if (key != null && key.startsWith("add_custom_bouquet_to_cart")) {
                    handleAddCustomBouquetToCart(m, client, session);
                }
            }

            // non-Message signals (kept as-is)
            String msgString = msg.toString();
            if ("request_catalog".equals(msgString)) {
                handleCatalogRequest(client);
            } else if (msgString.startsWith("remove client")) {
                handleClientRemoval(client);
                System.out.println("removed subscribed client");
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                client.sendToClient(new Message("server_error", null, null));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    /* ----------------------- Handlers (all receive Session) ----------------------- */

    private void handleAddCustomBouquetToCart(Message message, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            User user = (User) message.getObjectList().get(0);
            if (!(user instanceof Customer)) {
                client.sendToClient(new Message("error", "User is not a customer", null));
                tx.rollback();
                return;
            }
            CustomBouquet bouquet = (CustomBouquet) message.getObject();

            Customer managedCustomer = session.get(Customer.class, ((Customer) user).getId());
            if (managedCustomer == null) {
                tx.rollback();
                client.sendToClient(new Message("error", "Customer not found", null));
                return;
            }

            Cart cart = session.createQuery(
                            "SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.customer.id = :uid",
                            Cart.class).setParameter("uid", managedCustomer.getId())
                    .uniqueResultOptional().orElse(null);
            if (cart == null) {
                cart = new Cart(managedCustomer);
                session.persist(cart);
                managedCustomer.setCart(cart);
            }

            bouquet.setCreatedBy(managedCustomer);
            if (bouquet.getItems() != null) {
                for (CustomBouquetItem it : bouquet.getItems()) {
                    it.setBouquet(bouquet);
                }
            }
            bouquet.recomputeTotalPrice();

            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setCustomBouquet(bouquet);
            newItem.setQuantity(1);

            session.persist(newItem);
            cart.getItems().add(newItem);

            session.merge(cart);
            tx.commit();

            // Refresh view using a short new session (read-only)
            try (Session s2 = HibernateUtil.getSessionFactory().openSession()) {
                Cart refreshed = fetchCartWithEverything(s2, cart.getId());
                client.sendToClient(new Message("cart_data", refreshed, null));
            }
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("error", "Failed to add custom bouquet to cart", null)); }
            catch (IOException ignored) {}
        }
    }

    private void handleBranchesRequest(Message msg, ConnectionToClient client, Session session) throws IOException {
        List<Branch> branches = getListFromDB(session, Branch.class);
        for (Branch branch : branches) {
            System.out.println("Branch: " + branch.getName());
        }
        client.sendToClient(new Message("Branches", branches, null));
    }

    private void handleCustomerDataRequest(Message message, ConnectionToClient client, Session session) {
        try {
            Object payload = message.getObject();
            User user = null;

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

            Customer customer = (user instanceof Customer)
                    ? (Customer) user
                    : session.get(Customer.class, user.getId());

            client.sendToClient(new Message("customer_data_response", customer, null));

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
        Transaction tx = session.beginTransaction();
        try {
            Object payload = message.getObject();
            User user = null;
            Customer customer = null;

            if (payload instanceof User) {
                user = (User) payload;
                if (user instanceof Customer) customer = (Customer) user;
            } else if (payload instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) payload;
                if (!list.isEmpty() && list.get(0) instanceof User) user = (User) list.get(0);
                if (list.size() > 1 && list.get(1) instanceof Customer) customer = (Customer) list.get(1);
            }

            if (user == null) {
                client.sendToClient(new Message("profile_update_failed", "Invalid user data", null));
                tx.rollback();
                return;
            }

            session.merge(user);
            if (customer != null) session.merge(customer);
            tx.commit();

            client.sendToClient(new Message("profile_updated_success", null, null));
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try {
                client.sendToClient(new Message("profile_update_failed",
                        "Database update failed: " + e.getMessage(), null));
            } catch (IOException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void handleDeleteProduct(Message msg, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Long productId = (Long) msg.getObject();
            Product p = session.get(Product.class, productId);
            if (p == null) {
                tx.rollback();
                client.sendToClient(new Message("product_not_found", null, null));
                return;
            }

            // A) CartItems referencing product
            List<Long> cartItemsForProduct = session.createQuery("""
                select ci.id
                  from CartItem ci
                 where ci.product.id = :pid
            """, Long.class).setParameter("pid", productId).getResultList();

            // B) Bouquets containing product via CustomBouquetItem
            List<Long> bouquetIds = session.createQuery("""
                select distinct cbi.bouquet.id
                  from CustomBouquetItem cbi
                 where cbi.flower.id = :pid
            """, Long.class).setParameter("pid", productId).getResultList();

            List<Long> deletableBouquetIds = new ArrayList<>();
            List<Long> protectedBouquetIds = Collections.emptyList();

            if (!bouquetIds.isEmpty()) {
                protectedBouquetIds = session.createQuery("""
                    select distinct oi.customBouquet.id
                      from OrderItem oi
                     where oi.customBouquet.id in (:bids)
                """, Long.class).setParameterList("bids", bouquetIds).getResultList();

                deletableBouquetIds.addAll(bouquetIds);
                deletableBouquetIds.removeAll(protectedBouquetIds);
            }

            // C) CartItems for deletable bouquets
            List<Long> cartItemsForBouquets = Collections.emptyList();
            if (!deletableBouquetIds.isEmpty()) {
                cartItemsForBouquets = session.createQuery("""
                    select ci.id
                      from CartItem ci
                     where ci.customBouquet.id in (:bids)
                """, Long.class).setParameterList("bids", deletableBouquetIds).getResultList();
            }

            // D) Delete cart items (product + bouquets)
            if (!cartItemsForProduct.isEmpty()) {
                session.createQuery("delete from CartItem ci where ci.id in (:ids)")
                        .setParameterList("ids", cartItemsForProduct).executeUpdate();
            }
            if (!cartItemsForBouquets.isEmpty()) {
                session.createQuery("delete from CartItem ci where ci.id in (:ids)")
                        .setParameterList("ids", cartItemsForBouquets).executeUpdate();
            }

            // E) Delete bouquet items, then bouquets (for deletable)
            if (!deletableBouquetIds.isEmpty()) {
                session.createQuery("delete from CustomBouquetItem cbi where cbi.bouquet.id in (:bids)")
                        .setParameterList("bids", deletableBouquetIds).executeUpdate();

                session.createQuery("delete from CustomBouquet cb where cb.id in (:bids)")
                        .setParameterList("bids", deletableBouquetIds).executeUpdate();
            }

            // F) For protected bouquets, null the flower FK
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

            // G) Detach product from historical OrderItems
            session.createQuery("update OrderItem oi set oi.product = null where oi.product.id = :pid")
                    .setParameter("pid", productId)
                    .executeUpdate();

            // H) HARD delete the product row
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
            sendToAllClients(new Message("delete_product", productId, null));

            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", productId);
            payload.put("removedCartItemIds",
                    java.util.stream.Stream.concat(cartItemsForProduct.stream(), cartItemsForBouquets.stream())
                            .collect(Collectors.toList()));
            payload.put("removedCount", cartItemsForProduct.size() + cartItemsForBouquets.size());
            payload.put("deletedBouquetIds", deletableBouquetIds);
            payload.put("protectedBouquetIds", protectedBouquetIds);
            sendToAllClients(new Message("item_removed", payload, null));

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("delete_failed", null, null)); } catch (IOException ignored) {}
        }
    }

    private void handleAddProduct(Message msg, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Product product = (Product) msg.getObject();
            session.save(product);
            tx.commit();

            // update in-memory snapshot
            catalogLock.writeLock().lock();
            try {
                catalog.getFlowers().add(product);
            } finally {
                catalogLock.writeLock().unlock();
            }

            sendToAllClients(new Message("add_product", product, null));
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("add_failed", null, null)); }
            catch (IOException ignored) {}
        }
    }

    private boolean checkExistence(String username) {
        return userCache.containsKey(username);
    }

    private void handleUserRegistration(Message msg, ConnectionToClient client, Session session) throws IOException {
        String username = ((User) (msg.getObject())).getUsername();
        final Object lock = usernameLocks.computeIfAbsent(username, k -> new Object());

        synchronized (lock) {
            if (checkExistence(username)) {
                client.sendToClient(new Message("user already exists", msg.getObject(), null));
                return;
            }
            Transaction tx = session.beginTransaction();
            try {
                User user = (User) msg.getObject();
                session.save(user);
                tx.commit();

                userCache.putIfAbsent(user.getUsername(), user);
                client.sendToClient(new Message("registered", null, null));
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                try { client.sendToClient(new Message("registration_failed", null, null)); }
                catch (IOException ignored) {}
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

    private void handleProductEdit(Message msg, ConnectionToClient client, Session session) {
        Product product = (Product) msg.getObject();
        if (product == null) return;

        boolean updateSuccess = updateProduct(session, product.getId(),
                product.getName(), product.getColor(), product.getPrice(),
                product.getDiscountPercentage(), product.getType(), product.getImagePath());

        if (updateSuccess) {
            // refresh in-memory catalog + propagate to open carts
            catalogLock.writeLock().lock();
            try {
                catalog.setFlowers(getListFromDB(session, Product.class));
            } finally {
                catalogLock.writeLock().unlock();
            }
            propagateProductEditToOpenCarts(session, product.getId());

            try { sendToAllClients(new Message(msg.getMessage(), product, null)); }
            catch (Exception ignored) {}
        }
    }

    private void propagateProductEditToOpenCarts(Session session, Long productId) {
        if (productId == null) return;
        Transaction tx = session.beginTransaction();
        try {
            Product managed = session.get(Product.class, productId);
            if (managed == null) {
                tx.rollback();
                return;
            }

            List<Long> affectedCartIds = session.createQuery("""
                select distinct ci.cart.id
                  from CartItem ci
                  left join ci.customBouquet cb
                  left join cb.items cbi
                 where (ci.product.id = :pid)
                    or (cbi.flower.id = :pid)
            """, Long.class).setParameter("pid", productId).getResultList();

            if (!affectedCartIds.isEmpty()) {
                java.math.BigDecimal newUnit = java.math.BigDecimal.valueOf(managed.getSalePrice());

                for (Long cid : affectedCartIds) {
                    Cart cart = fetchCartWithEverything(session, cid);
                    boolean cartChanged = false;

                    for (CartItem ci : cart.getItems()) {
                        if (ci.getProduct() != null && Objects.equals(ci.getProduct().getId(), productId)) {
                            ci.setProduct(managed);
                            cartChanged = true;
                        }
                        CustomBouquet cb = ci.getCustomBouquet();
                        if (cb != null && cb.getItems() != null) {
                            boolean bouquetChanged = false;
                            for (CustomBouquetItem line : cb.getItems()) {
                                Product f = line.getFlower();
                                if (f != null && Objects.equals(f.getId(), productId)) {
                                    line.setFlower(managed);
                                    line.setFlowerNameSnapshot(managed.getName());
                                    line.setUnitPriceSnapshot(newUnit);
                                    bouquetChanged = true;
                                }
                            }
                            if (bouquetChanged) {
                                cb.recomputeTotalPrice();
                                session.merge(cb);
                                cartChanged = true;
                            }
                        }
                    }
                    if (cartChanged) session.merge(cart);
                }
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        }
    }

    private void handleClientRemoval(ConnectionToClient client) {
        subscribersList.removeIf(subscribedClient -> subscribedClient.getClient().equals(client));
    }

    private void handleUserAuthentication(Message msg, ConnectionToClient client, Session session) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            List<String> info = (List<String>) msg.getObject();
            String username = info.get(0);
            String password = info.get(1);

            User user = userCache.get(username);

            if (user == null) {
                System.out.println("user not found");
                client.sendToClient(new Message("incorrect", null, null));
            } else {
                if (user.getPassword().equals(password)) {
                    System.out.println(user.getUsername());
                    System.out.println(user.getPassword());
                    client.sendToClient(new Message("correct", user, null));
                } else {
                    System.out.println("Incorrect password");
                    client.sendToClient(new Message("incorrect", null, null));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                System.out.println(e.getMessage());
                client.sendToClient(new Message("authentication_error", null, null));
            } catch (IOException ioException) {
                System.out.println(ioException.getMessage());
                ioException.printStackTrace();
            }
        }
    }

    public boolean updateProduct(Session session, Long productId, String newProductName, String newColor,
                                 double newPrice, double newDiscount, String newType, String newImagePath) {
        catalogLock.writeLock().lock();
        Transaction tx = session.beginTransaction();
        try {
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
                tx.rollback();
                return false;
            }
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            return false;
        } finally {
            catalogLock.writeLock().unlock();
        }
    }

    private void handleAddToCart(Message message, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Long productId = (Long) message.getObject();
            Customer customer = (Customer) message.getObjectList().get(0);

            Customer managedCustomer = session.get(Customer.class, customer.getId());
            Product product = session.get(Product.class, productId);

            if (managedCustomer == null || product == null) {
                tx.rollback();
                client.sendToClient(new Message("error", "Customer or product not found", null));
                return;
            }

            Cart cart = managedCustomer.getCart();
            if (cart == null) {
                cart = new Cart(managedCustomer);
                session.persist(cart);
                managedCustomer.setCart(cart);
            }

            CartItem existingItem = cart.getItems().stream()
                    .filter(ci -> ci.getProduct() != null && Objects.equals(ci.getProduct().getId(), productId))
                    .findFirst().orElse(null);

            if (existingItem != null) {
                existingItem.setQuantity(existingItem.getQuantity() + 1);
            } else {
                CartItem newItem = new CartItem(product, cart, 1);
                cart.getItems().add(newItem);
                session.persist(newItem);
            }

            session.merge(cart);
            tx.commit();

            try (Session s2 = HibernateUtil.getSessionFactory().openSession()) {
                Cart refreshedCart = fetchCartWithEverything(s2, cart.getId());
                System.out.println("DEBUG: cart size: " + refreshedCart.getItems().size());
                client.sendToClient(new Message("cart_data", refreshedCart, null));
            }
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("error", "Failed to add to cart", null)); }
            catch (IOException ignored) {}
        }
    }

    private void handleRemoveFromCart(Message message, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Customer customer = (Customer) message.getObjectList().get(0);
            CartItem itemToRemove = (CartItem) message.getObjectList().get(1);

            Customer managedCustomer = session.get(Customer.class, customer.getId());
            if (managedCustomer == null) {
                tx.rollback();
                client.sendToClient(new Message("error", "Customer not found", null));
                return;
            }

            Cart cart = managedCustomer.getCart();
            if (cart == null) {
                tx.rollback();
                client.sendToClient(new Message("error", "Cart not found", null));
                return;
            }

            CartItem managedItem = session.get(CartItem.class, itemToRemove.getId());
            if (managedItem == null || !cart.getItems().contains(managedItem)) {
                tx.rollback();
                client.sendToClient(new Message("error", "Item not found in cart", null));
                return;
            }

            if (managedItem.getQuantity() > 1) {
                managedItem.setQuantity(managedItem.getQuantity() - 1);
                session.merge(managedItem);
            } else {
                cart.getItems().remove(managedItem);
                session.remove(managedItem);
            }

            session.merge(cart);
            tx.commit();

            try (Session s2 = HibernateUtil.getSessionFactory().openSession()) {
                Cart refreshedCart = fetchCartWithEverything(s2, cart.getId());
                client.sendToClient(new Message("cart_data", refreshedCart, null));
            }
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("error", "Failed to remove item from cart", null)); }
            catch (IOException ignored) {}
        }
    }

    private void handleUpdateCartItemQuantity(Message message, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Customer customer = (Customer) message.getObjectList().get(0);
            CartItem itemToUpdate = (CartItem) message.getObjectList().get(1);
            int newQuantity = (Integer) message.getObjectList().get(2);

            Customer managedCustomer = session.get(Customer.class, customer.getId());
            if (managedCustomer == null) {
                tx.rollback();
                client.sendToClient(new Message("error", "Customer not found", null));
                return;
            }

            Cart cart = managedCustomer.getCart();
            if (cart == null) {
                tx.rollback();
                client.sendToClient(new Message("error", "Cart not found", null));
                return;
            }

            CartItem managedItem = session.get(CartItem.class, itemToUpdate.getId());
            if (managedItem == null || !cart.getItems().contains(managedItem)) {
                tx.rollback();
                client.sendToClient(new Message("error", "Item not found in cart", null));
                return;
            }

            if (newQuantity <= 0) {
                cart.getItems().remove(managedItem);
                session.remove(managedItem);
            } else {
                managedItem.setQuantity(newQuantity);
                session.merge(managedItem);
            }

            session.merge(cart);
            tx.commit();

            try (Session s2 = HibernateUtil.getSessionFactory().openSession()) {
                Cart refreshedCart = fetchCartWithEverything(s2, cart.getId());
                client.sendToClient(new Message("cart_data", refreshedCart, null));
            }
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        }
    }

    private void handleCartRequest(Message message, ConnectionToClient client, Session session) {
        try {
            User user = (User) message.getObjectList().get(0);
            if (!(user instanceof Customer)) {
                client.sendToClient(new Message("error", "User is not a customer", null));
                return;
            }

            Transaction tx = session.beginTransaction();
            Customer customer = session.get(Customer.class, ((Customer) user).getId());

            Cart cart = session.createQuery(
                            "SELECT c FROM Cart c WHERE c.customer.id = :uid", Cart.class)
                    .setParameter("uid", customer.getId())
                    .uniqueResultOptional()
                    .orElse(null);

            if (cart == null) {
                cart = new Cart(customer);
                session.persist(cart);
            }
            tx.commit();

            Cart hydrated = fetchCartWithEverything(session, cart.getId());
            client.sendToClient(new Message("cart_data", hydrated, null));

            if (cart != null) {
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
            try { client.sendToClient(new Message("error", "Failed to load cart", null)); }
            catch (IOException ignored) {}
        }
    }

    private void handleOrdersRequest(Message message, ConnectionToClient client, Session session) {
        try {
            User user = (User) message.getObjectList().get(0);
            if (!(user instanceof Customer)) {
                client.sendToClient(new Message("error", "User is not a customer", null));
                return;
            }
            Customer customer = session.get(Customer.class, user.getId());

            List<Order> orders = fetchOrdersWithEverything(session, customer.getId());
            client.sendToClient(new Message("orders_data", orders, null));

        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("error", "Failed to load orders", null)); }
            catch (IOException ignored) {}
        }
    }

    /** Load orders with items (+ product + bouquet ref), then hydrate bouquet lines. */
    private List<Order> fetchOrdersWithEverything(Session session, Long customerId) {
        Transaction tx = session.getTransaction().isActive() ? session.getTransaction() : session.beginTransaction();
        List<Order> orders;
        try {
            orders = session.createQuery(
                    "SELECT DISTINCT o FROM Order o " +
                            "LEFT JOIN FETCH o.items i " +
                            "LEFT JOIN FETCH i.product " +
                            "LEFT JOIN FETCH i.customBouquet cb " +
                            "WHERE o.customer.id = :cid " +
                            "ORDER BY o.orderDate DESC",
                    Order.class).setParameter("cid", customerId).getResultList();

            List<Long> bouquetIds = orders.stream()
                    .flatMap(o -> o.getItems().stream())
                    .map(OrderItem::getCustomBouquet)
                    .filter(Objects::nonNull)
                    .map(CustomBouquet::getId)
                    .distinct()
                    .collect(Collectors.toList());

            if (!bouquetIds.isEmpty()) {
                session.createQuery(
                                "SELECT DISTINCT cb FROM CustomBouquet cb " +
                                        "LEFT JOIN FETCH cb.items li " +
                                        "LEFT JOIN FETCH li.flower " +
                                        "WHERE cb.id IN :ids", CustomBouquet.class)
                        .setParameter("ids", bouquetIds)
                        .getResultList();
            }
            if (tx.isActive()) tx.commit();
            return orders;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    /** Load a cart with items + product + custom bouquet (+ bouquet lines + line flower). */
    private Cart fetchCartWithEverything(Session session, Long cartId) {
        Transaction tx = session.getTransaction().isActive() ? session.getTransaction() : session.beginTransaction();
        try {
            Cart cart = session.createQuery(
                            "SELECT DISTINCT c FROM Cart c " +
                                    "LEFT JOIN FETCH c.items i " +
                                    "LEFT JOIN FETCH i.product " +
                                    "LEFT JOIN FETCH i.customBouquet cb " +
                                    "WHERE c.id = :cid", Cart.class)
                    .setParameter("cid", cartId)
                    .getSingleResult();

            List<Long> bouquetIds = cart.getItems().stream()
                    .map(CartItem::getCustomBouquet)
                    .filter(Objects::nonNull)
                    .map(CustomBouquet::getId)
                    .distinct()
                    .collect(Collectors.toList());

            if (!bouquetIds.isEmpty()) {
                session.createQuery(
                                "SELECT DISTINCT cb FROM CustomBouquet cb " +
                                        "LEFT JOIN FETCH cb.items li " +
                                        "LEFT JOIN FETCH li.flower " +
                                        "WHERE cb.id IN :ids", CustomBouquet.class)
                        .setParameter("ids", bouquetIds)
                        .getResultList();
            }
            if (tx.isActive()) tx.commit();
            return cart;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private void handlePlaceOrder(Message message, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Order clientOrder = (Order) message.getObject();
            if (clientOrder == null || clientOrder.getCustomer() == null) {
                client.sendToClient(new Message("order_error", "Missing order or customer data", null));
                tx.rollback();
                return;
            }

            Long customerId = clientOrder.getCustomer().getId();
            Customer managedCustomer = session.get(Customer.class, customerId);
            if (managedCustomer == null) {
                tx.rollback();
                client.sendToClient(new Message("order_error", "Customer not found", null));
                return;
            }

            // 1) Fetch cart + items + product + bouquet (no bouquet.items yet)
            Cart cart = session.createQuery(
                            "SELECT DISTINCT c FROM Cart c " +
                                    "LEFT JOIN FETCH c.items i " +
                                    "LEFT JOIN FETCH i.product p " +
                                    "LEFT JOIN FETCH i.customBouquet cb " +
                                    "WHERE c.customer.id = :cid", Cart.class)
                    .setParameter("cid", managedCustomer.getId())
                    .uniqueResultOptional()
                    .orElse(null);

            if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
                tx.rollback();
                client.sendToClient(new Message("order_error", "Cart is empty", null));
                return;
            }

            // 2) Fetch bouquet.items in a second query
            Set<Long> bouquetIds = new HashSet<>();
            for (CartItem ci : cart.getItems()) {
                if (ci.getCustomBouquet() != null && ci.getCustomBouquet().getId() != null) {
                    bouquetIds.add(ci.getCustomBouquet().getId());
                }
            }
            if (!bouquetIds.isEmpty()) {
                session.createQuery(
                                "SELECT DISTINCT cb FROM CustomBouquet cb " +
                                        "LEFT JOIN FETCH cb.items cbi " +
                                        "LEFT JOIN FETCH cbi.flower f " +
                                        "WHERE cb.id IN :ids", CustomBouquet.class)
                        .setParameter("ids", bouquetIds)
                        .getResultList();
            }

            // 3) Build managed Order and copy fields
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

            // 4) Convert cart items -> order items, snapshotting
            for (CartItem cartItem : new ArrayList<>(cart.getItems())) {
                OrderItem oi = new OrderItem();
                oi.setOrder(order);
                oi.setQuantity(Math.max(1, cartItem.getQuantity()));

                if (cartItem.getProduct() != null) {
                    Product product = session.get(Product.class, cartItem.getProduct().getId());
                    oi.setProduct(product);
                    if (product != null) {
                        oi.snapshotFromProduct(product);
                    }
                } else if (cartItem.getCustomBouquet() != null) {
                    CustomBouquet copy = cloneBouquetForOrder(cartItem.getCustomBouquet(), managedCustomer);
                    oi.setCustomBouquet(copy);
                    oi.snapshotFromBouquet(copy);
                }
                order.getItems().add(oi);
            }

            // 5) Persist order
            session.persist(order);

            // 6) Clear cart safely
            for (CartItem ci : new ArrayList<>(cart.getItems())) {
                cart.getItems().remove(ci);
                CartItem managedCI = session.contains(ci) ? ci : session.get(CartItem.class, ci.getId());
                if (managedCI != null) session.remove(managedCI);
            }

            tx.commit();
            client.sendToClient(new Message("order_placed_successfully", order, null));

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try {
                client.sendToClient(new Message("order_error", "Failed to place order: " + e.getMessage(), null));
            } catch (IOException ignored) {}
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

    /* ----------------------- Misc helpers ----------------------- */

    public void sendToAllClients(Message message) {
        try {
            for (SubscribedClient subscribedClient : subscribersList) {
                try {
                    subscribedClient.getClient().sendToClient(message);
                } catch (IOException e) {
                    subscribersList.remove(subscribedClient);
                    System.out.println("Removed disconnected client");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}