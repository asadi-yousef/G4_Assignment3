package il.cshaifasweng.OCSFMediatorExample.server;
import il.cshaifasweng.OCSFMediatorExample.entities.InboxItemDTO;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.util.Map;
import java.time.LocalDate;

import java.util.stream.Stream;


public class SimpleServer extends AbstractServer {

    // ---- Subscribers (thread-safe) ----
    private static final CopyOnWriteArrayList<SubscribedClient> subscribersList = new CopyOnWriteArrayList<>();

    // ---- In-memory catalog snapshot + lock ----
    private static volatile Catalog catalog = new Catalog(new ArrayList<>());
    private static final ReentrantReadWriteLock catalogLock = new ReentrantReadWriteLock();

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


    /* ----------------------- Request dispatch ----------------------- */

    @Override
    protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
        if (msg == null) return;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (msg instanceof Message) {
                Message m = (Message) msg;
                String key = m.getMessage();
                System.out.println("[RX] key=" + key +
                        " obj=" + (m.getObject()==null ? "null" : m.getObject().getClass().getSimpleName()) +
                        " listSize=" + (m.getObjectList()==null ? 0 : m.getObjectList().size()));

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
                } else if ("get_my_complaints".equals(key) || "get_customer_complaints".equals(key)) {
                    handleGetMyComplaints(m, client, session);
                } else if ("cancel_order".equals(key)) {
                    handleCancelOrder(m, client, session);
                } else if ("submit_complaint".equals(key)) {
                    handleSubmitComplaint(m, client, session);
                } else if ("get_complaints_for_branch".equals(key) || "get_all_complaints".equals(key)) {
                    handleGetAllComplaints(m, client, session);
                } else if ("resolve_complaint".equals(key)) {
                    handleResolveComplaint(m, client, session);
                } else if ("get_complaints_count".equals(key)) {
                    handleGetComplaintsCount(client, session);
                } else if ("get_complaint_ids".equals(key)) {
                    handleGetComplaintIds(client, session);
                } else if ("ping_echo".equals(key)) {
                    sendMsg(client, new Message("pong", "ok", null), "ping_echo");
                } else if ("get_order_complaint_status".equals(key)) {
                    handleGetOrderComplaintStatus(m, client, session);
                } else if("update_budget_add".equals(key)) {
                    handleBudgetAdd(m, client, session);
                } else if("update_budget_subtract".equals(key)) {
                    handleBudgetSubtract(m, client, session);
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
                else if ("get_inbox".equals(key)) {
                    handleGetInbox(m, client, session);
                } else if ("mark_notification_read".equals(key)) {
                    handleMarkNotificationRead(m, client, session);
                } else if ("mark_notification_unread".equals(key)) {
                    handleMarkNotificationUnread(m, client, session);
                } else if ("create_broadcast".equals(key)) {
                }
                else if ("mark_notification_unread".equals(key)) {
                    handleMarkNotificationUnread(m, client, session);
                }
                else if ("create_broadcast".equals(key)) {
                    handleCreateBroadcast(m, client, session);
                }
                else if ("request_report_income".equals(key)) {
                    handleReportIncome(m, client, session);
                } else if ("request_report_orders".equals(key)) {
                    handleReportOrders(m, client, session);
                } else if ("request_report_complaints".equals(key)) {
                    handleReportComplaints(m, client, session);
                }else if("admin_delete_user".equals(key)) {
                    handleAdminDeleteUser(m, client, session);
                } else if ("request_orders".equals(key)) {
                    handleOrdersRequest(m, client, session);

                    // ---------- EMPLOYEE SCHEDULE (accept multiple aliases) ----------
                } else if ("request_orders_by_day".equals(key)
                        || "request_day".equals(key)
                        || "request_schedule".equals(key)) {
                    handleRequestOrdersByDay(m, client, session);

                    // --------------------------------------------------------------
                } else {
                    System.out.println("[RX] UNKNOWN key=" + key + " obj=" + m.getObject() + " listSize=" +
                            (m.getObjectList()==null ? 0 : m.getObjectList().size()));
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
    @SuppressWarnings("unchecked")
    private void handleAdminDeleteUser(Message m, ConnectionToClient client, Session session) {
        Map<String,Object> req = (m.getObject() instanceof Map) ? (Map<String,Object>) m.getObject() : null;
        Long userId = (req == null || !(req.get("userId") instanceof Number)) ? null
                : ((Number) req.get("userId")).longValue();

        if (userId == null) {
            try { client.sendToClient(new Message("admin_delete_error", "Missing userId", null)); }
            catch (IOException ignored) {}
            return;
        }

        Transaction tx = session.beginTransaction();
        try {
            // Load once (handles inheritance via DTYPE)
            User u = session.get(User.class, userId);
            if (u == null) throw new IllegalArgumentException("User not found: " + userId);

            // --- Employee-specific FK cleanup (safe no-op for customers) ---
            // If Branch has a manager FK to Employee, break it before deleting the employee.
            //session.createQuery("update Branch b set b.manager = null where b.manager.id = :uid")
             //       .setParameter("uid", userId)
                  //  .executeUpdate();

            // --- Type-specific child graph cleanup (bulk HQL only) ---
            if (u instanceof Customer) {
                Customer c = (Customer) u;
                // Uses the fixed version that deletes by parent IDs (orders->items, carts->items, bouquets->items, etc.)
                deleteCustomerGraph(session, c);
            } else if (u instanceof Employee) {
                Employee e = (Employee) u;
                deleteEmployeeGraph(session, e);
            }

            // --- Delete the parent row via BULK HQL (avoid OptimisticLockException) ---
            int deleted = session.createQuery("delete from User u where u.id = :uid")
                    .setParameter("uid", userId)
                    .executeUpdate();
            if (deleted == 0) throw new IllegalStateException("User already deleted: " + userId);

            tx.commit();

            // After bulk DML, clear 1st-level cache to avoid stale state
            session.clear();

            // --- Notify and force-logout the exact connected client (if online), then clear connection info ---
            for (SubscribedClient sc : subscribersList) {
                ConnectionToClient s = sc.getClient();
                Object uid = s.getInfo("userId"); // set at login
                if (uid instanceof Long && java.util.Objects.equals(uid, userId)) {
                    try {
                        s.sendToClient(new Message("account_deleted", "Your account was deleted", null));
                        // Clear any per-connection user info
                        s.setInfo("userId", null);
                        s.setInfo("username", null);
                        s.setInfo("userType", null);

                        // Optional: s.close(); // only if your client UX supports reconnect cleanly
                    } catch (Exception ignored) {}
                }
            }

            // --- Reply to the admin UI ---
            try { client.sendToClient(new Message("admin_delete_ok", userId, null)); }
            catch (IOException ignored) {}

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("admin_delete_error", e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }

    private void deleteEmployeeGraph(Session session, Employee e) {
        Long eid = e.getId();

        // 1) Break the FK from complaints -> employee (keep responderName intact)
        session.createQuery("update Complaint c set c.responder = null where c.responder.id = :eid")
                .setParameter("eid", eid)
                .executeUpdate();

    }



    /**
     * Deletes all data that depends on a Customer in a FK-safe order.
     */
    private void deleteCustomerGraph(Session session, Customer c) {
        Long cid = c.getId();

        // ---- ORDERS (items first, then orders)
        List<Long> orderIds = session.createQuery(
                        "select o.id from Order o where o.customer.id = :cid", Long.class)
                .setParameter("cid", cid)
                .getResultList();

        if (!orderIds.isEmpty()) {
            session.createQuery("delete from OrderItem oi where oi.order.id in (:ids)")
                    .setParameterList("ids", orderIds)
                    .executeUpdate();
            session.createQuery("delete from Order o where o.id in (:ids)")
                    .setParameterList("ids", orderIds)
                    .executeUpdate();
        }

        // ---- CART (items first, then carts)
        List<Long> cartIds = session.createQuery(
                        "select crt.id from Cart crt where crt.customer.id = :cid", Long.class)
                .setParameter("cid", cid)
                .getResultList();

        if (!cartIds.isEmpty()) {
            session.createQuery("delete from CartItem ci where ci.cart.id in (:ids)")
                    .setParameterList("ids", cartIds)
                    .executeUpdate();
            session.createQuery("delete from Cart crt where crt.id in (:ids)")
                    .setParameterList("ids", cartIds)
                    .executeUpdate();
        }

        // ---- CUSTOM BOUQUETS (items first, then bouquets)
        List<Long> bouquetIds = session.createQuery(
                        "select cb.id from CustomBouquet cb where cb.createdBy.id = :cid", Long.class)
                .setParameter("cid", cid)
                .getResultList();

        if (!bouquetIds.isEmpty()) {
            // Primary: field is likely "bouquet" on CustomBouquetItem
            int removed = session.createQuery(
                            "delete from CustomBouquetItem cbi where cbi.bouquet.id in (:ids)")
                    .setParameterList("ids", bouquetIds)
                    .executeUpdate();

            // Fallback if your field is named "customBouquet" instead of "bouquet"
            if (removed == 0) {
                session.createQuery(
                                "delete from CustomBouquetItem cbi where cbi.bouquet.id in (:ids)")
                        .setParameterList("ids", bouquetIds)
                        .executeUpdate();
            }

            session.createQuery("delete from CustomBouquet cb where cb.id in (:ids)")
                    .setParameterList("ids", bouquetIds)
                    .executeUpdate();
        }

        // ---- SIMPLE 1:1 / 1:N leaves
        session.createQuery("delete from Budget b where b.customer.id = :cid")
                .setParameter("cid", cid)
                .executeUpdate();

        session.createQuery("delete from CreditCard cc where cc.customer.id = :cid")
                .setParameter("cid", cid)
                .executeUpdate();

        session.createQuery("delete from Subscription s where s.customer.id = :cid")
                .setParameter("cid", cid)
                .executeUpdate();

        // ---- Complaints created by this customer (if FK exists)
        session.createQuery("delete from Complaint cp where cp.customer.id = :cid")
                .setParameter("cid", cid)
                .executeUpdate();
    }


    /** Resolve a product image path (server DB value) to a File on the server disk. */
    private static File resolveServerImageFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;
        try {
            String sp = storedPath.trim();

            // Already server-relative "images/..." → live under IMAGES_DIR
            if (sp.startsWith("images/") || sp.startsWith("images\\")) {
                String fileName = java.nio.file.Paths.get(sp.replace('\\','/')).getFileName().toString();
                return new File(IMAGES_DIR, fileName);
            }
            // file: URI (common in your legacy rows)
            if (sp.startsWith("file:")) {
                try {
                    return new File(java.net.URI.create(sp));
                } catch (Exception ignored) { /* fall through */ }
            }
            // Absolute or relative plain path
            File f = new File(sp);
            if (!f.isAbsolute()) f = new File(".", sp);
            return f;
        } catch (Exception ex) {
            return null;
        }
    }


    @SuppressWarnings("unchecked")
    private void handleAdminFreezeCustomer(Message m, ConnectionToClient client, Session session) {
        Map<String,Object> req = (m.getObject() instanceof Map) ? (Map<String,Object>) m.getObject() : null;
        if (req == null) {
            try { client.sendToClient(new Message("admin_freeze_error", "Bad request", null)); } catch (IOException ignored) {}
            return;
        }

        Long customerId = null;
        Boolean frozen = null;
        Object idObj = req.get("customerId");
        Object frObj = req.get("frozen");
        if (idObj instanceof Number) customerId = ((Number) idObj).longValue();
        if (frObj instanceof Boolean) frozen = (Boolean) frObj;

        if (customerId == null || frozen == null) {
            try { client.sendToClient(new Message("admin_freeze_error", "Bad request", null)); } catch (IOException ignored) {}
            return;
        }

        Transaction tx = session.beginTransaction();
        try {
            // Single atomic DB write; no need to merge a managed entity
            int updated = session.createQuery(
                            "update Customer c set c.frozen = :frozen, c.isLoggedIn = false where c.id = :id")
                    .setParameter("frozen", frozen)
                    .setParameter("id", customerId)
                    .executeUpdate(); // returns number of rows changed
            tx.commit();

            if (updated == 0) {
                try { client.sendToClient(new Message("admin_freeze_error", "Customer not found", null)); } catch (IOException ignored) {}
                return;
            }

            // Bulk JPQL bypasses the first-level cache—clear and reload a fresh view before sending
            session.clear();
            Customer fresh = session.find(Customer.class, customerId);

            try { client.sendToClient(new Message("admin_freeze_ok", toDTO(fresh), null)); } catch (IOException ignored) {}

            if (Boolean.TRUE.equals(frozen)) {
                // Notify/force-logout the exact connected client for this user (if connected)
                for (SubscribedClient sc : subscribersList) {
                    ConnectionToClient s = sc.getClient();
                    Object uid = s.getInfo("userId"); // set at login
                    if (uid instanceof Long && java.util.Objects.equals(uid, customerId)) {
                        try {
                            s.sendToClient(new Message("account_banned", "Your account was banned", null));
                            // Optionally: also close the connection if your client can reconnect cleanly
                            // s.close(); // only if supported/desired by your OCSF layer
                        } catch (Exception ignored) {}
                    }
                }
            }

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("admin_freeze_error", e.getMessage(), null)); } catch (IOException ignored) {}
        }
    }


    @SuppressWarnings("unchecked")
    private void handleAdminUpdateUser(Message m, ConnectionToClient client, Session session) {
        UserAdminDTO dto = (UserAdminDTO) m.getObject();
        if (dto == null || dto.getId() == null || dto.getUserType() == null) {
            try { client.sendToClient(new Message("admin_update_error", "Bad request", null)); }
            catch (IOException ignored) {}
            return;
        }

        Transaction tx = session.beginTransaction();
        try {
            // Resolve branch (by name)
            Branch newBranch = null;
            if (dto.getBranchName() != null && !dto.getBranchName().isBlank()) {
                newBranch = findBranchByName(session, dto.getBranchName().trim());
                if (newBranch == null) {
                    tx.rollback();
                    try { client.sendToClient(new Message("admin_update_error", "Branch not found: " + dto.getBranchName(), null)); }
                    catch (IOException ignored) {}
                    return;
                }
            }

            if ("EMPLOYEE".equalsIgnoreCase(dto.getUserType())) {
                Employee e = session.get(Employee.class, dto.getId());
                if (e == null) {
                    tx.rollback();
                    try { client.sendToClient(new Message("admin_update_error", "Employee not found", null)); }
                    catch (IOException ignored) {}
                    return;
                }

                // Hard server-side gate: block any edit while logged in
                if (e.isLoggedIn()) {
                    tx.rollback();
                    try {
                        client.sendToClient(new Message(
                                "admin_update_error",
                                "User is logged in — only Freeze/Unfreeze is allowed.",
                                null
                        ));
                    } catch (IOException ignored) {}
                    return;
                }

                // Decide target values (incoming if provided, otherwise current).
                final String targetUsername = dto.getUsername() == null ? e.getUsername() : dto.getUsername().trim();

                // --- Uniqueness preflight (username)
                if (dto.getUsername() != null                  // only if actually edited
                        && !equalsIgnoreCaseSafe(targetUsername, e.getUsername())
                        && usernameExists(session, targetUsername, e.getId())) {
                    tx.rollback();
                    try { client.sendToClient(new Message("admin_update_error", "Username already in use: " + targetUsername, null)); }
                    catch (IOException ignored) {}
                    return;
                }

                // --- Uniqueness preflight (ID number) -> ONLY if dto supplied a new one AND it differs
                if (dto.getIdNumber() != null) {
                    final String candidateId = dto.getIdNumber().trim();
                    if (!Objects.equals(candidateId, e.getIdNumber())
                            && idNumberExists(session, candidateId, e.getId())) {
                        tx.rollback();
                        try { client.sendToClient(new Message("admin_update_error", "ID number already in use: " + candidateId, null)); }
                        catch (IOException ignored) {}
                        return;
                    }
                }

                final String oldUsername = e.getUsername();
                    // Basic fields (nulls = don't change)
                    if (dto.getFirstName() != null) e.setFirstName(dto.getFirstName());
                    if (dto.getLastName()  != null) e.setLastName(dto.getLastName()); // <-- fixed
                    if (dto.getUsername()  != null) e.setUsername(targetUsername);
                    if (dto.getPassword()  != null) e.setPassword(dto.getPassword());
                    if (dto.getIdNumber()  != null) e.setIdNumber(dto.getIdNumber().trim());

                    // Role + invariants
                    if (dto.getRole() != null) {
                        String newRole = dto.getRole().toLowerCase();
                        e.setRole(newRole);

                        boolean toNet = isNetRole(newRole);
                        if (toNet) {
                            e.setNetworkAccount(true);
                            e.setBranch(null);
                        } else {
                            if (newBranch == null && e.getBranch() == null) {
                                tx.rollback();
                                try { client.sendToClient(new Message("admin_update_error", "Branch role requires a branch.", null)); }
                                catch (IOException ignored) {}
                                return;
                            }
                            if (newBranch != null) e.setBranch(newBranch);
                            e.setNetworkAccount(false);
                        }
                    } else {
                        // No role change; still allow branch or network changes with invariants
                        if (newBranch != null) {
                            if (e.isNetworkAccount()) {
                                tx.rollback();
                                try { client.sendToClient(new Message("admin_update_error", "Network employees cannot have a branch.", null)); }
                                catch (IOException ignored) {}
                                return;
                            }
                            e.setBranch(newBranch);
                        }
                        if (dto.getNetworkAccount() != null) {
                            boolean na = dto.getNetworkAccount();
                            e.setNetworkAccount(na);
                            if (na) {
                                e.setBranch(null);
                            } else {
                                if (newBranch == null && e.getBranch() == null) {
                                    tx.rollback();
                                    try { client.sendToClient(new Message("admin_update_error", "Non-network employees must have a branch.", null)); }
                                    catch (IOException ignored) {}
                                    return;
                                }
                                if (newBranch != null) e.setBranch(newBranch);
                            }
                        }
                    }

                    Employee managed = (Employee) session.merge(e);
                    tx.commit();
                    client.sendToClient(new Message("admin_update_ok", toDTO(managed), null));

            } else if ("CUSTOMER".equalsIgnoreCase(dto.getUserType())) {
                Customer c = session.get(Customer.class, dto.getId());
                if (c == null) {
                    tx.rollback();
                    try { client.sendToClient(new Message("admin_update_error", "Customer not found", null)); }
                    catch (IOException ignored) {}
                    return;
                }

                if (c.isLoggedIn()) {
                    tx.rollback();
                    try {
                        client.sendToClient(new Message(
                                "admin_update_error",
                                "User is logged in — only Freeze/Unfreeze is allowed.",
                                null
                        ));
                    } catch (IOException ignored) {}
                    return;
                }

                final String targetUsername = dto.getUsername() == null ? c.getUsername() : dto.getUsername().trim();

                // Username uniqueness only if actually edited
                if (dto.getUsername() != null
                        && !equalsIgnoreCaseSafe(targetUsername, c.getUsername())
                        && usernameExists(session, targetUsername, c.getId())) {
                    tx.rollback();
                    try { client.sendToClient(new Message("admin_update_error", "Username already in use: " + targetUsername, null)); }
                    catch (IOException ignored) {}
                    return;
                }

                // ID number uniqueness only if actually edited
                if (dto.getIdNumber() != null) {
                    final String candidateId = dto.getIdNumber().trim();
                    if (!Objects.equals(candidateId, c.getIdNumber())
                            && idNumberExists(session, candidateId, c.getId())) {
                        tx.rollback();
                        try { client.sendToClient(new Message("admin_update_error", "ID number already in use: " + candidateId, null)); }
                        catch (IOException ignored) {}
                        return;
                    }
                }

                final String oldUsername = c.getUsername();
                    // Basic fields
                    if (dto.getFirstName() != null) c.setFirstName(dto.getFirstName());
                    if (dto.getLastName()  != null) c.setLastName(dto.getLastName()); // <-- fixed
                    if (dto.getUsername()  != null) c.setUsername(targetUsername);
                    if (dto.getPassword()  != null) c.setPassword(dto.getPassword());
                    if (dto.getIdNumber()  != null) c.setIdNumber(dto.getIdNumber().trim());

                    // Customer-only fields
                    if (dto.getBudget() != null && dto.getBudget().signum() >= 0) c.getBudget().setBalance(dto.getBudget().doubleValue());
                    if (dto.getFrozen() != null) c.setFrozen(dto.getFrozen());

                    // Network/branch invariants
                    if (dto.getNetworkAccount() != null) {
                        boolean na = dto.getNetworkAccount();
                        c.setNetworkAccount(na);
                        if (na) {
                            c.setBranch(null);
                        } else {
                            if (newBranch == null && c.getBranch() == null) {
                                tx.rollback();
                                try { client.sendToClient(new Message("admin_update_error", "Non-network customers must have a branch.", null)); }
                                catch (IOException ignored) {}
                                return;
                            }
                            if (newBranch != null) c.setBranch(newBranch);
                        }
                    } else if (newBranch != null) {
                        if (c.isNetworkAccount()) {
                            tx.rollback();
                            try { client.sendToClient(new Message("admin_update_error", "Network customers cannot have a branch.", null)); }
                            catch (IOException ignored) {}
                            return;
                        }
                        c.setBranch(newBranch);
                    }

                    Customer managed = (Customer) session.merge(c);
                    tx.commit();

                    try { client.sendToClient(new Message("admin_update_ok", toDTO(managed), null)); }
                    catch (IOException ignored) {}
            } else {
                tx.rollback();
                try { client.sendToClient(new Message("admin_update_error", "Unknown userType: " + dto.getUserType(), null)); }
                catch (IOException ignored) {}
            }

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace(); // keep for server logs (optional)
            try { client.sendToClient(new Message("admin_update_error", e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }


    private void clientSendSafe(ConnectionToClient client, Message out) {
        try { client.sendToClient(out); } catch (IOException ignored) {}
    }

    private boolean equalsIgnoreCaseSafe(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
    /** Use case-insensitive username uniqueness with cache fast path + optional DB fallback. */
    private boolean usernameExists(Session session, String candidate, Long excludeId) {
        if (candidate == null || candidate.isBlank()) return false;

        final String keyLower = candidate.trim().toLowerCase();

        Long count = session.createQuery(
                        "select count(u.id) from User u where lower(u.username) = :un and (:id is null or u.id <> :id)",
                        Long.class)
                .setParameter("un", keyLower)
                .setParameter("id", excludeId)
                .getSingleResult();

        return count != null && count > 0;
    }


    /** ID-number uniqueness (DB). If you add an idNumber index to cache, mirror the same pattern. */
    private boolean idNumberExists(Session session, String candidate, Long excludeId) {
        if (candidate == null || candidate.isBlank()) return false;
        final String keyLower = candidate.trim().toLowerCase();
        Long cnt = session.createQuery(
                        "select count(u.id) from User u where lower(u.idNumber) = :in and u.id <> :id", Long.class)
                .setParameter("in", keyLower)
                .setParameter("id", (excludeId == null ? -1L : excludeId))
                .getSingleResult();
        return cnt != null && cnt > 0L;
    }


    private UserAdminDTO toDTO(User u) {
        UserAdminDTO dto = new UserAdminDTO();

        dto.setId(u.getId());

        // userType without pattern matching (Java 15 safe)
        String userType;
        if (u instanceof Employee) {
            userType = "EMPLOYEE";
        } else if (u instanceof Customer) {
            userType = "CUSTOMER";
        } else {
            userType = "USER";
        }
        dto.setUserType(userType);

        // new identity fields
        dto.setIdNumber(u.getIdNumber());
        dto.setFirstName(u.getFirstName());
        dto.setLastName(u.getLastName());
        dto.setUsername(u.getUsername());

        // branch info
        if (u.getBranch() != null) {
            dto.setBranchId(u.getBranch().getId());
            dto.setBranchName(u.getBranch().getName());
        } else {
            dto.setBranchId(null);
            dto.setBranchName(null);
        }

        dto.setNetworkAccount(u.isNetworkAccount());
        dto.setLoggedIn(u.isLoggedIn());

        if (u instanceof Employee) {
            Employee e = (Employee) u;     // classic cast
            dto.setRole(e.getRole());
        } else if (u instanceof Customer) {
            Customer c = (Customer) u;     // classic cast
            if (c.getBudget() != null) {
                dto.setBudget(java.math.BigDecimal.valueOf(c.getBudget().getBalance()));
            }
            try {
                dto.setFrozen(c.isFrozen());
            } catch (Throwable ignore) {}
        }
        return dto;
    }



    private Branch findBranchByName(Session session, String name) {
        if (name == null || name.isBlank()) return null;
        return session.createQuery("from Branch b where lower(b.name) = :n", Branch.class)
                .setParameter("n", name.toLowerCase())
                .setMaxResults(1)
                .uniqueResult();
    }

    private static boolean isNetRole(String role) {
        return role != null && role.toLowerCase().startsWith("net") || role.equalsIgnoreCase("systemadmin") || role.equalsIgnoreCase("customerservice");
    }


    @SuppressWarnings("unchecked")
    private void handleAdminListUsers(Message m, ConnectionToClient client, Session session) {
        try {
            String search = "";
            int offset = 0, limit = 50;

            Object payload = m.getObject();
            if (payload instanceof Map) {
                Map<String,Object> q = (Map<String,Object>) payload;
                if (q.get("search") instanceof String) search = ((String) q.get("search")).trim();
                if (q.get("offset") instanceof Number) offset = Math.max(0, ((Number) q.get("offset")).intValue());
                if (q.get("limit")  instanceof Number)  limit  = Math.max(1, ((Number) q.get("limit")).intValue());
            }

            var cb = session.getCriteriaBuilder();

            // ---------- DATA query ----------
            var cq = cb.createQuery(User.class);
            var root = cq.from(User.class);

            // LEFT JOIN FETCH branch for display
            root.fetch("branch", jakarta.persistence.criteria.JoinType.LEFT);

            if (!search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                cq.where(cb.or(
                        cb.like(cb.lower(root.get("username")),  like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")),  like),
                        cb.like(cb.lower(root.get("idNumber")),  like)
                ));
            }

            // Stable, case-insensitive ordering for pagination
            cq.orderBy(
                    cb.asc(cb.lower(root.get("username"))),
                    cb.asc(root.get("id"))
            );
            cq.distinct(true);

            var dataQ = session.createQuery(cq)
                    .setFirstResult(offset)
                    .setMaxResults(limit);

            var pageUsers = dataQ.getResultList();

            // ---------- COUNT query ----------
            var ccq = cb.createQuery(Long.class);
            var croot = ccq.from(User.class);
            if (!search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                ccq.where(cb.or(
                        cb.like(cb.lower(croot.get("username")),  like),
                        cb.like(cb.lower(croot.get("firstName")), like),
                        cb.like(cb.lower(croot.get("lastName")),  like),
                        cb.like(cb.lower(croot.get("idNumber")),  like)
                ));
            }
            ccq.select(cb.count(croot));
            long total = session.createQuery(ccq).getSingleResult();

            // Map to DTOs
            List<UserAdminDTO> rows = new ArrayList<>(pageUsers.size());
            for (User u : pageUsers) rows.add(toDTO(u));

            Map<String,Object> page = new HashMap<>();
            page.put("rows", rows);
            page.put("total", total);

            client.sendToClient(new Message("admin_users_page", page, null));
        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("admin_users_error", e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }

    private void handleLogout(ConnectionToClient client, Session session, Message message) {
        try {
            final String username = (message != null && message.getObject() instanceof String)
                    ? ((String) message.getObject()).trim()
                    : null;

            if (username == null || username.isBlank()) {
                try { client.sendToClient(new Message("logout_error", "Missing username", null)); } catch (IOException ignored) {}
                return;
            }

            User user;
            try {
                user = session.createQuery(
                                "select u from User u where lower(u.username) = :un", User.class)
                        .setParameter("un", username.toLowerCase(java.util.Locale.ROOT))
                        .uniqueResult();
            } catch (org.hibernate.NonUniqueResultException e) {
                try { client.sendToClient(new Message("logout_error", "Duplicate username", null)); } catch (IOException ignored) {}
                return;
            }

            if (user == null) {
                try { client.sendToClient(new Message("logout_error", "User not found", null)); } catch (IOException ignored) {}
                return;
            }

            // Atomic logout: flip to false only if currently true
            Transaction tx = session.beginTransaction();
            int updated;
            try {
                updated = session.createQuery(
                                "update User u set u.isLoggedIn = false " +
                                        "where u.id = :id and u.isLoggedIn = true")
                        .setParameter("id", user.getId())
                        .executeUpdate();
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }

            // Idempotent response
            final String msgType = (message != null && "force_logout".equals(message.getMessage()))
                    ? "force_logout" : "logout_success";
            try { client.sendToClient(new Message(msgType, username, null)); } catch (IOException ignored) {}

        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("logout_error", "Exception: " + e.getMessage(), null)); } catch (IOException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void handleReportComplaints(Message m, ConnectionToClient client, Session session) {
        try {
            Map<String,Object> crit = (Map<String,Object>) m.getObject();
            LocalDateTime from  = (LocalDateTime) crit.get("from");
            LocalDateTime to    = (LocalDateTime) crit.get("to");
            String branch       = crit.get("branch") == null ? null : crit.get("branch").toString();

            StringBuilder hql = new StringBuilder(
                    "SELECT c FROM Complaint c " +
                            "LEFT JOIN FETCH c.branch b " +
                            "WHERE 1=1 "
            );
            if (from != null)  hql.append("AND c.submittedAt >= :from ");
            if (to != null)    hql.append("AND c.submittedAt < :to ");
            if (branch != null && !branch.isBlank()) hql.append("AND b.name = :branch ");
            hql.append("ORDER BY c.submittedAt ASC");

            var q = session.createQuery(hql.toString(), Complaint.class);
            if (from != null)  q.setParameter("from", from);
            if (to != null)    q.setParameter("to", to);
            if (branch != null && !branch.isBlank()) q.setParameter("branch", branch);

            List<Complaint> list = q.getResultList();

            Map<String, Long> perDay = new TreeMap<>();
            long resolved = 0;
            for (Complaint c : list) {
                String day = (c.getSubmittedAt() == null ? "unknown" : c.getSubmittedAt().toLocalDate().toString());
                perDay.merge(day, 1L, Long::sum);
                if (Boolean.TRUE.equals(c.isResolved())) resolved++;
            }
            long total = list.size();
            long unresolved = total - resolved;
            String peakDay = perDay.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("-");

            Map<String,Object> payload = new HashMap<>();
            payload.put("histogram", perDay);
            payload.put("total", total);
            payload.put("resolved", resolved);
            payload.put("unresolved", unresolved);
            payload.put("peakDay", peakDay);

            payload.put("slot", crit.get("slot"));
            sendMsg(client, new Message("report_complaints_data", payload, null), "report_complaints");
        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("report_complaints_data", null, null)); } catch (IOException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void handleReportOrders(Message m, ConnectionToClient client, Session session) {
        try {
            Map<String,Object> crit = (Map<String,Object>) m.getObject();
            LocalDateTime from  = (LocalDateTime) crit.get("from");
            LocalDateTime to    = (LocalDateTime) crit.get("to");
            String branch       = crit.get("branch") == null ? null : crit.get("branch").toString();

            List<Order> orders = fetchOrdersForRange(session, from, to, branch);

            Map<String, Integer> hist = new TreeMap<>();
            int totalOrders = orders.size();
            int cancelled = 0; // TODO: if you track status, compute cancelled count
            int netOrders = totalOrders - cancelled;

            for (Order o : orders) {
                if (o.getItems() == null) continue;
                for (OrderItem it : o.getItems()) {
                    String key;
                    if (it.getProduct() != null && it.getProduct().getName() != null) {
                        key = it.getProduct().getName();
                    } else if (it.getCustomBouquet() != null) {
                        key = "Custom Bouquet";
                    } else {
                        key = "Unknown";
                    }
                    int qty = Math.max(1, it.getQuantity());
                    hist.merge(key, qty, Integer::sum);
                }
            }

            Map<String,Object> payload = new HashMap<>();
            payload.put("histogram", hist);
            payload.put("totalOrders", totalOrders);
            payload.put("cancelled", cancelled);
            payload.put("netOrders", netOrders);
            payload.put("slot", crit.get("slot"));
            sendMsg(client, new Message("report_orders_data", payload, null), "report_orders");
        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("report_orders_data", null, null)); } catch (IOException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void handleReportIncome(Message m, ConnectionToClient client, Session session) {
        try {
            Map<String,Object> crit = (Map<String,Object>) m.getObject();
            LocalDateTime from  = (LocalDateTime) crit.get("from");
            LocalDateTime to    = (LocalDateTime) crit.get("to");
            String branch       = crit.get("branch") == null ? null : crit.get("branch").toString();

            List<Order> orders = fetchOrdersForRange(session, from, to, branch);

            Map<String, Double> perDay = new TreeMap<>();
            double totalRevenue = 0.0;

            for (Order o : orders) {
                double orderTotal = 0.0;
                if (o.getItems() != null) {
                    for (OrderItem it : o.getItems()) {
                        if (it == null) continue;
                        if (it.getProduct() != null) {
                            orderTotal += it.getProduct().getPrice() * it.getQuantity();
                        } else if (it.getCustomBouquet() != null) {
                            CustomBouquet cb = it.getCustomBouquet();
                            double price = 0.0;
                            if (cb.getTotalPrice() != null) {
                                price = cb.getTotalPrice().doubleValue();
                            } else if (cb.getItems() != null) {
                                price = cb.getItems().stream()
                                        .mapToDouble(li -> {
                                            var unit = li.getUnitPriceSnapshot();
                                            return (unit == null ? 0.0 : unit.doubleValue()) * Math.max(0, li.getQuantity());
                                        }).sum();
                            }
                            orderTotal += price * Math.max(1, it.getQuantity());
                        }
                    }
                }
                totalRevenue += orderTotal;
                String day = (o.getOrderDate() == null ? "unknown" : o.getOrderDate().toLocalDate().toString());
                perDay.merge(day, orderTotal, Double::sum);
            }

            double totalExpenses = 0.0; // plug COGS later if you want
            double net           = totalRevenue - totalExpenses;

            long days = 1;
            if (from != null && to != null) {
                long d = java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
                if (d > 0) days = d;
            }
            double avgDailyNet = net / days;

            // ---- Map payload only (no DTOs) ----
            Map<String,Object> payload = new HashMap<>();
            payload.put("histogram", perDay);             // Map<String, Double>
            payload.put("totalRevenue", totalRevenue);    // double
            payload.put("totalExpenses", totalExpenses);  // double
            payload.put("netProfit", net);                // double
            payload.put("avgDailyNet", avgDailyNet);      // double
            payload.put("transactions", orders.size());   // int

            // Compare-mode routing (safe to include)
            payload.put("requestId", crit.get("requestId"));
            payload.put("slot",      crit.get("slot"));

            // Optional extra labels (client won’t break if unused)
            payload.put("branchLabel", (branch == null || branch.isBlank()) ? "All Branches" : branch);
            payload.put("fromLabel",  from == null ? null : from.toLocalDate().toString());
            payload.put("toLabel",    to   == null ? null : to.minusSeconds(1).toLocalDate().toString());
            payload.put("slot", crit.get("slot"));
            sendMsg(client, new Message("report_income_data", payload, null), "report_income");
        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("report_income_data", null, null)); } catch (IOException ignored) {}
        }
    }

    private List<Order> fetchOrdersForRange(Session session, LocalDateTime from, LocalDateTime to, String branch) {
        Transaction tx = session.getTransaction().isActive() ? session.getTransaction() : session.beginTransaction();
        try {
            StringBuilder hql = new StringBuilder(
                    "SELECT DISTINCT o FROM Order o " +
                            "LEFT JOIN FETCH o.items i " +
                            "LEFT JOIN FETCH i.product p " +
                            "LEFT JOIN FETCH i.customBouquet cb " +
                            "WHERE 1=1 "
            );
            if (from != null)  hql.append("AND o.orderDate >= :from ");
            if (to != null)    hql.append("AND o.orderDate < :to ");
            if (branch != null && !branch.isBlank()) hql.append("AND o.branchName = :branch ");
            hql.append("ORDER BY o.orderDate ASC");

            var q = session.createQuery(hql.toString(), Order.class);
            if (from != null)  q.setParameter("from", from);
            if (to != null)    q.setParameter("to", to);
            if (branch != null && !branch.isBlank()) q.setParameter("branch", branch);

            List<Order> res = q.getResultList();

            // hydrate bouquet items so totalPrice is valid, if needed
            List<Long> bouquetIds = res.stream()
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
                                        "LEFT JOIN FETCH li.flower f " +
                                        "WHERE cb.id IN :ids", CustomBouquet.class)
                        .setParameter("ids", bouquetIds)
                        .getResultList();
            }

            if (tx.isActive()) tx.commit();
            return res;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
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
    private void handleCancelOrder(Message m, ConnectionToClient client, Session session) {
        if (m.getObjectList() == null || m.getObjectList().isEmpty()) {
            sendCancelError(client, "No order ID provided.");
            return;
        }

        Object first = m.getObjectList().get(0);
        Long orderId;
        try {
            orderId = (Long) first;
        } catch (Exception e) {
            sendCancelError(client, "Invalid order ID.");
            return;
        }

        Transaction tx = null;
        try {
            tx = session.beginTransaction();

            Order order = session.get(Order.class, orderId);
            if (order == null) {
                sendCancelError(client, "Order not found.");
                return;
            }

            Customer customer = order.getCustomer();

            // Calculate refund based on delivery time
            double refund = 0;
            if (customer != null && order.getDeliveryDateTime() != null) {
                LocalDateTime deliveryTime = order.getDeliveryDateTime();
                LocalDateTime now = LocalDateTime.now();
                Duration diff = Duration.between(now, deliveryTime);
                long hoursUntilDelivery = diff.toHours();

                if (hoursUntilDelivery >= 24) {
                    refund = order.getTotalPrice(); // full refund
                } else if (hoursUntilDelivery >= 3) {
                    refund = order.getTotalPrice() * 0.5; // 50% refund
                } else {
                    refund = 0; // no refund
                }
            }

            // Apply refund to customer's budget
            Budget budget = customer.getBudget();
            if (budget != null && refund > 0) {
                budget.addFunds(refund);
                session.update(budget); // persist update
            }

            // Delete complaints linked to the order
            List<Complaint> complaints = session.createQuery(
                            "FROM Complaint c WHERE c.order.id = :orderId", Complaint.class)
                    .setParameter("orderId", orderId)
                    .list();

            for (Complaint c : complaints) {
                session.delete(c);
            }

            // Delete the order itself
            session.delete(order);

            session.flush();
            tx.commit();

            // Send success response including updated budget
            Message response = new Message(
                    "order_cancelled_successfully",
                    budget, // include updated budget
                    new ArrayList<>(List.of(orderId))
            );
            client.sendToClient(response);

            System.out.println("Order " + orderId + " cancelled successfully. Refund: " + refund);

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
            sendCancelError(client, "Error cancelling order: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMarkOrderCompleted(Message m, ConnectionToClient client, org.hibernate.Session session) {
        org.hibernate.Transaction tx = session.beginTransaction();
        try {
            Long orderId = null;

            // Accept either a Map {"orderId": ...} in objectList, a Number in objectList, or a Number in object
            if (m.getObjectList() != null && !m.getObjectList().isEmpty()) {
                Object first = m.getObjectList().get(0);
                if (first instanceof java.util.Map<?,?> map) {
                    Object v = map.get("orderId");
                    if (v instanceof Number n) orderId = n.longValue();
                } else if (first instanceof Number n) {
                    orderId = n.longValue();
                }
            } else if (m.getObject() instanceof Number n) {
                orderId = n.longValue();
            }

            if (orderId == null) {
                tx.rollback();
                client.sendToClient(new Message("order_completed_ack", null, null));
                return;
            }

            Order o = session.get(Order.class, orderId);
            if (o == null) {
                tx.rollback();
                client.sendToClient(new Message("order_completed_ack", null, null));
                return;
            }

            String before = java.util.Objects.toString(o.getStatus(), "PLACED");
            String after  = java.lang.Boolean.TRUE.equals(o.getDelivery()) ? "DELIVERED" : "PICKED_UP";

            // Idempotent: only act if status actually changes
            if (!before.equals(after)) {
                o.setStatus(after);
                session.merge(o);
                session.flush();

                if (o.getCustomer() != null) {
                    String title = after.equals("DELIVERED") ? "Order delivered" : "Order picked up";
                    String body  = after.equals("DELIVERED")
                            ? "Your order #" + o.getId() + " was delivered. Enjoy!"
                            : "Thanks! Your order #" + o.getId() + " was picked up.";
                    createPersonalNotification(session, o.getCustomer().getId(), title, body);
                }

                // Push to all UIs
                sendToAllClients(new Message(
                        "orders_state_changed",
                        java.util.Map.of("orderId", o.getId(), "status", o.getStatus()),
                        null));
            }

            tx.commit();
            client.sendToClient(new Message("order_completed_ack", o.getId(), null));
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("order_completed_ack", null, null)); } catch (IOException ignored) {}
        }
    }



    /**
     * Send cancellation error to client.
     * Only define this once in your server class.
     */
    private void sendCancelError(ConnectionToClient client, String msg) {
        try {
            Message response = new Message(
                    "order_cancel_error",
                    null,
                    new ArrayList<>(List.of(msg))  // Wrap string in a list
            );
            client.sendToClient(response);
        } catch (IOException ex) {
            ex.printStackTrace();
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
            c.setSubmittedAt(LocalDateTime.now());
            c.setDeadline(c.getSubmittedAt().plusHours(24));
            c.setResolved(false);
            c.setBranch(customer.getBranch());

            session.persist(c);
            tx.commit();

            // Ack to submitting customer (client will navigate away)
            client.sendToClient(new Message("complaint_submitted", new ComplaintDTO(c), null));

            // Live-refresh any open UIs (employee/customer lists)
            sendToAllClients(new Message("complaints_refresh", null, null));

            // Create the personal notification in a separate, safe TX
            try (Session s2 = HibernateUtil.getSessionFactory().openSession()) {
                var tx2 = s2.beginTransaction();
                Customer fresh = s2.get(Customer.class, customer.getId());
                if (fresh != null) {
                    Notification n = new Notification(
                            fresh,
                            "Complaint submitted",
                            "Your complaint #" + c.getId() + " was received. We'll get back to you within 24h."
                    );
                    s2.persist(n);
                    s2.flush();
                    pushInboxPersonalNew(fresh.getId(), n);
                }
                tx2.commit();
            } catch (Exception notifEx) {
                notifEx.printStackTrace(); // log and continue; don't break complaint flow
            }

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            try { client.sendToClient(new Message("complaint_error", "Exception: " + e.getMessage(), null)); }
            catch (IOException ignored) {}
        }
    }



    @SuppressWarnings("unchecked")
    private void handleRequestOrdersByDay(Message m, ConnectionToClient client, Session session) {
        try {
            System.out.println("[SRV][orders_by_day] key=" + m.getMessage() +
                    " obj=" + (m.getObject() == null ? "null" : m.getObject().getClass().getName()) +
                    " listSize=" + (m.getObjectList() == null ? 0 : m.getObjectList().size()));

            // Accept a String "yyyy-MM-dd" or LocalDate/LocalDateTime; fallback to today.
            LocalDate day = null;
            Object obj = m.getObject();
            if (obj instanceof LocalDate) {
                day = (LocalDate) obj;
            } else if (obj instanceof LocalDateTime) {
                day = ((LocalDateTime) obj).toLocalDate();
            } else if (obj instanceof String s && s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                day = LocalDate.parse(s);
            }
            if (day == null) day = LocalDate.now();

            LocalDateTime start = day.atStartOfDay();
            LocalDateTime next  = day.plusDays(1).atStartOfDay();   // use < next (no off-by-nanos)

            // HQL over your entity class name "Order"
            String hql =
                    "select distinct o " +
                            "from Order o " +
                            "left join fetch o.items i " +
                            "left join fetch i.product p " +
                            "left join fetch o.customer c " +
                            "where (o.pickupDateTime is not null and o.pickupDateTime >= :start and o.pickupDateTime < :next) " +
                            "   or (o.deliveryDateTime is not null and o.deliveryDateTime >= :start and o.deliveryDateTime < :next) " +
                            "   or ( (o.pickupDateTime is null and o.deliveryDateTime is null) " +
                            "        and o.orderDate >= :start and o.orderDate < :next ) " +
                            "order by coalesce(o.pickupDateTime, o.deliveryDateTime, o.orderDate)";

            List<Order> orders = session.createQuery(hql, Order.class)
                    .setParameter("start", start)
                    .setParameter("next",  next)
                    .getResultList();

            // Map to DTOs the client expects
            List<ScheduleOrderDTO> dtoList = new ArrayList<>();
            for (Order o : orders) {
                boolean delivery = Boolean.TRUE.equals(o.getDelivery());
                java.time.LocalDateTime when = o.getDeliveryDateTime();
               // java.time.LocalDateTime when = delivery
               //         ? o.getDeliveryDateTime()
               //         : o.getPickupDateTime(); // final fallback

                // whereText: branch for pickup, address for delivery
                String whereText = "-";
                if (delivery) {
                    String addr = String.valueOf(o.getDeliveryAddress());
                    if (addr != null && !addr.isBlank()) whereText = addr;
                } else {
                    String branch = o.getStoreLocation(); // this is your branchName string
                    if (branch != null && !branch.isBlank()) whereText = branch;
                }

                // customer name (fallbacks safe)
                String customerName = "-";
                Customer cust = o.getCustomer();
                if (cust != null && cust.getUsername() != null && !cust.getUsername().isBlank()) {
                    customerName = cust.getUsername();
                }

                // items
                List<ScheduleItemDTO> items = new ArrayList<>();
                if (o.getItems() != null) {
                    for (OrderItem oi : o.getItems()) {
                        String name = "Item";
                        String imagePath = null;
                        java.math.BigDecimal unitPrice = java.math.BigDecimal.ZERO;

                        if (oi.getProduct() != null) {
                            Product p = oi.getProduct();
                            name = String.valueOf(p.getName());
                            imagePath = p.getImagePath();
                            java.math.BigDecimal snap = oi.getUnitPriceSnapshot();
                            unitPrice = (snap != null) ? snap : java.math.BigDecimal.valueOf(p.getPrice());
                        } else if (oi.getCustomBouquet() != null) {
                            name = "Custom bouquet";
                            java.math.BigDecimal snap = oi.getUnitPriceSnapshot();
                            if (snap != null) unitPrice = snap;
                        }

                        int qty = Math.max(1, oi.getQuantity());
                        items.add(new ScheduleItemDTO(name, qty, unitPrice, imagePath));
                    }
                }

                ScheduleOrderDTO row = new ScheduleOrderDTO(
                        o.getId(),
                        customerName,
                        delivery,
                        when,
                        whereText,
                        String.valueOf(o.getStatus() == null ? "PLACED" : o.getStatus())
                );
                row.setItems(items);
                dtoList.add(row);
            }

            // Client’s EmployeeScheduleController listens for "orders_for_day" in the message.object (not objectList)
            client.sendToClient(new Message("orders_for_day", dtoList, null));
            System.out.println("[SEND][orders_by_day] -> orders_for_day (obj=ArrayList, size=" + dtoList.size() + ")");

        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("orders_for_day", List.of(), null)); }
            catch (java.io.IOException ignored) {}
        }
    }





    @SuppressWarnings("unchecked")
    private void handleMarkOrderReady(Message m, ConnectionToClient client, Session session) {
        var tx = session.beginTransaction();
        try {
            Long orderId = null;
            if (m.getObjectList()!=null && !m.getObjectList().isEmpty() && m.getObjectList().get(0) instanceof Map) {
                Object v = ((Map<String,Object>)m.getObjectList().get(0)).get("orderId");
                if (v instanceof Number) orderId = ((Number) v).longValue();
            }
            if (orderId == null) { tx.rollback(); return; }

            Order o = session.get(Order.class, orderId);
            if (o == null) { tx.rollback(); return; }

            final String before = java.util.Objects.toString(o.getStatus(), "PLACED");
            final boolean delivery = java.lang.Boolean.TRUE.equals(o.getDelivery());

            // idempotency: do nothing if already in or beyond "ready"
            if ((!delivery && (before.equals("READY_FOR_PICKUP") || before.equals("PICKED_UP")))
                    || ( delivery && (before.equals("OUT_FOR_DELIVERY") || before.equals("DELIVERED")))) {
                tx.rollback();                           // no changes -> no extra inbox
                client.sendToClient(new Message("order_ready_ack", o.getId(), null));
                return;
            }

            // transition
            String after = delivery ? "OUT_FOR_DELIVERY" : "READY_FOR_PICKUP";
            o.setStatus(after);
            session.merge(o);
            session.flush();

            // single inbox only when changed
            if (o.getCustomer()!=null) {
                String title = delivery ? "Order out for delivery" : "Order ready for pickup";
                String body  = delivery ? "Your order #" + o.getId() + " is on the way."
                        : "Your order #" + o.getId() + " is ready for pickup.";
                createPersonalNotification(session, o.getCustomer().getId(), title, body);
            }

            tx.commit();

            client.sendToClient(new Message("order_ready_ack", o, null));
            // notify all UIs (employees, customers)
            sendToAllClients(new Message("orders_state_changed",
                    java.util.Map.of("orderId", o.getId(), "status", o.getStatus()), null));

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
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

            List<ComplaintDTO> dto = list.stream().map(ComplaintDTO::new).collect(Collectors.toList());
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

    // --- PUSH: send a single new personal/broadcast inbox item to clients ---

    private void pushInboxPersonalNew(long customerId, Notification n) {
        InboxItemDTO dto = toDTO(n);
        sendToAllClients(new Message("inbox_personal_new", dto, List.of(customerId)));
    }

    private void pushInboxBroadcastNew(Notification n) {
        InboxItemDTO dto = toDTO(n);
        sendToAllClients(new Message("inbox_broadcast_new", dto, null));
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


    @SuppressWarnings("unchecked")
    private void handleCreateBroadcast(Message m, ConnectionToClient client, Session session) {
        try {
            if (m.getObjectList()==null || m.getObjectList().isEmpty() || !(m.getObjectList().get(0) instanceof Map)) {
                sendMsg(client, new Message("broadcast_error", "missing_payload", null), "create_broadcast");
                return;
            }
            Map<String,Object> p = (Map<String,Object>) m.getObjectList().get(0);
            String title = Objects.toString(p.get("title"), "").trim();
            String body  = Objects.toString(p.get("body"),  "").trim();
            if (title.isBlank() || body.isBlank()) {
                sendMsg(client, new Message("broadcast_error", "empty_fields", null), "create_broadcast");
                return;
            }
            var tx = session.beginTransaction();
            createBroadcastNotification(session, title, body); // flush + push happens inside
            tx.commit();
            sendMsg(client, new Message("broadcast_created", null, null), "create_broadcast");
        } catch (Exception ex) {
            ex.printStackTrace();
            sendMsg(client, new Message("broadcast_error", "server_exception", null), "create_broadcast");
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
            BigDecimal compensation = null;

            // --- parse payload ---
            if (m.getObjectList() != null && !m.getObjectList().isEmpty()) {
                Object first = m.getObjectList().get(0);
                if (first instanceof Map) {
                    Map<?,?> p = (Map<?,?>) first;
                    if (p.get("complaintId") != null) {
                        complaintId = Long.valueOf(p.get("complaintId").toString());
                    }
                    if (p.get("responseText") instanceof String) {
                        responseText = ((String) p.get("responseText")).trim();
                    }
                    if (p.get("compensation") != null) {
                        compensation = new BigDecimal(p.get("compensation").toString());
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
            c.setResolvedAt(LocalDateTime.now());
            if (responseText != null) c.setResponseText(responseText);
            if (compensation != null) c.setCompensationAmount(compensation);
            if (responder != null) {
                c.setResponder(responder);
                c.setResponderName(responder.toString());
            }

            session.merge(c);

            // --- Compensation logic ---
            if (compensation != null && compensation.compareTo(BigDecimal.ZERO) > 0) {
                Customer managedCustomer = session.get(Customer.class, c.getCustomer().getId());
                if (managedCustomer != null) {
                    Budget budget = managedCustomer.getBudget();
                    if (budget == null) {
                        budget = new Budget(managedCustomer, 0);
                        managedCustomer.setBudget(budget);
                        session.persist(budget);
                    }
                    double oldBal = budget.getBalance();
                    budget.addFunds(compensation.doubleValue());

                    // No need for session.update(budget) if budget is managed
                    session.merge(managedCustomer);  // ensure customer + budget changes are tracked

                    System.out.printf("[DBG] Compensation added: customer %d balance %.2f -> %.2f%n",
                            managedCustomer.getId(), oldBal, budget.getBalance());
                }
            }

            createPersonalNotification(session,
                    c.getCustomer().getId(),
                    "Complaint resolved",
                    "We’ve reviewed your complaint #" + c.getId() + ". " +
                            (c.getResponseText() == null ? "" : c.getResponseText()) +
                            (c.getCompensationAmount() != null ? " Compensation: " + c.getCompensationAmount() + "." : "")
            );

            tx.commit();

            client.sendToClient(new Message("complaint_resolved", new ComplaintDTO(c), null));
            sendToAllClients(new Message("complaints_refresh", null, null));
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try {
                client.sendToClient(new Message("complaint_resolve_error", "Exception: " + e.getMessage(), null));
            } catch (IOException ignored) {}
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


    // --- DTO mapper ---
    private InboxItemDTO toDTO(
            Notification n) {
        return new InboxItemDTO(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getCreatedAt(),
                n.isReadFlag(),
                n.getCustomer() == null // broadcast if no customer
        );
    }


    /** Fetch inbox for a given customer: personal (sorted desc) + broadcast (customer==null). */
    private void handleGetInbox(Message m, ConnectionToClient client, Session session) {
        try {
            Long customerId = null;
            if (m.getObjectList() != null && !m.getObjectList().isEmpty() && m.getObjectList().get(0) instanceof Map) {
                Object v = ((Map<?, ?>) m.getObjectList().get(0)).get("customerId");
                if (v instanceof Number) customerId = ((Number) v).longValue();
            }
            if (customerId == null) {
                sendMsg(client, new Message("inbox_list_error", "missing_customer_id", null), "get_inbox");
                return;
            }

            var personalEntities = session.createQuery(
                    "from Notification n where n.customer.id = :cid order by n.createdAt desc",
                    Notification.class
            ).setParameter("cid", customerId).getResultList();

            var broadcastEntities = session.createQuery(
                    "from Notification n where n.customer is null order by n.createdAt desc",
                    Notification.class
            ).getResultList();

            List<InboxItemDTO> personalDTO =
                    personalEntities.stream().map(this::toDTO).toList();
            List<InboxItemDTO> broadcastDTO =
                    broadcastEntities.stream().map(this::toDTO).toList();

            var payload = new InboxListDTO(personalDTO, broadcastDTO);
            sendMsg(client, new Message("inbox_list", payload, null), "get_inbox");
        } catch (Exception ex) {
            ex.printStackTrace();
            sendMsg(client, new Message("inbox_list_error", "server_exception", null), "get_inbox");
        }
    }

    // --- INBOX: mark one personal notification as read ---
    private void handleMarkNotificationRead(Message m, ConnectionToClient client, Session session) {
        try {
            if (m.getObjectList() == null || m.getObjectList().isEmpty()) {
                sendMsg(client, new Message("inbox_list_error", "missing_notification_id", null), "mark_notification_read");
                return;
            }
            long id = ((Number) m.getObjectList().get(0)).longValue();

            var tx = session.beginTransaction();
            Notification n = session.get(Notification.class, id);
            if (n == null) {
                tx.rollback();
                sendMsg(client, new Message("inbox_list_error", "not_found", null), "mark_notification_read");
                return;
            }
            // broadcast notifications (customer == null) don't have read flag
            if (n.getCustomer() == null) {
                tx.rollback();
                sendMsg(client, new Message("inbox_list_error", "broadcast_has_no_read_flag", null), "mark_notification_read");
                return;
            }

            n.setReadFlag(true);
            session.merge(n);
            tx.commit();

            sendMsg(client, new Message("inbox_read_ack", id, null), "mark_notification_read");
        } catch (Exception ex) {
            ex.printStackTrace();
            sendMsg(client, new Message("inbox_list_error", "server_exception", null), "mark_notification_read");
        }
    }

    private void handleMarkNotificationUnread(Message m, ConnectionToClient client, Session session) {
        try {
            if (m.getObjectList() == null || m.getObjectList().isEmpty()) {
                sendMsg(client, new Message("inbox_list_error", "missing_notification_id", null), "mark_notification_unread");
                return;
            }
            long id = ((Number) m.getObjectList().get(0)).longValue();

            var tx = session.beginTransaction();
            Notification n = session.get(Notification.class, id);
            if (n == null || n.getCustomer() == null) {
                tx.rollback();
                sendMsg(client, new Message("inbox_list_error", "not_found_or_broadcast", null), "mark_notification_unread");
                return;
            }
            n.setReadFlag(false);
            session.merge(n);
            tx.commit();

            sendMsg(client, new Message("inbox_unread_ack", id, null), "mark_notification_unread");
        } catch (Exception ex) {
            ex.printStackTrace();
            sendMsg(client, new Message("inbox_list_error", "server_exception", null), "mark_notification_unread");
        }
    }


    private void handleBranchesRequest(Message msg, ConnectionToClient client, Session session) throws IOException {
        List<Branch> branches = getListFromDB(session, Branch.class);
        for (Branch branch : branches) {
            System.out.println("Branch: " + branch.getName());
        }
        client.sendToClient(new Message("Branches", branches, null));
    }

    private void createBroadcastNotification(Session session, String title, String body) {
        Notification n = new Notification(null, title, body);
        session.persist(n);
        session.flush();                 // ensure ID exists
        pushInboxBroadcastNew(n);        // <— NEW
    }

    private void createPersonalNotification(Session session, Long customerId, String title, String body) {
        Customer c = session.get(Customer.class, customerId);
        if (c == null) return;
        Notification n = new Notification(c, title, body);
        session.persist(n);
        session.flush();                 // ensure ID exists
        pushInboxPersonalNew(customerId, n);   // <— NEW
    }




    private void handleCustomerDataRequest(Message message, ConnectionToClient client, Session session) {
        try {
            Object payload = message.getObject();
            User user = null;

            if (payload instanceof User) {
                user = (User) payload;
            } else if (payload instanceof List) {
                List<?> list = (List<?>) payload;
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
            } else if (payload instanceof List) {
                List<?> list = (List<?>) payload;
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
            User mergedUser;
                mergedUser = (User) session.merge(user);
                if (customer != null) session.merge(customer);
                tx.commit();
            session.merge(user);
            if (customer != null) session.merge(customer);

            session.flush();

            createPersonalNotification(session,
                    customer.getId(),
                    "Profile updated",
                    "Your profile details were updated successfully.");


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
                    Stream.concat(cartItemsForProduct.stream(), cartItemsForBouquets.stream())
                            .collect(Collectors.toList()));
            payload.put("removedCount", cartItemsForProduct.size() + cartItemsForBouquets.size());
            payload.put("deletedBouquetIds", deletableBouquetIds);
            payload.put("protectedBouquetIds", protectedBouquetIds);
            sendToAllClients(new Message("item_removed", payload, null));
            createBroadcastNotification(session,
                    "Product removed",
                    "A product was removed from the catalog. Your cart may have been updated.");


        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("delete_failed", null, null)); } catch (IOException ignored) {}
        }
    }

    private static final File IMAGES_DIR = new File("images").getAbsoluteFile();

    private static String getExtension(String name) {
        if (name == null) return "bin";
        int i = name.lastIndexOf('.');
        return (i > 0 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase() : "bin";
    }

    private static String normalizeRelative(String path) {
        return path.replace('\\', '/');
    }

    private void handleAddProduct(Message msg, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Object payload = msg.getObject();

            // Back-compat: old clients send Product directly
            if (!(payload instanceof AddProductRequest)) {
                Product legacy = (Product) payload;
                session.save(legacy);
                tx.commit();

                catalogLock.writeLock().lock();
                try { catalog.getFlowers().add(legacy); }
                finally { catalogLock.writeLock().unlock(); }

                sendToAllClients(new Message("add_product", legacy, null));
                return;
            }

            AddProductRequest req = (AddProductRequest) payload;
            Product product = req.productMeta;

            byte[] broadcastBytes = null;

            // --- Save the image into SERVER images/ (if provided) ---
            if (req.imageBytes != null && req.imageBytes.length > 0) {
                if (!IMAGES_DIR.exists() && !IMAGES_DIR.mkdirs()) {
                    throw new IOException("Cannot create images dir: " + IMAGES_DIR.getAbsolutePath());
                }
                String ext = getExtension(req.imageName);
                String fileName = java.util.UUID.randomUUID().toString() + "." + ext;
                File out = new File(IMAGES_DIR, fileName);

                // WRITE FIRST
                try (OutputStream os = new FileOutputStream(out)) {
                    os.write(req.imageBytes);
                }
                // set path stored in DB as server-relative
                String relative = normalizeRelative("images/" + fileName);
                product.setImagePath(relative);

                // BROADCAST the bytes we already have (don’t re-read)
                broadcastBytes = req.imageBytes;
            } else {
                product.setImagePath(null);
            }
            // --------------------------------------------------------

            session.save(product);
            tx.commit();

            // update in-memory snapshot
            catalogLock.writeLock().lock();
            try {
                catalog.getFlowers().add(product);
            } finally {
                catalogLock.writeLock().unlock();
            }

            // Broadcast new product + its image bytes to all clients
            sendToAllClients(new Message(
                    "add_product",
                    product,
                    (broadcastBytes != null ? java.util.List.of(broadcastBytes) : null)
            ));

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("add_failed", null, null)); }
            catch (IOException ignored) {}
        }
    }


    private boolean checkExistenceUsername(Session session, String username) {
        if (username == null || username.isBlank()) {
            return false;
        }

        Long count = session.createQuery(
                        "select count(u.id) from User u where lower(u.username) = :un",
                        Long.class)
                .setParameter("un", username.trim().toLowerCase())
                .getSingleResult();

        return count != null && count > 0;
    }
    private boolean checkExistenceIdNumber(String idNumber,Session session) {
        boolean idNumberExists = false;
        User user = session.createQuery(
                        "SELECT u FROM User u WHERE u.idNumber = :idNumber", User.class)
                .setParameter("idNumber", idNumber)
                .uniqueResult();
        if (user != null) {
            idNumberExists = true;
        }
        return idNumberExists;
    }

    private void handleUserRegistration(Message msg, ConnectionToClient client, Session session) throws IOException {
        String username = ((Customer) (msg.getObject())).getUsername();
        String idNumber = ((Customer) (msg.getObject())).getIdNumber();
            if (checkExistenceUsername(session,username)) {
                client.sendToClient(new Message("user already exists", msg.getObject(), null));
                return;
            }
            if(checkExistenceIdNumber(idNumber, session)) {
                client.sendToClient(new Message("id already exists", msg.getObject(), null));
                return;
            }
            Transaction tx = session.beginTransaction();
            try {
                Customer incoming = (Customer) msg.getObject();

                session.save(incoming);
                tx.commit();

                client.sendToClient(new Message("registered", null, null));
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                try { client.sendToClient(new Message("registration_failed", null, null)); }
                catch (IOException ignored) {}
                e.printStackTrace();
            }
    }


    private void handleCatalogRequest(ConnectionToClient client) {
        try {
            Catalog full = catalog;
            java.util.List<Product> all = (full != null && full.getFlowers() != null)
                    ? full.getFlowers()
                    : java.util.List.of();

            Catalog flowersOnly = new Catalog(
                    all.stream().filter(SimpleServer::isFlower).collect(java.util.stream.Collectors.toList()));
            Catalog nonFlowers  = new Catalog(
                    all.stream().filter(p -> !isFlower(p)).collect(java.util.stream.Collectors.toList()));

            // Build productId -> image bytes (now also supports legacy file:/ and absolute paths)
            Map<Long, byte[]> imageBlobs = new java.util.HashMap<>();
            for (Product p : all) {
                if (p == null || p.getId() == null) continue;
                String sp = p.getImagePath();
                if (sp == null || sp.isBlank()) continue;

                try {
                    File f = resolveServerImageFile(sp);
                    if (f != null && f.exists() && f.isFile()) {
                        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                        if (bytes != null && bytes.length > 0) {
                            imageBlobs.put(p.getId(), bytes);
                        }
                    } else {
                        System.out.println("[catalog] image not found on server: " + sp);
                    }
                } catch (Exception ex) {
                    System.err.println("[catalog] failed reading image '" + sp + "': " + ex.getMessage());
                }
            }

            // Send: object = full catalog; objectList = [flowersOnly, nonFlowers, imageBlobs]
            client.sendToClient(new Message("catalog", full, java.util.List.of(flowersOnly, nonFlowers, imageBlobs)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isFlower(Product p) {
        return p != null && p.getType() != null && p.getType().equalsIgnoreCase("flower");
    }

    private void handleProductEdit(Message msg, ConnectionToClient client, Session session) {
        Product incoming = (Product) msg.getObject();
        if (incoming == null) return;

        // Persist (this already normalizes and copies the image into images/ if accessible)
        boolean updateSuccess = updateProduct(session, incoming.getId(),
                incoming.getName(), incoming.getColor(), incoming.getPrice(),
                incoming.getDiscountPercentage(), incoming.getType(), incoming.getImagePath());

        if (!updateSuccess) return;

        // Re-read the managed product so we have the final (possibly new) imagePath
        Product managed = session.get(Product.class, incoming.getId());
        if (managed == null) return;

        // Refresh in-memory catalog + propagate to open carts
        catalogLock.writeLock().lock();
        try {
            catalog.setFlowers(getListFromDB(session, Product.class));
        } finally {
            catalogLock.writeLock().unlock();
        }
        propagateProductEditToOpenCarts(session, managed.getId());

        // Try to attach image bytes (same idea as in handleAddProduct)
        List<Object> payload = null;
        try {
            String sp = managed.getImagePath();
            if (sp != null && !sp.isBlank()) {
                File f = resolveServerImageFile(sp);
                if (f != null && f.exists() && f.isFile()) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                    if (bytes != null && bytes.length > 0) {
                        payload = java.util.List.of(java.util.Map.of("imageBytes", bytes));
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[editProduct] couldn't attach image bytes: " + ex.getMessage());
        }

        try {
            // Use the managed product (with final imagePath) in the broadcast
            sendToAllClients(new Message(msg.getMessage(), managed, payload));
        } catch (Exception ignored) {}
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
                BigDecimal newUnit = BigDecimal.valueOf(managed.getSalePrice());

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

    @SuppressWarnings("unchecked")
    private void handleUserAuthentication(Message msg, ConnectionToClient client, Session session) {
        try {
            if (msg == null || !(msg.getObject() instanceof java.util.List)) {
                try { client.sendToClient(new Message("authentication_error", null, null)); } catch (IOException ignored) {}
                return;
            }
            java.util.List<String> info = (java.util.List<String>) msg.getObject();
            if (info.size() < 2) {
                try { client.sendToClient(new Message("authentication_error", null, null)); } catch (IOException ignored) {}
                return;
            }

            final String username = info.get(0);
            final String password = info.get(1);

            // Case-insensitive unique username is enforced in DB, so uniqueResult() is OK.
            User user;
            try {
                user = session.createQuery(
                                "select u from User u where lower(u.username) = :un", User.class)
                        .setParameter("un", username.toLowerCase(java.util.Locale.ROOT))
                        .uniqueResult(); // throws NonUniqueResultException if invariant is violated
            } catch (org.hibernate.NonUniqueResultException e) {
                // Defensive: if the DB invariant is ever violated, fail closed.
                try { client.sendToClient(new Message("authentication_error", "Duplicate username", null)); } catch (IOException ignored) {}
                return;
            }

            if (user == null) {
                try { client.sendToClient(new Message("incorrect", null, null)); } catch (IOException ignored) {}
                return;
            }
            if (!java.util.Objects.equals(user.getPassword(), password)) {
                try { client.sendToClient(new Message("incorrect", null, null)); } catch (IOException ignored) {}
                return;
            }
            if (user instanceof Customer && ((Customer) user).isFrozen()) {
                try { client.sendToClient(new Message("frozen", null, null)); } catch (IOException ignored) {}
                return;
            }

            // Atomic login: flip to true only if currently false (and not frozen for customers)
            Transaction tx = session.beginTransaction();
            int updated;
            try {
                if (user instanceof Customer) {
                    updated = session.createQuery(
                                    "update Customer c set c.isLoggedIn = true " +
                                            "where c.id = :id and c.isLoggedIn = false and c.frozen = false")
                            .setParameter("id", user.getId())
                            .executeUpdate();
                } else {
                    updated = session.createQuery(
                                    "update User u set u.isLoggedIn = true " +
                                            "where u.id = :id and u.isLoggedIn = false")
                            .setParameter("id", user.getId())
                            .executeUpdate();
                }
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }

            if (updated == 0) {
                // Someone else logged in first, or (for customers) account got frozen in the meantime
                try { client.sendToClient(new Message("already_logged", null, null)); } catch (IOException ignored) {}
                return;
            }

            // Bulk JPQL bypasses the first-level cache → clear & reload before sending the entity out
            session.clear();
            User fresh = session.find(User.class, user.getId());

            client.setInfo("userId", fresh.getId());
            client.setInfo("username", fresh.getUsername());
            try { client.sendToClient(new Message("correct", fresh, null)); } catch (IOException ignored) {}

        } catch (Exception e) {
            e.printStackTrace();
            try { client.sendToClient(new Message("authentication_error", null, null)); } catch (IOException ignored) {}
        }
    }


    public boolean updateProduct(Session session, Long productId, String newProductName, String newColor,
                                 double newPrice, double newDiscount, String newType, String newImagePath) {
        catalogLock.writeLock().lock();
        Transaction tx = session.beginTransaction();
        try {
            Product product = session.get(Product.class, productId);
            if (product == null) {
                System.out.println("Product not found with ID: " + productId);
                tx.rollback();
                return false;
            }

            product.setPrice(newPrice);
            product.setDiscountPercentage(newDiscount);
            product.setName(newProductName);
            product.setType(newType);
            product.setColor(newColor);

            // ---- Image path normalization (server-side) ----
            String finalPath = product.getImagePath(); // keep old unless we can improve it
            if (newImagePath != null) {
                String sp = newImagePath.trim();
                if (sp.isEmpty()) {
                    finalPath = null; // explicit clear
                } else if (sp.startsWith("images/") || sp.startsWith("images\\")) {
                    // already in server images/ -> normalize slashes
                    finalPath = normalizeRelative(sp);
                } else {
                    // Try to copy a reachable file on the SERVER into images/
                    File source = resolveServerImageFile(sp);
                    if (source != null && source.exists() && source.isFile()) {
                        if (!IMAGES_DIR.exists()) IMAGES_DIR.mkdirs();
                        String ext = getExtension(source.getName());
                        String fileName = java.util.UUID.randomUUID().toString() + "." + ext;
                        File out = new File(IMAGES_DIR, fileName);
                        java.nio.file.Files.copy(
                                source.toPath(),
                                out.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        finalPath = normalizeRelative("images/" + fileName);
                    } else {
                        // not reachable by the server → keep previous imagePath
                        System.err.println("[updateProduct] Provided image path not accessible on server: " + sp);
                    }
                }
            }
            product.setImagePath(finalPath);
            // -----------------------------------------------

            session.update(product);
            tx.commit();
            return true;
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
            if (customer == null) {
                client.sendToClient(new Message("orders_data", List.of(), null));
                return;
            }

            List<Order> orders = session.createQuery(
                            "select distinct o from Order o " +
                                    " left join fetch o.items oi " +
                                    " left join fetch oi.product p " +
                                    " left join fetch o.customer c " +
                                    " where c.id = :cid " +
                                    " order by o.orderDate desc", Order.class)
                    .setParameter("cid", customer.getId())
                    .getResultList();

            for (Order o : orders) {
                for (OrderItem oi : o.getItems()) {
                    CustomBouquet cb = oi.getCustomBouquet();
                    if (cb != null) {
                        org.hibernate.Hibernate.initialize(cb.getItems());
                    }
                }
            }

            client.sendToClient(new Message("orders_data", orders, null));  // single send
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

            double baseTotal = cart.getTotalWithDiscount();
            if(clientOrder.getDelivery()) {
                baseTotal += 20;
            }
            // 3) Build managed Order and copy fields
            Order order = new Order();
            order.setCustomer(managedCustomer);
            order.setStoreLocation(clientOrder.getStoreLocation());
            order.setDelivery(clientOrder.getDelivery());
            order.setOrderDate(LocalDateTime.now());
            order.setDeliveryDateTime(clientOrder.getDeliveryDateTime());
            order.setPickupDateTime(clientOrder.getPickupDateTime());
            order.setRecipientPhone(clientOrder.getRecipientPhone());
            order.setDeliveryAddress(clientOrder.getDeliveryAddress());
            order.setNote(clientOrder.getNote());
            order.setPaymentMethod(clientOrder.getPaymentMethod());
            order.setPaymentDetails(clientOrder.getPaymentDetails());
            order.setCardExpiryDate(clientOrder.getCardExpiryDate());
            order.setCardCVV(clientOrder.getCardCVV());
            order.setTotalPrice(baseTotal);

//            if ("BUDGET".equalsIgnoreCase(clientOrder.getPaymentMethod())) {
//                Budget budget = managedCustomer.getBudget();
//                if (budget == null || budget.getBalance() < cart.getTotalWithDiscount()) {
//                    client.sendToClient(new Message("order_error", "Insufficient budget", null));
//                    tx.rollback();
//                    return;
//                }
//                budget.subtractFunds(cart.getTotalWithDiscount());
//                session.update(budget);
//                order.setPaymentMethod("BUDGET");
//            }
            if ("BUDGET".equalsIgnoreCase(clientOrder.getPaymentMethod())) {
                Budget budget = managedCustomer.getBudget();
                double orderTotal = baseTotal;

                if (budget == null || budget.getBalance() <= 0) {
                    client.sendToClient(new Message("order_error", "No available budget", null));
                    tx.rollback();
                    return;
                }

                // Subtract budget safely: if balance < order total, use all remaining balance
                double usedFromBudget = Math.min(budget.getBalance(), orderTotal);
                budget.setBalance(budget.getBalance() - usedFromBudget);
                session.update(budget); // persist the new balance

                // Set the payment info on the order (optional: partial payment handling)
                order.setPaymentMethod("BUDGET");
            }



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

            session.flush();

            // add notification BEFORE tx.commit()
            createPersonalNotification(session,
                    order.getCustomer().getId(),
                    "Order placed",
                    "Thanks! Your order #" + order.getId() + " was placed. " +
                            (order.getDeliveryDateTime() == null ? "" : "Delivery: " + order.getDeliveryDateTime()));


            tx.commit();
            try {
                // send budget update first so client UIs see new balance before order success UI transitions
                client.sendToClient(new Message("budget_updated", managedCustomer, null));
            } catch (IOException e) {
                e.printStackTrace();
            }
            client.sendToClient(new Message("order_placed_successfully", order, null));

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try {
                client.sendToClient(new Message("order_error", "Failed to place order: " + e.getMessage(), null));
            } catch (IOException ignored) {}
        }
    }

    private void handleBudgetAdd(Message msg, ConnectionToClient client, Session session) {
        Transaction tx = session.beginTransaction();
        try {
            Customer payload = (Customer) msg.getObject();
            Customer dbCustomer = session.get(Customer.class, payload.getId());

            if (dbCustomer != null && dbCustomer.getBudget() != null) {
                double delta = payload.getBudget().getBalance(); // amount to add
                dbCustomer.getBudget().addFunds(delta);
                session.update(dbCustomer.getBudget()); // update the budget itself
            }

            tx.commit();
            client.sendToClient(new Message("budget_updated", dbCustomer, null));

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try {
                client.sendToClient(new Message("budget_update_failed", null, null));
            } catch (IOException ignored) {}
        }
    }



    private void handleBudgetSubtract(Message msg, ConnectionToClient client, Session session) {
        Customer customer = (Customer) msg.getObject();
        Transaction tx = session.beginTransaction();
        try {
            Customer dbCustomer = session.get(Customer.class, customer.getId());
            if (dbCustomer != null && dbCustomer.getBudget() != null) {
                dbCustomer.getBudget().subtractFunds(customer.getBudget().getBalance());
                session.update(dbCustomer.getBudget());
            }
            tx.commit();
            client.sendToClient(new Message("budget_updated", dbCustomer, null));
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            try { client.sendToClient(new Message("budget_update_failed", null, null)); } catch (IOException ignored) {}
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
                line.setUnitPriceSnapshot(it.getUnitPriceSnapshot() != null ? it.getUnitPriceSnapshot() : BigDecimal.ZERO);
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