package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;

import org.hibernate.Session;
import org.hibernate.Transaction;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;


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

        // --- DEBUG: list all mapped entities so we can confirm Complaint is registered ---
        try {
            var sf = HibernateUtil.getSessionFactory();
            System.out.println("=== Hibernate mapped entities ===");
            sf.getMetamodel().getEntities().forEach(e ->
                    System.out.println(" - " + e.getName() + "  (" + e.getJavaType().getName() + ")")
            );
            boolean complaintMapped = sf.getMetamodel().getEntities().stream()
                    .anyMatch(e -> "Complaint".equals(e.getName())
                            || e.getJavaType().getName().endsWith(".Complaint"));
            if (!complaintMapped) {
                System.err.println("WARNING: Complaint entity is NOT mapped. " +
                        "Ensure the server is loading the correct hibernate.cfg.xml " +
                        "and that the entities module with Complaint.class is on the server classpath.");
            }
            System.out.println("=================================");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // -------------------------------------------------------------------------------

        initCaches(); // warm user cache before catalog

        // Initialize catalog snapshot once at startup
        catalogLock.writeLock().lock();
        try {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                catalog.setFlowers(getListFromDB(session, Product.class));
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
                System.out.println("[RX] key=" + key +
                        " obj=" + (m.getObject()==null?"null":m.getObject().getClass().getSimpleName()) +
                        " listSize=" + (m.getObjectList()==null?0:m.getObjectList().size()));


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
                else if ("get_my_complaints".equals(key) || "get_customer_complaints".equals(key)) {
                    handleGetMyComplaints(m, client, session);
                }
                // === Complaint routes ===
                // === Complaint routes ===
                else if ("submit_complaint".equals(key)) {
                    handleSubmitComplaint(m, client, session);
                } else if ("get_complaints_for_branch".equals(key) || "get_all_complaints".equals(key)) {
                    // unified: employees see all unresolved complaints
                    handleGetAllComplaints(m, client, session);
                } else if ("resolve_complaint".equals(key)) {
                    handleResolveComplaint(m, client, session);
                }
                else if ("get_complaints_count".equals(key)) {
                    handleGetComplaintsCount(client, session);
                } else if ("get_complaint_ids".equals(key)) {
                    handleGetComplaintIds(client, session);
                } else if ("ping_echo".equals(key)) {
                    sendMsg(client, new Message("pong", "ok", null), "ping_echo");
                }
                else if ("get_my_complaints".equals(key) || "get_customer_complaints".equals(key)) {
                    handleGetMyComplaints(m, client, session);
                }
                else if ("get_order_complaint_status".equals(key)) {
                    handleGetOrderComplaintStatus(m, client, session);
                }
                else if("logout".equals(key)) {
                    handleLogout(client,session,m);
                }
                else if ("admin_list_users".equals(key)) {
                    handleAdminListUsers(m, client, session);
                } else if ("admin_update_user".equals(key)) {
                    handleAdminUpdateUser(m, client, session);
                } else if ("admin_freeze_customer".equals(key)) {
                    handleAdminFreezeCustomer(m, client, session);
                }
                else {
                    System.out.println("[WARN] Unhandled key: " + key);
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
    /** Put/replace user in cache, and migrate the key if username changed. */
    private void upsertUserCache(User managedUser, String oldUsernameIfKnown) {
        if (managedUser == null) return;
        final String newUsername = managedUser.getUsername();

        // If username changed, move the cache entry and the per-username lock
        if (oldUsernameIfKnown != null && !oldUsernameIfKnown.equals(newUsername)) {
            userCache.remove(oldUsernameIfKnown);
            Object lock = usernameLocks.remove(oldUsernameIfKnown);
            if (lock != null) {
                usernameLocks.putIfAbsent(newUsername, lock);
            }
        }
        userCache.put(newUsername, managedUser);
    }

    /** Resolve a lock object for a username that might be null/unknown. */
    private Object lockForUsername(String username) {
        return usernameLocks.computeIfAbsent(username == null ? "<null>" : username, k -> new Object());
    }


    @SuppressWarnings("unchecked")
    private void handleAdminFreezeCustomer(Message m, ConnectionToClient client, Session session) {
        Map<String,Object> req = (m.getObject() instanceof Map) ? (Map<String,Object>) m.getObject() : null;
        if (req == null) {
            try { client.sendToClient(new Message("admin_freeze_error", "Bad request", null)); }
            catch (IOException ignored) {}
            return;
        }

        Long customerId = null;
        Boolean frozen = null;
        Object idObj = req.get("customerId");
        Object frObj = req.get("frozen");
        if (idObj instanceof Number) customerId = ((Number)idObj).longValue();
        if (frObj instanceof Boolean) frozen = (Boolean) frObj;

        if (customerId == null || frozen == null) {
            try { client.sendToClient(new Message("admin_freeze_error", "Bad request", null)); }
            catch (IOException ignored) {}
            return;
        }

        Transaction tx = session.beginTransaction();
        try {
            Customer c = session.get(Customer.class, customerId);
            if (c == null) throw new IllegalArgumentException("Customer not found");

            final String username = c.getUsername();
            final Object lock = usernameLocks.computeIfAbsent(username, k -> new Object());

            synchronized (lock) {
                // assumes you added a 'frozen' boolean to Customer
                c.setFrozen(frozen);
                Customer managed = (Customer) session.merge(c);
                tx.commit();

                // refresh cache entry
                upsertUserCache(managed, username);

                client.sendToClient(new Message("admin_freeze_ok", toDTO(managed), null));
            }
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("admin_freeze_error", e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }



    private void handleAdminUpdateUser(Message m, ConnectionToClient client, Session session) {
        var dto = (UserAdminDTO) m.getObject();
        if (dto == null || dto.getId() == null || dto.getUserType() == null) {
            try { client.sendToClient(new Message("admin_update_error", "Bad request", null)); }
            catch (IOException ignored) {}
            return;
        }

        Transaction tx = session.beginTransaction();
        try {
            Branch newBranch = findBranchByName(session, dto.getBranchName());

            if ("EMPLOYEE".equalsIgnoreCase(dto.getUserType())) {
                Employee e = session.get(Employee.class, dto.getId());
                if (e == null) throw new IllegalArgumentException("Employee not found");
                final String oldUsername = e.getUsername();

                // Per-username lock around mutation
                final Object lock = usernameLocks.computeIfAbsent(oldUsername, k -> new Object());
                synchronized (lock) {
                    if (dto.getName() != null) e.setName(dto.getName());
                    if (dto.getUsername() != null) e.setUsername(dto.getUsername());
                    if (dto.getPassword() != null) e.setPassword(dto.getPassword());
                    if (dto.getNetworkAccount() != null) e.setNetworkAccount(dto.getNetworkAccount());
                    if (newBranch != null) e.setBranch(newBranch);
                    if (dto.getRole() != null) e.setRole(dto.getRole());

                    Employee managed = (Employee) session.merge(e);
                    tx.commit();

                    // Move/refresh cache and lock if username changed
                    upsertUserCache(managed, oldUsername);

                    client.sendToClient(new Message("admin_update_ok", toDTO(managed), null));
                }
            } else if ("CUSTOMER".equalsIgnoreCase(dto.getUserType())) {
                Customer c = session.get(Customer.class, dto.getId());
                if (c == null) throw new IllegalArgumentException("Customer not found");
                final String oldUsername = c.getUsername();

                final Object lock = usernameLocks.computeIfAbsent(oldUsername, k -> new Object());
                synchronized (lock) {
                    if (dto.getName() != null) c.setName(dto.getName());
                    if (dto.getUsername() != null) c.setUsername(dto.getUsername());
                    if (dto.getPassword() != null) c.setPassword(dto.getPassword());
                    if (dto.getNetworkAccount() != null) c.setNetworkAccount(dto.getNetworkAccount());
                    if (newBranch != null) c.setBranch(newBranch);
                    if (dto.getBudget() != null) c.setBudget(dto.getBudget());
                    // (frozen) handled in admin_freeze_customer

                    Customer managed = (Customer) session.merge(c);
                    tx.commit();

                    upsertUserCache(managed, oldUsername);

                    client.sendToClient(new Message("admin_update_ok", toDTO(managed), null));
                }
            } else {
                throw new IllegalArgumentException("Unknown userType");
            }
            System.out.println("DEBUG: updated user " + dto.getUsername());
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("admin_update_error", e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }



    private UserAdminDTO toDTO(User u) {
        UserAdminDTO dto = new UserAdminDTO();
        dto.setId(u.getId());
        dto.setName(u.getName());
        dto.setUsername(u.getUsername());
        dto.setBranchName(u.getBranch() != null ? u.getBranch().getName() : null);
        dto.setNetworkAccount(u.isNetworkAccount());
        dto.setLoggedIn(u.isLoggedIn());

        if (u instanceof Employee) {
            Employee e = (Employee) u;
            dto.setUserType("EMPLOYEE");
            dto.setRole(e.getRole());
        } else if (u instanceof Customer) {
            Customer c = (Customer) u;
            dto.setUserType("CUSTOMER");
            dto.setBudget(c.getBudget());
            // assumes you added a 'frozen' boolean to Customer as discussed
            try {
                // if field exists
                dto.setFrozen(c.isFrozen());
            } catch (Throwable ignore) {}
        } else {
            dto.setUserType("USER");
        }
        return dto;
    }

    private Branch findBranchByName(Session session, String name) {
        if (name == null || name.isBlank()) return null;
        return session.createQuery("from Branch b where b.name = :n", Branch.class)
                .setParameter("n", name.trim())
                .uniqueResultOptional().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void handleAdminListUsers(Message m, ConnectionToClient client, Session session) {
        try {
            String search = "";
            int offset = 0, limit = 50;

            Object payload = m.getObject();
            if (payload instanceof Map) {
                Map<String,Object> q = (Map<String,Object>) payload;
                if (q.get("search") instanceof String){
                    String s = (String) q.get("search");
                    search = s.trim();
                }
                if (q.get("offset") instanceof Number){
                    Number o = (Number) q.get("offset");
                    offset = Math.max(0, o.intValue());
                }
                if (q.get("limit") instanceof Number){
                    Number l = (Number) q.get("limit");
                    limit = Math.max(1, l.intValue());
                }
            }

            String jpql = "select u from User u left join fetch u.branch"
                    + (search.isBlank() ? "" : " where lower(u.username) like :like");

            var query = session.createQuery(jpql, User.class);
            if (!search.isBlank()) query.setParameter("like", "%" + search.toLowerCase() + "%");

            List<User> users = query.getResultList();

            // Map to DTOs
            List<UserAdminDTO> all = new ArrayList<>(users.size());
            for (User u : users) all.add(toDTO(u));

            // Sort + page
            all.sort(Comparator.comparing(d -> {
                String un = d.getUsername();
                return un == null ? "" : un.toLowerCase();
            }));

            int total = all.size();
            int from  = Math.min(offset, total);
            int to    = Math.min(from + limit, total);

            Map<String,Object> page = new HashMap<>();
            page.put("rows", new ArrayList<>(all.subList(from, to))); // <-- Serializable
            page.put("total", total);

            client.sendToClient(new Message("admin_users_page", page, null));
        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("admin_users_error", e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }





    private void handleLogout(ConnectionToClient client, Session session, Message message) {
        final String username = (message != null && message.getObject() instanceof String)
                ? ((String) message.getObject()).trim()
                : null;

        if (username == null || username.isBlank()) {
            try { client.sendToClient(new Message("logout_error", "Missing username", null)); }
            catch (IOException ignored) {}
            return;
        }

        final Object lock = usernameLocks.computeIfAbsent(username, k -> new Object());

        synchronized (lock) {
            User cached = userCache.get(username);
            if (cached == null) {
                try { client.sendToClient(new Message("logout_error", "User not found in cache", null)); }
                catch (IOException ignored) {}
                return;
            }
            if (!cached.isLoggedIn()) {
                try { client.sendToClient(new Message("logout_success", username, null)); }
                catch (IOException ignored) {}
                return;
            }

            Transaction tx = session.beginTransaction();
            try {
                cached.setLoggedIn(false);
                User managed = (User) session.merge(cached);
                tx.commit();

                // keep cache updated
                upsertUserCache(managed, username);

                try { client.sendToClient(new Message("logout_success", username, null)); }
                catch (IOException ignored) {}
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                e.printStackTrace();
                try { client.sendToClient(new Message("logout_error", "Exception: " + e.getMessage(), null)); }
                catch (IOException ignored) {}
            }
        }
    }


    private void handleGetComplaintsCount(ConnectionToClient client, Session session) {
        try {
            Long cnt = session.createQuery("select count(c) from Complaint c", Long.class).getSingleResult();
            System.out.println("[GetComplaintsCount] count=" + cnt);
            sendMsg(client, new Message("complaints_count", cnt, null), "get_complaints_count");
        } catch (Exception e) {
            e.printStackTrace();
            sendMsg(client, new Message("complaints_count_error", e.getMessage(), null), "get_complaints_count");
        }
    }

    private void handleGetComplaintIds(ConnectionToClient client, Session session) {
        try {
            List<Integer> ids = session.createQuery("select c.id from Complaint c order by c.submittedAt desc", Integer.class)
                    .setMaxResults(20)
                    .getResultList();
            System.out.println("[GetComplaintIds] size=" + ids.size() + " ids=" + ids);
            sendMsg(client, new Message("complaint_ids", ids, null), "get_complaint_ids");
        } catch (Exception e) {
            e.printStackTrace();
            sendMsg(client, new Message("complaint_ids_error", e.getMessage(), null), "get_complaint_ids");
        }
    }




    @SuppressWarnings("unchecked")
    private void handleSubmitComplaint(Message m, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Long customerId = null;
            Long orderId    = null;
            String text     = null;

            if (m.getObjectList() != null && !m.getObjectList().isEmpty()) {
                Object p0 = m.getObjectList().get(0);
                if (p0 instanceof Map) {
                    Map<String,Object> p = (Map<String,Object>) p0;
                    text = Objects.toString(p.get("text"), "").trim();

                    Object cid = p.get("customerId");
                    if (cid instanceof Number) customerId = ((Number) cid).longValue();
                    else if (cid instanceof String && !((String) cid).isBlank()) customerId = Long.valueOf((String) cid);

                    Object oid = p.get("orderId");
                    if (oid instanceof Number) orderId = ((Number) oid).longValue();
                    else if (oid instanceof String && !((String) oid).isBlank()) orderId = Long.valueOf((String) oid);
                } else if (p0 instanceof Complaint) {
                    Complaint incoming = (Complaint) p0;
                    text = Optional.ofNullable(incoming.getText()).orElse("").trim();
                    if (m.getObjectList().size() > 1 && m.getObjectList().get(1) instanceof Customer)
                        customerId = ((Customer) m.getObjectList().get(1)).getId();
                    if (m.getObjectList().size() > 2 && m.getObjectList().get(2) instanceof Order)
                        orderId = ((Order) m.getObjectList().get(2)).getId();
                }
            }

            if (text == null || text.isBlank()) {
                tx.rollback();
                client.sendToClient(new Message("complaint_error", "Complaint text empty", null));
                return;
            }
            if (customerId == null) {
                tx.rollback();
                client.sendToClient(new Message("complaint_error", "Missing customerId", null));
                return;
            }

            Customer customer = session.get(Customer.class, customerId);
            if (customer == null) {
                tx.rollback();
                client.sendToClient(new Message("complaint_error", "Customer not found", null));
                return;
            }
            Order order = (orderId != null) ? session.get(Order.class, orderId) : null;

            // Block duplicate complaint for same (customer, order)
            if (order != null) {
                Long dupCount = session.createQuery(
                                "select count(c) from Complaint c " +
                                        "where c.customer.id = :cid and c.order.id = :oid", Long.class)
                        .setParameter("cid", customer.getId())
                        .setParameter("oid", order.getId())
                        .getSingleResult();
                if (dupCount != null && dupCount > 0) {
                    tx.rollback();
                    client.sendToClient(new Message("complaint_exists_for_order", null, null));
                    return;
                }
            }

            Complaint c = new Complaint();
            c.setCustomer(customer);
            c.setOrder(order);
            c.setText(text.length() > 120 ? text.substring(0, 120) : text);
            c.setSubmittedAt(java.time.LocalDateTime.now());
            c.setDeadline(c.getSubmittedAt().plusHours(24));
            c.setResolved(false);
            c.setBranch(customer.getBranch());

            session.persist(c);
            tx.commit();

            // Ack to submitting customer (client will navigate away)
            client.sendToClient(new Message("complaint_submitted", new ComplaintDTO(c), null));

            // Live-refresh any open UIs (employee/customer lists)
            sendToAllClients(new Message("complaints_refresh", null, null));
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            try { client.sendToClient(new Message("complaint_error", "Exception: " + e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }



    @SuppressWarnings("unchecked")
    private void handleGetMyComplaints(Message m, ConnectionToClient client, Session session) {
        try {
            Long customerId = null;
            boolean unresolvedOnly = false;

            if (m.getObjectList() != null && !m.getObjectList().isEmpty() && m.getObjectList().get(0) instanceof Map) {
                Map<String,Object> p = (Map<String,Object>) m.getObjectList().get(0);
                Object cid = p.get("customerId");
                if (cid instanceof Number) customerId = ((Number) cid).longValue();
                else if (cid instanceof String && !((String) cid).isBlank()) customerId = Long.valueOf((String) cid);

                Object uo = p.get("unresolvedOnly");
                if (uo instanceof Boolean) unresolvedOnly = (Boolean) uo;
            }

            if (customerId == null) {
                client.sendToClient(new Message("complaints_history", List.of(), null));
                return;
            }

            String hql = "select c from Complaint c " +
                    "left join fetch c.customer " +
                    "left join fetch c.order " +
                    "where c.customer.id = :cid " +
                    "order by c.submittedAt desc";
            List<Complaint> list = session.createQuery(hql, Complaint.class)
                    .setParameter("cid", customerId)
                    .getResultList();

            if (unresolvedOnly) list.removeIf(Complaint::isResolved);

            List<ComplaintDTO> dto = list.stream().map(ComplaintDTO::new).collect(java.util.stream.Collectors.toList());
            client.sendToClient(new Message("complaints_history", dto, null));
        } catch (Exception e) {
            try { client.sendToClient(new Message("complaints_history", List.of(), null)); } catch (IOException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void handleGetOrderComplaintStatus(Message m, ConnectionToClient client, Session session) {
        try {
            Long customerId = null, orderId = null;
            if (m.getObjectList()!=null && !m.getObjectList().isEmpty() && m.getObjectList().get(0) instanceof Map) {
                Map<String,Object> p = (Map<String,Object>) m.getObjectList().get(0);
                Object cid = p.get("customerId");
                Object oid = p.get("orderId");
                if (cid instanceof Number) customerId = ((Number) cid).longValue();
                if (oid instanceof Number) orderId = ((Number) oid).longValue();
            }
            if (customerId == null || orderId == null) {
                client.sendToClient(new Message("order_complaint_status", null, null));
                return;
            }

            Complaint c = session.createQuery(
                            "select c from Complaint c " +
                                    "left join fetch c.order " +
                                    "where c.customer.id = :cid and c.order.id = :oid",
                            Complaint.class)
                    .setParameter("cid", customerId)
                    .setParameter("oid", orderId)
                    .setMaxResults(1)
                    .uniqueResultOptional()
                    .orElse(null);

            client.sendToClient(new Message("order_complaint_status",
                    c == null ? null : new ComplaintDTO(c), null));
        } catch (Exception e) {
            try { client.sendToClient(new Message("order_complaint_status", null, null)); }
            catch (IOException ignored) {}
        }
    }



    private void handleGetAllComplaints(Message m, ConnectionToClient client, Session session) {
        try {
            String hql = "select c from Complaint c " +
                    "left join fetch c.customer " +
                    "left join fetch c.order " +
                    "order by c.submittedAt desc";
            var list = session.createQuery(hql, Complaint.class).getResultList();

            // if the client sent {"resolved": true}, include resolved; otherwise keep unresolved only
            boolean includeResolved = false;
            if (m.getObjectList() != null && !m.getObjectList().isEmpty() && m.getObjectList().get(0) instanceof Map) {
                Object flag = ((Map<?,?>) m.getObjectList().get(0)).get("resolved");
                if (flag instanceof Boolean) includeResolved = (Boolean) flag;
            }
            if (!includeResolved) list.removeIf(Complaint::isResolved);

            List<ComplaintDTO> dtoList = list.stream().map(ComplaintDTO::new).collect(Collectors.toList());
            System.out.println("[Complaints] sending DTO list size=" + dtoList.size());
            client.sendToClient(new Message("complaints_list", dtoList, null));
        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("complaints_error", "Failed to fetch complaints", null)); }
            catch (IOException ignored) {}
        }
    }





    private void sendMsg(ConnectionToClient client, Message msg, String context) {
        try {
            System.out.println("[SEND][" + context + "] -> " + msg.getMessage() +
                    " (obj=" + (msg.getObject()==null?"null":msg.getObject().getClass().getSimpleName()) +
                    ", listSize=" + (msg.getObjectList()==null?0:msg.getObjectList().size()) + ")");
            client.sendToClient(msg);
        } catch (IOException ex) {
            System.err.println("[SEND-ERROR][" + context + "] to=" + client + " msg=" + msg.getMessage());
            ex.printStackTrace();
        }
    }


    /** Mark a complaint as resolved, set responder/response/compensation, and notify clients. */
    private void handleResolveComplaint(Message m, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Long complaintId = null;
            Employee responder = null;
            String responseText = null;
            java.math.BigDecimal compensation = null;

            // Accept either: Map payload OR positional list
            if (m.getObjectList() != null && !m.getObjectList().isEmpty()) {
                Object first = m.getObjectList().get(0);
                if (first instanceof Map) {
                    Map<?,?> p = (Map<?,?>) first;
                    Object idObj = p.get("complaintId");
                    if (idObj instanceof Number) complaintId = ((Number) idObj).longValue();
                    else if (idObj instanceof String && !((String) idObj).isBlank()) complaintId = Long.valueOf((String) idObj);

                    Object txt = p.get("responseText");
                    if (txt instanceof String) responseText = ((String) txt).trim();

                    Object comp = p.get("compensation");
                    if (comp instanceof java.math.BigDecimal) compensation = (java.math.BigDecimal) comp;
                    else if (comp instanceof Number) compensation = java.math.BigDecimal.valueOf(((Number) comp).doubleValue());
                } else {
                    // old positional style
                    if (m.getObjectList().size() > 0) {
                        Object idObj = m.getObjectList().get(0);
                        if (idObj instanceof Number) complaintId = ((Number) idObj).longValue();
                        else if (idObj instanceof Complaint && ((Complaint) idObj).getId() != 0)
                            complaintId = (long) ((Complaint) idObj).getId();
                    }
                    if (m.getObjectList().size() > 1 && m.getObjectList().get(1) instanceof Employee) {
                        responder = session.get(Employee.class, ((Employee) m.getObjectList().get(1)).getId());
                    }
                    if (m.getObjectList().size() > 2 && m.getObjectList().get(2) instanceof String) {
                        responseText = ((String) m.getObjectList().get(2)).trim();
                    }
                    if (m.getObjectList().size() > 3) {
                        Object comp = m.getObjectList().get(3);
                        if (comp instanceof java.math.BigDecimal) compensation = (java.math.BigDecimal) comp;
                        else if (comp instanceof Number) compensation = java.math.BigDecimal.valueOf(((Number) comp).doubleValue());
                    }
                }
            }

            if (complaintId == null) {
                tx.rollback();
                client.sendToClient(new Message("complaint_resolve_error", "Missing complaint id", null));
                return;
            }

            Complaint c = session.get(Complaint.class, complaintId);
            if (c == null) {
                tx.rollback();
                client.sendToClient(new Message("complaint_resolve_error", "Complaint not found", null));
                return;
            }

            c.setResolved(true);
            c.setResolvedAt(java.time.LocalDateTime.now());
            if (responseText != null) c.setResponseText(responseText);
            if (compensation != null) c.setCompensationAmount(compensation);
            if (responder != null) c.setResponder(responder);

            session.merge(c);
            tx.commit();

            // Acknowledge to the resolving employee
            client.sendToClient(new Message("complaint_resolved", new ComplaintDTO(c), null));

            // Nudge all open UIs (employee list hides it unless "show resolved"; customer screen will show response)
            sendToAllClients(new Message("complaints_refresh", null, null));
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("complaint_resolve_error", "Exception: " + e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }




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

            // Lock using current (old) username; capture it in case it changes
            final String oldUsername = user.getUsername();
            final Object lock = usernameLocks.computeIfAbsent(oldUsername, k -> new Object());

            User mergedUser;
            synchronized (lock) {
                mergedUser = (User) session.merge(user);
                if (customer != null) session.merge(customer);
                tx.commit();

                // (Possibly) move cache key if username changed
                upsertUserCache(mergedUser, oldUsername);
            }

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
                User incoming = (User) msg.getObject();
                session.save(incoming);
                // Obtain managed fresh state
                User managed = session.get(User.class, incoming.getId());
                tx.commit();

                // cache + lock ready for this username
                upsertUserCache(managed, null);

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

    private void handleUserAuthentication(Message msg, ConnectionToClient client, Session session) {
        try {
            if (msg == null || !(msg.getObject() instanceof List)) {
                try { client.sendToClient(new Message("authentication_error", null, null)); } catch (IOException ignored) {}
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> info = (List<String>) msg.getObject();
            if (info.size() < 2) {
                try { client.sendToClient(new Message("authentication_error", null, null)); } catch (IOException ignored) {}
                return;
            }

            final String username = info.get(0);
            final String password = info.get(1);
            final Object lock = usernameLocks.computeIfAbsent(username, k -> new Object());

            synchronized (lock) {
                User cached = userCache.get(username);
                if (cached == null) {
                    System.out.println("user not found");
                    try { client.sendToClient(new Message("incorrect", null, null)); } catch (IOException ignored) {}
                    return;
                }
                if (!Objects.equals(cached.getPassword(), password)) {
                    System.out.println("Incorrect password");
                    try { client.sendToClient(new Message("incorrect", null, null)); } catch (IOException ignored) {}
                    return;
                }
                if (cached.isLoggedIn()) {
                    try { client.sendToClient(new Message("already_logged", null, null)); } catch (IOException ignored) {}
                    return;
                }

                Transaction tx = session.beginTransaction();
                try {
                    cached.setLoggedIn(true);
                    User managed = (User) session.merge(cached);
                    tx.commit();

                    // keep cache consistent with DB state
                    upsertUserCache(managed, username);

                    System.out.println(cached.getUsername());
                    System.out.println(cached.getPassword());
                    try { client.sendToClient(new Message("correct", managed, null)); } catch (IOException ignored) {}
                } catch (Exception e) {
                    if (tx.isActive()) tx.rollback();
                    e.printStackTrace();
                    try { client.sendToClient(new Message("authentication_error", null, null)); } catch (IOException ignored) {}
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