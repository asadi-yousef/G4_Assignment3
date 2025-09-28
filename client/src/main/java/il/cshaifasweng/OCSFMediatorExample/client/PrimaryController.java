package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PrimaryController implements Initializable {

	@FXML private Button loginButton;
	@FXML private Label userStatusLabel;
	@FXML private HBox customerMenuBar;
	@FXML private HBox employeeMenuBar;
	@FXML private Button logoutButton;
	@FXML private GridPane catalogGrid;
	@FXML private ProgressIndicator loadingIndicator;
	@FXML private TextField searchTextField;
	@FXML private ComboBox<String> typeFilterComboBox;
	@FXML private TextField minPriceField;
	@FXML private TextField maxPriceField;
    @FXML private Button inboxButton;
    @FXML private Button broadcastButton;
	@FXML private Button usersPageButton;
	@FXML private Button budgetButton;
    private int unreadPersonalCount = 0;
    private Label inboxBadge;


    // Reports button (must exist in primary.fxml with fx:id="reportButton")
	@FXML private Button reportButton;

	// Customer-only “Make a Custom Bouquet” button (present in FXML)
	@FXML private Button makeCustomBouquetButton;

	// Optional: a status label to show selected stems count
	@FXML private Label customStatusLabel;

	// Server sends: Message("catalog", full, [flowersOnly, nonFlowers])
	private Catalog fullCatalog;
	private Catalog flowersOnlyCatalog;
	private Catalog nonFlowerCatalog;

	// What we currently render:
	private Catalog catalog;

	private PauseTransition debounceTimer;

	// Custom bouquet mode flag + selections
	private volatile boolean customBouquetMode = false;
	private final Map<Long, Integer> selectedFlowerQuantities = new LinkedHashMap<>();
	private boolean preserveCustomModeOnNextCatalog = false;
	// ---- image bytes sent by the server (productId -> byte[]) ----
	private static final Map<Long, byte[]> IMAGE_CACHE = new ConcurrentHashMap<>();
	public static byte[] getImageBytes(long productId) { return IMAGE_CACHE.get(productId); }
	public static boolean hasImageBytes(long productId) { return IMAGE_CACHE.containsKey(productId); }


	@Override
	public void initialize(URL location, ResourceBundle resources) {
		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this);
		}

        setupInboxBadge();

        debounceTimer = new PauseTransition(Duration.millis(300));
		debounceTimer.setOnFinished(event -> renderCatalog());

		searchTextField.textProperty().addListener((obs, o, n) -> debounceTimer.playFromStart());
		minPriceField.textProperty().addListener((obs, o, n) -> debounceTimer.playFromStart());
		maxPriceField.textProperty().addListener((obs, o, n) -> debounceTimer.playFromStart());
		typeFilterComboBox.valueProperty().addListener((obs, o, n) -> renderCatalog());

		updateUIBasedOnUserStatus();
		loadCatalogData();
	}

	// =================== Rendering ===================

	private void renderCatalog() {
		final boolean isEmployee = SessionManager.getInstance().isEmployee();

		Platform.runLater(() -> {
			catalogGrid.getChildren().clear();

			if (catalog == null || catalog.getFlowers() == null) {
				loadingIndicator.setVisible(false);
				return;
			}

			String searchQuery = valueOrEmpty(searchTextField.getText()).toLowerCase();
			String selectedType = typeFilterComboBox.getValue();
			Optional<BigDecimal> minPrice = parseBigDecimal(minPriceField.getText());
			Optional<BigDecimal> maxPrice = parseBigDecimal(maxPriceField.getText());
			List<Product> productsToRender = catalog.getFlowers().stream()
					.distinct()
					.filter(p -> searchQuery.isEmpty() || safeLower(p.getName()).contains(searchQuery))
					.filter(p -> selectedType == null || selectedType.equals("All Types") || safeEqualsIgnoreCase(p.getType(), selectedType))
					.filter(p -> minPrice.map(min -> p.getPrice().compareTo(min) >= 0).orElse(true))
					.filter(p -> maxPrice.map(max -> p.getPrice().compareTo(max) <= 0).orElse(true))
					.collect(Collectors.toList());

			int col = 0;
			int row = 0;
			for (Product product : productsToRender) {
				try {
					FXMLLoader loader = new FXMLLoader(getClass().getResource("/il/cshaifasweng/OCSFMediatorExample/client/productCard.fxml"));
					StackPane cardNode = loader.load();
					ProductCardController cardController = loader.getController();

					if (isEmployee) {
						cardController.setData(product, updatedProduct -> {
							try {
								Message message = new Message("editProduct", updatedProduct, null);
								SimpleClient.getClient().sendToServer(message);
							} catch (IOException e) { e.printStackTrace(); }
						}, () -> handleDeleteProduct(product));
					} else {
						if (customBouquetMode) {
							cardController.setData(
									product,
									() -> handleViewProduct(product),
									() -> handleSelectFlower(product),
									"Select"
							);
						} else {
							cardController.setData(
									product,
									() -> handleViewProduct(product),
									() -> handleAddToCart(product)
							);
						}
					}

					catalogGrid.add(cardNode, col, row);
					col = (col + 1) % 3;
					if (col == 0) row++;

				} catch (IOException e) {
					System.err.println("CRITICAL ERROR: Could not load productCard.fxml.");
					e.printStackTrace();
				}
			}

			updateCustomStatusLabel();
		});
	}
	/** Persist server-sent image bytes to the per-user cache using the product's server filename. */
	private void persistImageBytesFor(Product p, byte[] data) {
		if (p == null || p.getId() == null || data == null || data.length == 0) return;
		try {
			File target = cacheFileFor(p);  // uses imagePath's last segment or <id>.bin
			writeBytes(target, data);       // will mkdirs() as needed
		} catch (Exception ex) {
			System.err.println("Failed to persist image for product " + p.getId() + ": " + ex.getMessage());
		}
	}

	// Local image cache under the user home
	private static final File LOCAL_IMAGE_CACHE_DIR =
			new File(System.getProperty("user.home"), ".ocsf_app/images").getAbsoluteFile();

	private static void ensureCacheDir() {
		if (!LOCAL_IMAGE_CACHE_DIR.exists()) LOCAL_IMAGE_CACHE_DIR.mkdirs();
	}

	private static File cacheFileFor(Product p) {
		// Prefer server-provided file name from product.getImagePath() -> last segment
		String path = p.getImagePath();
		String fileName = null;
		if (path != null && !path.isBlank()) {
			String norm = path.replace('\\','/');
			int i = norm.lastIndexOf('/');
			fileName = (i >= 0 && i < norm.length()-1) ? norm.substring(i+1) : norm;
		}
		if (fileName == null || fileName.isBlank()) {
			// Fallback: id.bin
			fileName = (p.getId() == null ? "noid" : p.getId().toString()) + ".bin";
		}
		return new File(LOCAL_IMAGE_CACHE_DIR, fileName);
	}

	private static void writeBytes(File target, byte[] data) throws IOException {
		ensureCacheDir();
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(target)) {
			fos.write(data);
		}
	}


	private Product safeCastProduct(Object o) {
		try { return (Product) o; } catch (Exception ignored) { return null; }
	}
	private Long safeCastLong(Object o) {
		try { return (Long) o; } catch (Exception ignored) { return null; }
	}

	private void applyUpsertToFull(Product p) {
		if (p == null) return;
		if (fullCatalog == null) fullCatalog = new Catalog(new ArrayList<>());
		List<Product> list = fullCatalog.getFlowers();
		if (list == null) {
			list = new ArrayList<>();
			fullCatalog.setFlowers(list);
		}
		int idx = indexOfProductById(list, p.getId());
		if (idx >= 0) {
			list.set(idx, p); // replace with edited version
		} else {
			list.add(p); // new product
		}
	}

	private void applyDeleteFromFull(Long id) {
		if (fullCatalog == null || fullCatalog.getFlowers() == null) return;
		fullCatalog.getFlowers().removeIf(prod -> Objects.equals(prod.getId(), id));
	}

	private int indexOfProductById(List<Product> list, Long id) {
		if (id == null) return -1;
		for (int i = 0; i < list.size(); i++) {
			Product p = list.get(i);
			if (p != null && Objects.equals(p.getId(), id)) return i;
		}
		return -1;
	}

	private void rebuildDerivedCatalogsFromFull() {
		if (fullCatalog == null || fullCatalog.getFlowers() == null) {
			flowersOnlyCatalog = new Catalog(new ArrayList<>());
			nonFlowerCatalog   = new Catalog(new ArrayList<>());
			return;
		}
		List<Product> all = fullCatalog.getFlowers();
		List<Product> flowers = all.stream().filter(this::isFlower).collect(Collectors.toList());
		List<Product> non    = all.stream().filter(p -> !isFlower(p)).collect(Collectors.toList());
		flowersOnlyCatalog = new Catalog(flowers);
		nonFlowerCatalog   = new Catalog(non);
	}

	private Catalog flowersOnlyCatalogOrDerived() {
		if (flowersOnlyCatalog != null) return flowersOnlyCatalog;
		// derive on the fly
		if (fullCatalog == null || fullCatalog.getFlowers() == null) return new Catalog(new ArrayList<>());
		List<Product> flowers = fullCatalog.getFlowers().stream().filter(this::isFlower).collect(Collectors.toList());
		return new Catalog(flowers);
	}

	private void refreshViewKeepingMode() {
		// choose which catalog to show based on current mode
		if (customBouquetMode) {
			this.catalog = flowersOnlyCatalogOrDerived();
		} else {
			this.catalog = fullCatalog != null ? fullCatalog : this.catalog;
		}
		populateTypeFilter(this.catalog);
		renderCatalog();
	}

	private void fallbackReloadKeepingMode() {
		preserveCustomModeOnNextCatalog = customBouquetMode;
		loadCatalogData();
	}

	private void updateCustomStatusLabel() {
		if (customStatusLabel == null) return;
		if (!customBouquetMode) {
			customStatusLabel.setText("");
			customStatusLabel.setVisible(false);
			customStatusLabel.setManaged(false);
		} else {
			int total = selectedFlowerQuantities.values().stream().mapToInt(Integer::intValue).sum();
			customStatusLabel.setText("Selected stems: " + total + (total > 0 ? " (tap Select again to add more)" : ""));
			customStatusLabel.setVisible(true);
			customStatusLabel.setManaged(true);
		}
	}

	private String safeLower(String s) { return s == null ? "" : s.toLowerCase(); }
	private boolean safeEqualsIgnoreCase(String a, String b) {
		return a == null ? b == null : a.equalsIgnoreCase(b);
	}
	private String valueOrEmpty(String s) { return s == null ? "" : s; }

	private Optional<BigDecimal> parseBigDecimal(String text) {
		if (text == null || text.trim().isEmpty()) return Optional.empty();
		try {
			return Optional.of(new BigDecimal(text.trim()));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private void populateTypeFilter(Catalog base) {
		if (base == null || base.getFlowers() == null) return;
		Set<String> types = base.getFlowers().stream()
				.map(Product::getType)
				.filter(type -> type != null && !type.trim().isEmpty())
				.collect(Collectors.toCollection(TreeSet::new));

		Platform.runLater(() -> {
			String selected = typeFilterComboBox.getValue();
			typeFilterComboBox.getItems().clear();
			typeFilterComboBox.getItems().add("All Types");
			typeFilterComboBox.getItems().addAll(types);

			if (selected != null && (selected.equals("All Types") || types.contains(selected))) {
				typeFilterComboBox.setValue(selected);
			} else {
				typeFilterComboBox.setValue("All Types");
			}
		});
	}

	@FXML
	void handleClearFilters(ActionEvent event) {
		searchTextField.clear();
		minPriceField.clear();
		maxPriceField.clear();
		typeFilterComboBox.setValue("All Types");
		renderCatalog();
	}

	// =================== Server messages ===================

	@Subscribe
	public void onMessageFromServer(Message msg) {
        if ("orders_for_day".equals(msg.getMessage())) return;
        Platform.runLater(() -> {
			loadingIndicator.setVisible(false);
			switch (msg.getMessage()) {
				case "catalog": {
					this.fullCatalog = (Catalog) msg.getObject();

					List<?> list = msg.getObjectList();
					if (list != null && list.size() >= 2) {
						this.flowersOnlyCatalog = safeCast(list.get(0));
						this.nonFlowerCatalog   = safeCast(list.get(1));

						// index 2 (optional): Map<Long, byte[]> of productId -> image bytes
						if (list.size() >= 3 && list.get(2) instanceof Map<?,?> blobs) {
							for (Map.Entry<?,?> e : ((Map<?,?>) blobs).entrySet()) {
								Object k = e.getKey();
								Object v = e.getValue();
								if (k instanceof Number && v instanceof byte[]) {
									long pid = ((Number) k).longValue();
									byte[] data = (byte[]) v;
									IMAGE_CACHE.put(pid, data);
								}
							}
						}
					} else {
						// server didn't split: derive locally
						rebuildDerivedCatalogsFromFull();
					}

					// choose current catalog: FULL by default (flowers-only if custom mode)
					if (fullCatalog != null) {
						this.catalog = customBouquetMode ? flowersOnlyCatalogOrDerived() : fullCatalog;
						populateTypeFilter(this.catalog);
					}

					// persist any received image bytes to disk cache so ProductCard can load "images/<uuid>.*"
					try {
						if (fullCatalog != null && fullCatalog.getFlowers() != null) {
							for (Product p : fullCatalog.getFlowers()) {
								if (p == null || p.getId() == null) continue;
								byte[] data = IMAGE_CACHE.get(p.getId());
								if (data != null && data.length > 0) {
									persistImageBytesFor(p, data);
								}
							}
						}
					} catch (Exception ex) {
						System.err.println("Catalog image persistence error: " + ex.getMessage());
					}

					// keep custom mode flag if requested
					if (preserveCustomModeOnNextCatalog) {
						preserveCustomModeOnNextCatalog = false;
					}

					updateCustomButtonState();
					renderCatalog();
					break;
				}

				case "add_product": {
					Product added = safeCastProduct(msg.getObject());
					if (added != null) {
						// server may include raw bytes in objectList[0]
						if (msg.getObjectList() != null && !msg.getObjectList().isEmpty()) {
							Object o = msg.getObjectList().get(0);
							if (o instanceof byte[] bytes) {
								IMAGE_CACHE.put(added.getId(), bytes);
								// also persist to local cache immediately for cross-machine loading
								persistImageBytesFor(added, bytes);
							} else if (o instanceof Map<?,?> meta) {
								Object bytes = meta.get("imageBytes");
								if (bytes instanceof byte[] b) {
									IMAGE_CACHE.put(added.getId(), b);
									persistImageBytesFor(added, b);
								}
							}
						}

						applyUpsertToFull(added);
						rebuildDerivedCatalogsFromFull();
						refreshViewKeepingMode();
					} else {
						// fallback if no payload (unlikely)
						fallbackReloadKeepingMode();
					}
					break;
				}

				case "editProduct": {
					Product edited = safeCastProduct(msg.getObject());
					if (edited != null) {
						// NEW: accept imageBytes if server included them (mirror add_product)
						if (msg.getObjectList() != null && !msg.getObjectList().isEmpty()) {
							Object o = msg.getObjectList().get(0);
							if (o instanceof byte[] bytes) {
								IMAGE_CACHE.put(edited.getId(), bytes);
								persistImageBytesFor(edited, bytes);
							} else if (o instanceof Map<?,?> meta) {
								Object bytes = ((Map<?,?>) meta).get("imageBytes");
								if (bytes instanceof byte[] b) {
									IMAGE_CACHE.put(edited.getId(), b);
									persistImageBytesFor(edited, b);
								}
							}
						}

						applyUpsertToFull(edited);
						rebuildDerivedCatalogsFromFull();

						// keep your existing safety net
						try {
							byte[] b = IMAGE_CACHE.get(edited.getId());
							if (b != null && b.length > 0) persistImageBytesFor(edited, b);
						} catch (Exception ignored) {}

						refreshViewKeepingMode();
					} else {
						fallbackReloadKeepingMode();
					}
					break;
				}


				case "delete_product": {
					Long id = safeCastLong(msg.getObject());
					if (id != null) {
						applyDeleteFromFull(id);
						rebuildDerivedCatalogsFromFull();
						refreshViewKeepingMode();
					} else {
						fallbackReloadKeepingMode();
					}
					break;
				}

				case "cart_updated":
					showAlert("Success", "Cart updated successfully!");
					break;

				case "cart_data":
					try{
						EventBus.getDefault().unregister(this);
						App.setRoot("cartView");
					}catch (Exception e){
						e.printStackTrace();
					}
					break;

				case "orders_data": {
					@SuppressWarnings("unchecked")
					List<Order> orders = (List<Order>) msg.getObject();
					SessionManager.getInstance().setOrders(orders);
					try {
						EventBus.getDefault().unregister(this);
						App.setRoot("ordersScreenView");
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				}

				case "logout_success":{
					SessionManager.getInstance().logout();
					updateUIBasedOnUserStatus();
					resetToFullCatalogAfterLogout();
					showAlert("Success", "Logged out successfully!");
					break;
				}
				case "inbox_list_error": {
					String err = (msg.getObject() instanceof String) ? (String) msg.getObject() : "Failed to load inbox.";
					showAlert("Inbox Error", err);
					break;
				}
				case "force_logout":{
					showAlert("Alert!", "You have been banned/frozen contact administrator.");
					break;
				}
				case "staff_send_delivery_email_ack": {
					// optional: show result if server sends it
					Object o = msg.getObject();
					if (o instanceof Boolean b && b) {
						showAlert("Email", "Delivery email sent.");
					} else if (o instanceof String s && !s.isBlank()) {
						showAlert("Email", "Email failed: " + s);
					}
					break;
				}

				default:
					System.out.println("Received unhandled message from server: " + msg.getMessage());
					break;
			}
		});
	}


	@SuppressWarnings("unchecked")
	private Catalog safeCast(Object o) {
		try { return (Catalog) o; } catch (Exception ignored) { return null; }
	}

	private void loadCatalogData() {
		loadingIndicator.setVisible(true);
		catalogGrid.getChildren().clear();

		Task<Void> loadTask = new Task<>() {
			@Override
			protected Void call() throws Exception {
				if (!SimpleClient.getClient().isConnected()) {
					SimpleClient.getClient().openConnection();
				}
				SimpleClient.getClient().sendToServer("request_catalog");
				return null;
			}
		};

		loadTask.setOnFailed(e -> {
			loadingIndicator.setVisible(false);
			showAlert("Connection Error", "Failed to connect to the server.");
		});

		new Thread(loadTask).start();
	}

	// =================== Role helpers / UI state ===================

	private boolean canSeeReports() {
		User u = SessionManager.getInstance().getCurrentUser();
		if (u == null) return false;
		if (u instanceof Employee) {
			String role = ((Employee) u).getRole();
			return role.contains("manager");
		}
		return false;
	}

	private boolean canSeeUsers() {
		User u = SessionManager.getInstance().getCurrentUser();
		if(u == null) return false;
		if(u instanceof Employee) {
			String role = ((Employee) u).getRole();
			return role.contains("systemadmin");
		}
		return false;
	}
	private boolean isManager() {
		User u = SessionManager.getInstance().getCurrentUser();
		if (u == null) return false;

		if (u instanceof Employee) {
			try {
				String role = ((Employee) u).getRole();
				return role != null && role.toLowerCase().contains("manager");
			} catch (Exception ignored) { }
		}
		// fallback via reflection
		try {
			Method m = u.getClass().getMethod("getRole");
			Object role = m.invoke(u);
			return role != null && "MANAGER".equalsIgnoreCase(role.toString());
		} catch (Exception ignored) { }
		return false;
	}

	private boolean isCustomerLoggedIn() {
		User u = SessionManager.getInstance().getCurrentUser();
		return (u != null) && !(u instanceof Employee);
	}

	private void updateUIBasedOnUserStatus() {
		boolean isLoggedIn = SessionManager.getInstance().getCurrentUser() != null;
		boolean isEmployee = SessionManager.getInstance().isEmployee();

		loginButton.setVisible(!isLoggedIn);
		logoutButton.setVisible(isLoggedIn);
		userStatusLabel.setVisible(isLoggedIn);
		customerMenuBar.setVisible(isLoggedIn && !isEmployee);
		employeeMenuBar.setVisible(isLoggedIn && isEmployee);

		if (reportButton != null) {
			boolean showReports = isLoggedIn && canSeeReports();
			reportButton.setVisible(showReports);
			reportButton.setManaged(showReports);
		}

        if (inboxButton != null) {
            boolean showInbox = isLoggedIn && !isEmployee;
            inboxButton.setVisible(showInbox);
            inboxButton.setManaged(showInbox);
            refreshInboxBadgeText(); // keeps the count on button label
        }

		if(budgetButton != null) {
			boolean showBudget = isLoggedIn && !isEmployee;
			budgetButton.setVisible(showBudget);
			budgetButton.setManaged(showBudget);
		}

        updateCustomButtonState();

		if (isLoggedIn) {
			userStatusLabel.setText("Hi, " + SessionManager.getInstance().getCurrentUser().getUsername());
		}

        if (broadcastButton != null) {
            broadcastButton.setVisible(isEmployee);
            broadcastButton.setManaged(isEmployee);
        }

		if(usersPageButton != null) {
			boolean showUsers = isLoggedIn && canSeeUsers();
			usersPageButton.setVisible(showUsers);
			usersPageButton.setManaged(showUsers);
		}

    }

	private void updateCustomButtonState() {
		if (makeCustomBouquetButton != null) {
			boolean show = isCustomerLoggedIn();
			makeCustomBouquetButton.setVisible(show);
			makeCustomBouquetButton.setManaged(show);
			makeCustomBouquetButton.setText(customBouquetMode ? "Done Selecting" : "Make a Custom Bouquet");
		}
	}

    // =================== Inbox actions  ===================

    @FXML
    private void handleInbox() {
        var u = SessionManager.getInstance().getCurrentUser();
        if (!(u instanceof il.cshaifasweng.OCSFMediatorExample.entities.Customer)) {
            showAlert("Inbox", "Please login as a customer.");
            return;
        }
        try {
            EventBus.getDefault().unregister(this);
            App.setRoot("InboxView"); // matches the FXML name (without .fxml)
        } catch (IOException e) {
            showAlert("Inbox", "Failed to open inbox.");
        }
    }



    @Subscribe
    public void onInboxMessages(Message msg) {
        switch (msg.getMessage()) {
            case "inbox_list": {
                InboxListDTO payload = (InboxListDTO) msg.getObject();
                java.util.List<InboxItemDTO> personal = (payload == null) ? java.util.List.of() : payload.getPersonal();
                unreadPersonalCount = (int) personal.stream().filter(n -> !n.isRead()).count();
                refreshInboxBadgeText();
                break;
            }
            case "inbox_personal_new": {
                Long targetId = null;
                if (msg.getObjectList() != null && !msg.getObjectList().isEmpty()) {
                    Object v = msg.getObjectList().get(0);
                    if (v instanceof Number) targetId = ((Number) v).longValue();
                }
                var u = SessionManager.getInstance().getCurrentUser();
                if (u instanceof Customer && targetId != null && java.util.Objects.equals(((Customer) u).getId(), targetId)) {
                    unreadPersonalCount = Math.max(0, unreadPersonalCount + 1);
                    refreshInboxBadgeText();
                }
                break;
            }
            case "inbox_read_ack": {
                unreadPersonalCount = Math.max(0, unreadPersonalCount - 1);
                refreshInboxBadgeText();
                break;
            }
            case "inbox_unread_ack": {
                unreadPersonalCount = Math.max(0, unreadPersonalCount + 1);
                refreshInboxBadgeText();
                break;
            }
            default: break;
        }
    }

    @FXML
    private void openEmployeeSchedule() {
        if (!SessionManager.getInstance().isEmployee()) return;
        try {
            EventBus.getDefault().unregister(this);
            App.setRoot("employeeScheduleView"); // new FXML below
        } catch (IOException e) {
            showAlert("Error", "Failed to open schedule.");
        }
    }





    @FXML
    private void handleBroadcast(javafx.event.ActionEvent e) {
        if (!SessionManager.getInstance().isEmployee()) return;

        // Simple dialog with Title + Body
        TextInputDialog titleDlg = new TextInputDialog();
        titleDlg.setTitle("Broadcast");
        titleDlg.setHeaderText("Send announcement to all customers");
        titleDlg.setContentText("Title:");
        Optional<String> t = titleDlg.showAndWait();
        if (t.isEmpty() || t.get().isBlank()) return;

        Dialog<String> bodyDlg = new Dialog<>();
        bodyDlg.setTitle("Broadcast");
        bodyDlg.setHeaderText("Message body");
        TextArea area = new TextArea();
        area.setPrefRowCount(6);
        bodyDlg.getDialogPane().setContent(area);
        bodyDlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        bodyDlg.setResultConverter(btn -> btn==ButtonType.OK ? area.getText() : null);
        Optional<String> b = bodyDlg.showAndWait();
        if (b.isEmpty() || b.get().isBlank()) return;

        try {
            Map<String,Object> payload = java.util.Map.of("title", t.get().trim(), "body", b.get().trim());
            SimpleClient.getClient().sendToServer(new Message("create_broadcast", null, java.util.List.of(payload)));
            showAlert("Broadcast", "Announcement sent.");
        } catch (IOException ex) {
            showAlert("Broadcast", "Failed to send announcement.");
        }
    }

	@FXML
	private void handleUsersPage(javafx.event.ActionEvent e) {
		if(!SessionManager.getInstance().isEmployee()) return;
		if(!canSeeUsers()) return;
		try {
			App.setRoot("AdminUsersView");
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}

    private void refreshInboxBadgeText() {
        if (inboxButton == null) return;
        if (inboxBadge == null) setupInboxBadge();

        if (unreadPersonalCount > 0) {
            inboxBadge.setText(String.valueOf(unreadPersonalCount));
            inboxBadge.setVisible(true);
            inboxBadge.setManaged(true);
        } else {
            inboxBadge.setVisible(false);
            inboxBadge.setManaged(false);
        }
    }


    private void setupInboxBadge() {
        if (inboxButton == null) return;

        // base label (the button's text)
        Label base = new Label("📥 Inbox");

        // the little red bubble
        inboxBadge = new Label();
        inboxBadge.setVisible(false);
        inboxBadge.setManaged(false); // don't affect layout when hidden
        inboxBadge.setStyle(
                "-fx-background-color:#e74c3c;" +
                        "-fx-text-fill:white;" +
                        "-fx-background-radius:10;" +
                        "-fx-padding:1 6;" +
                        "-fx-font-size:10px;" +
                        "-fx-font-weight:bold;"
        );

        StackPane wrap = new StackPane(base, inboxBadge);
        StackPane.setAlignment(inboxBadge, javafx.geometry.Pos.TOP_RIGHT);
        wrap.setMaxWidth(Region.USE_PREF_SIZE);

        inboxButton.setText(null);          // use graphic instead of text
        inboxButton.setGraphic(wrap);
    }



    // =================== Product actions (existing) ===================

	private void handleAddToCart(Product product) {
		User currentUser = SessionManager.getInstance().getCurrentUser();
		if (currentUser == null) {
			showAlert("Login Required", "Please login to add items to your cart.");
			return;
		}
		if(currentUser instanceof Customer) {
			Customer customer = (Customer) currentUser;
			try {
				List<Object> payload = new ArrayList<>();
				payload.add(customer);
				Message message = new Message("add_to_cart", product.getId(), payload);
				SimpleClient.getClient().sendToServer(message);
				showAlert("Success", product.getName() + " was added to your cart!");
			} catch (IOException e) {
				showAlert("Error", "Failed to add item to cart.");
			}
		}
	}

	private void handleViewProduct(Product product) {
		ViewFlowerController.setSelectedFlower(product);
		try {
			System.out.println("Viewing product " + product.getName());
			EventBus.getDefault().unregister(this);
			App.setRoot("viewFlower");
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Error", "Could not open the product page.");
		}
	}


	private void handleDeleteProduct(Product product) {
		Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
		confirmationAlert.setTitle("Confirm Deletion");
		confirmationAlert.setHeaderText("Are you sure you want to delete '" + product.getName() + "'?");
		confirmationAlert.setContentText("This action cannot be undone.");
		Optional<ButtonType> result = confirmationAlert.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK) {
			try {
				Message message = new Message("delete_product", product.getId(), null);
				SimpleClient.getClient().sendToServer(message);
				showAlert("Success", "Delete request for " + product.getName() + " has been sent.");
			} catch (IOException e) {
				showAlert("Error", "Failed to send delete request to server.");
			}
		}
	}

	// =================== Custom bouquet actions ===================

	@FXML
	public void handleMakeCustomBouquet(ActionEvent e) {
		if (SessionManager.getInstance().isEmployee()) {
			return; // button hidden anyway, extra guard
		}

		customBouquetMode = !customBouquetMode;

		if (customBouquetMode) {
			// switch to flowers-only
			if (flowersOnlyCatalog != null) {
				this.catalog = flowersOnlyCatalog;
				populateTypeFilter(flowersOnlyCatalog);
			} else if (fullCatalog != null) {
				Catalog fallback = new Catalog(
						fullCatalog.getFlowers().stream()
								.filter(this::isFlower)
								.collect(Collectors.toList())
				);
				this.catalog = fallback;
				populateTypeFilter(fallback);
			}
			selectedFlowerQuantities.clear();
			showInfoToast("Custom bouquet mode enabled. Use \"Select\" to add stems.");
		} else {
			// return to FULL catalog
			if (fullCatalog != null) {
				this.catalog = fullCatalog;
				populateTypeFilter(fullCatalog);
			} else if (nonFlowerCatalog != null) {
				this.catalog = nonFlowerCatalog; // rare fallback
				populateTypeFilter(nonFlowerCatalog);
			}
			if (!selectedFlowerQuantities.isEmpty()) {
				confirmCustomBouquet();
			}
		}

		updateCustomButtonState();
		renderCatalog();
	}


	private void handleSelectFlower(Product product) {
		selectedFlowerQuantities.merge(product.getId(), 1, Integer::sum);
		updateCustomStatusLabel();
		showInfoToast("Added 1 \"" + product.getName() + "\" (total: " + selectedFlowerQuantities.get(product.getId()) + ")");
	}

	private boolean isFlower(Product p) {
		return p != null && p.getType() != null && p.getType().equalsIgnoreCase("flower");
	}

	private void confirmCustomBouquet() {
		User currentUser = SessionManager.getInstance().getCurrentUser();
		if (!(currentUser instanceof Customer)) {
			showAlert("Login Required", "Please login as a customer to continue.");
			return;
		}

		int totalStems = selectedFlowerQuantities.values().stream().mapToInt(Integer::intValue).sum();
		if (totalStems <= 0) {
			showAlert("Nothing selected", "Please select at least one flower.");
			return;
		}

		// Optional: summarize selection for confirmation
		StringBuilder summary = new StringBuilder("You're about to add a Custom Bouquet with:\n");
		for (Map.Entry<Long, Integer> e : selectedFlowerQuantities.entrySet()) {
			Product p = findProductById(e.getKey());
			if (p != null) {
				summary.append("• ").append(p.getName()).append(" × ").append(e.getValue()).append("\n");
			}
		}
		summary.append("\nContinue?");

		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle("Add Custom Bouquet");
		confirm.setHeaderText("Custom Bouquet (" + totalStems + " stems)");
		confirm.setContentText(summary.toString());
		Optional<ButtonType> result = confirm.showAndWait();
		if (result.isEmpty() || result.get() != ButtonType.OK) {
			return; // user canceled
		}

		// Build bouquet with snapshots and total price
		CustomBouquet bouquet = buildBouquetFromSelection((Customer) currentUser, /*instructions*/ null);

		try {
			// Send to server; mirror your existing add_to_cart shape
			// Payload carries the current user for server-side cart lookup
			List<Object> payload = new ArrayList<>();
			payload.add(currentUser);

			Message message = new Message("add_custom_bouquet_to_cart", /*object*/ bouquet, payload);
			SimpleClient.getClient().sendToServer(message);

			showAlert("Success", "Custom Bouquet was added to your cart!");
		} catch (IOException ex) {
			showAlert("Error", "Failed to add Custom Bouquet to cart.");
			return;
		}

		// Reset selection + mode, return to full catalog view
		selectedFlowerQuantities.clear();
		customBouquetMode = false;
		updateCustomButtonState();
		updateCustomStatusLabel();

		if (fullCatalog != null) {
			this.catalog = fullCatalog;
			populateTypeFilter(fullCatalog);
			renderCatalog();
		} else {
			loadCatalogData();
		}
	}


	/** Find a Product by id, preferring fullCatalog, with safe fallbacks. */
	private Product findProductById(Long id) {
		if (id == null) return null;
		// 1) full catalog
		if (fullCatalog != null && fullCatalog.getFlowers() != null) {
			for (Product p : fullCatalog.getFlowers()) {
				if (p != null && Objects.equals(p.getId(), id)) return p;
			}
		}
		// 2) current catalog
		if (catalog != null && catalog.getFlowers() != null) {
			for (Product p : catalog.getFlowers()) {
				if (p != null && Objects.equals(p.getId(), id)) return p;
			}
		}
		// 3) derived lists
		if (flowersOnlyCatalog != null && flowersOnlyCatalog.getFlowers() != null) {
			for (Product p : flowersOnlyCatalog.getFlowers()) {
				if (p != null && Objects.equals(p.getId(), id)) return p;
			}
		}
		if (nonFlowerCatalog != null && nonFlowerCatalog.getFlowers() != null) {
			for (Product p : nonFlowerCatalog.getFlowers()) {
				if (p != null && Objects.equals(p.getId(), id)) return p;
			}
		}
		return null;
	}

	/** Build a CustomBouquet entity (with snapshot lines) from selectedFlowerQuantities. */
	private CustomBouquet buildBouquetFromSelection(Customer createdBy, String instructions) {
		CustomBouquet bouquet = new CustomBouquet(createdBy, instructions);
		for (Map.Entry<Long, Integer> e : selectedFlowerQuantities.entrySet()) {
			Long productId = e.getKey();
			int qty = Math.max(0, e.getValue());
			if (qty <= 0) continue;

			Product p = findProductById(productId);
			if (p == null) continue; // should not happen, but be safe

			// Create a line that snapshots current name/price
			CustomBouquetItem line = new CustomBouquetItem(p, qty);
			bouquet.addItem(line);
		}
		// ensure price up-to-date
		bouquet.recomputeTotalPrice();
		return bouquet;
	}

	// =================== Alerts / helpers ===================

	private void showAlert(String title, String message) {
		Platform.runLater(() -> {
			Alert alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setTitle(title);
			alert.setHeaderText(null);
			alert.setContentText(message);
			alert.showAndWait();
		});
	}

	private void showInfoToast(String message) {
		System.out.println("[INFO] " + message);
	}

	// =================== Existing navigation handlers (unchanged) ===================

	@FXML
	public void handleCatalog(ActionEvent actionEvent) {
		loadCatalogData();
	}

	@FXML
	public void handleCart(ActionEvent actionEvent) {
		try {
			User currentUser = SessionManager.getInstance().getCurrentUser();
			if (currentUser == null) {
				showAlert("Login Required", "Please login to view your cart.");
				return;
			}
			List<Object> payload = new ArrayList<>();
			payload.add(currentUser);
			Message message = new Message("request_cart", null, payload);
			SimpleClient.getClient().sendToServer(message);
		} catch (IOException e) {
			showAlert("Error", "Failed to request cart.");
		}
	}

	@FXML
	public void handleOrders(ActionEvent actionEvent) {
		try {
			App.setRoot("ordersView");
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "///////Failed to open orders page.");
		}
	}

	@FXML
	public void handleComplaints(ActionEvent actionEvent) {
		try {
			App.setRoot("complaintsList");
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to open complaints page.");
		}
	}

    @FXML
    private void handleOpenComplaints() {
        var u = SessionManager.getInstance().getCurrentUser();
        try {
            if (u instanceof il.cshaifasweng.OCSFMediatorExample.entities.Customer) {
                App.setRoot("MyComplaintsView");   // customer history screen
            } else {
                App.setRoot("complaintsListView"); // employee dashboard
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @FXML
	public void handleProfile(ActionEvent event) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("Profile.fxml"));
			Parent root = loader.load();
			Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
			stage.setScene(new Scene(root));
			stage.show();
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to load profile page.");
		}
	}

	@FXML
	void handleAddProduct(ActionEvent event) {
		try {
			EventBus.getDefault().unregister(this);
			App.setRoot("addProductView");
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FXML
	void handleViewReports(ActionEvent event) {
		try{
			EventBus.getDefault().unregister(this);
			App.setRoot("reportView");
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	@FXML
	void handleManageComplaints(ActionEvent event) {
		try {
			EventBus.getDefault().unregister(this);
			App.setRoot("complaintsList");
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void handleLogout(ActionEvent actionEvent) throws IOException {
		String username = (String) (SessionManager.getInstance().getCurrentUser().getUsername());
		SimpleClient.getClient().sendToServer(new Message("logout",username,null));
		//SessionManager.getInstance().logout();
		//updateUIBasedOnUserStatus();

		//resetToFullCatalogAfterLogout(); // <-- ensures full, unfiltered catalog

		//showAlert("Success", "Logged out successfully!");
	}


	@FXML
	public void handleLogin(ActionEvent actionEvent) {
		try {
			EventBus.getDefault().unregister(this);
			App.setRoot("logInView");
		} catch (IOException e) {
			showAlert("Error", "Failed to open login page.");
		}
	}
	private void resetToFullCatalogAfterLogout() {
		// clear UI filters using your existing handler
		handleClearFilters(null);

		// clear custom bouquet state
		preserveCustomModeOnNextCatalog = false;
		customBouquetMode = false;
		selectedFlowerQuantities.clear();
		updateCustomButtonState();
		updateCustomStatusLabel();

		// point back to full catalog (or reload if we don't have it yet)
		if (fullCatalog != null) {
			this.catalog = fullCatalog;
			populateTypeFilter(fullCatalog);
			renderCatalog();
		} else {
			loadCatalogData(); // will render on server response
		}
	}

	@FXML
	public void handleOrdersScreen(ActionEvent actionEvent) {
		try {
			User currentUser = SessionManager.getInstance().getCurrentUser();
			if (currentUser == null) {
				showAlert("Login Required", "Please login to view your orders.");
				return;
			}
			App.setRoot("ordersScreenView");
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Retrying load of ordersScreemView..");
			try {
				App.setRoot("ordersScreenView");
			} catch(IOException e1) {
				showAlert("Error", "Failed to open orders page.");
			}
		}
	}


	@FXML
	public void handleAddBudget(ActionEvent actionEvent) {
		try {
			User currentUser = SessionManager.getInstance().getCurrentUser();
			if(currentUser == null) {
				showAlert("Login Required", "Please login to access your budget.");
				return;
			}
			App.setRoot("addBudgetView");
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Error", "Failed to open budget page.");
		}
	}
}
