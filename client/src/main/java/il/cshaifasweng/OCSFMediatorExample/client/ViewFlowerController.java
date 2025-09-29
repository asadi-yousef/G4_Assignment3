package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class ViewFlowerController {

    @FXML private BorderPane mainPane;
    @FXML private HBox buttonContainer; // kept for FXML compat; not used

    private static Product selectedProduct;

    // Per-page cache bookkeeping: which files we wrote so we can delete them on Back
    private static final File LOCAL_IMAGE_CACHE_DIR =
            new File(System.getProperty("user.home"), ".ocsf_app/images").getAbsoluteFile();
    private final Set<File> filesWeWrote =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<File, Boolean>());

    private ProductCardController cardController;
    private StackPane cardNode;


    public static void setSelectedFlower(Product product) {
        selectedProduct = product;
    }

    @FXML
    public void initialize() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        ensureCacheDir();

        if (selectedProduct == null) {
            System.err.println("ViewFlowerController: No product was selected.");
            return;
        }
        renderCard(selectedProduct);
    }

    private void renderCard(Product product) {
        if (product == null) return;
        try {
            if (cardController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("productCard.fxml"));
                cardNode = loader.load();
                cardController = loader.getController();
                mainPane.setCenter(cardNode);
            }
            // Customer actions
            Runnable viewAction = () -> {}; // already here
            Runnable addToCartAction = () -> handleAddToCart(product);

            // Rebind data (price/discount/image all refresh inside the card)
            cardController.setData(product, viewAction, addToCartAction);

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Fatal Error", "Could not load the ProductCard component.");
        }
    }

    @Subscribe
    public void onMessageFromServer(Message message) {
        if (message == null) return;
        if (!"editProduct".equals(message.getMessage())) return;

        Object obj = message.getObject();
        if (!(obj instanceof Product)) return;
        Product edited = (Product) obj;

        if (selectedProduct == null || edited.getId() == null) return;
        if (!Objects.equals(edited.getId(), selectedProduct.getId())) return;

        // 1) If server sent image bytes, write them to ~/.ocsf_app/images/<fileName>
        byte[] bytes = extractImageBytesFromMessage(message);
        if (bytes != null && bytes.length > 0) {
            writeBytesToPerUserCache(edited, bytes);
        }

        // 2) Update reference & re-render the card (ProductCardController will pick the cached file)
        selectedProduct = edited;
        Platform.runLater(() -> renderCard(edited));
    }


    /** Writes image bytes to ~/.ocsf_app/images/<fileName-from-imagePath or fallback> */
    private void writeBytesToPerUserCache(Product product, byte[] data) {
        ensureCacheDir();
        String fileName = fileNameFromPath(product.getImagePath());
        if (fileName == null || fileName.isBlank()) {
            fileName = product.getId() + ".img";
        }
        File out = new File(LOCAL_IMAGE_CACHE_DIR, fileName);
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(data);
            filesWeWrote.add(out); // <-- add this line so handleBack() can delete it
        } catch (IOException e) {
            System.err.println("[ViewFlowerController] Failed writing cache image " + out + ": " + e);
        }
    }


    /** Robustly pull imageBytes from Message payload via reflection (works whether it's getObjectList() or getPayload()) */
    @SuppressWarnings("unchecked")
    private byte[] extractImageBytesFromMessage(Message msg) {
        // Try getObjectList()
        byte[] fromList = tryExtractFromList(invokeListGetter(msg, "getObjectList"));
        if (fromList != null) return fromList;
        // Try getPayload()
        return tryExtractFromList(invokeListGetter(msg, "getPayload"));
    }

    private List<?> invokeListGetter(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object val = m.invoke(target);
            if (val instanceof List<?>) return (List<?>) val;
        } catch (Exception ignored) {}
        return null;
    }

    private byte[] tryExtractFromList(List<?> list) {
        if (list == null) return null;
        for (Object entry : list) {
            if (entry instanceof Map<?, ?>) {
                Object bytesObj = ((Map<?, ?>) entry).get("imageBytes");
                if (bytesObj instanceof byte[]) {
                    return (byte[]) bytesObj;
                }
            }
        }
        return null;
    }

    private static void ensureCacheDir() {
        try {
            if (!LOCAL_IMAGE_CACHE_DIR.exists()) LOCAL_IMAGE_CACHE_DIR.mkdirs();
        } catch (Exception ignored) {}
    }

    /** Extract trailing file name from "images/uuid.ext" or "C:\\...\\name.png" */
    private static String fileNameFromPath(String path) {
        if (path == null) return null;
        String norm = path.replace('\\', '/');
        int i = norm.lastIndexOf('/');
        return (i >= 0 && i < norm.length() - 1) ? norm.substring(i + 1) : norm;
    }

    private void handleAddToCart(Product product) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            showAlert("Login Required", "Please login to add items to your cart.");
            return;
        }
        try {
            List<Object> payload = new ArrayList<>();
            payload.add(currentUser);
            Message message = new Message("add_to_cart", product.getId(), payload);
            SimpleClient.getClient().sendToServer(message);
            showAlert("Success", product.getName() + " was added to your cart!");
        } catch (IOException e) {
            showAlert("Error", "Failed to add item to cart.");
        }
    }

    @FXML
    private void handleBack() {
        try {
            // Clean up images we wrote for this page only
            for (File f : filesWeWrote) {
                try { if (f != null && f.exists()) f.delete(); } catch (Exception ignored) {}
            }
            filesWeWrote.clear();

            if (EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().unregister(this);
            }
            App.setRoot("primary");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
