package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

public class ProductCardController {

    // FXML Variables
    @FXML private VBox displayVBox;
    @FXML private VBox editVBox;
    @FXML private ImageView productImageView;
    @FXML private Label nameLabel;
    @FXML private Label typeLabel;
    @FXML private Rectangle colorDisplayRect;

    @FXML private HBox  priceBox;
    @FXML private Label priceLabel;     // <-- wired to the FXML label

    @FXML private HBox buttonBox;
    @FXML private Label saleBadge;

    private Product currentProduct;

    // allows caller to override the second button label (defaults to "Add to Cart")
    private String secondaryActionLabel = "Add to Cart";

    /** Per-user cache dir where we save server-sent image bytes. */
    private static final File LOCAL_IMAGE_CACHE_DIR =
            new File(System.getProperty("user.home"), ".ocsf_app/images").getAbsoluteFile();

    private static final NumberFormat CURRENCY =
            NumberFormat.getCurrencyInstance(new Locale("he", "IL")); // ₪ formatting

    @FXML
    public void initialize() {
        if (editVBox != null) {
            editVBox.setVisible(false);
            editVBox.setManaged(false);
        }
        if (productImageView != null) {
            productImageView.setCache(false); // avoid node-level caching glitches
        }
        try {
            if (!LOCAL_IMAGE_CACHE_DIR.exists()) LOCAL_IMAGE_CACHE_DIR.mkdirs();
        } catch (Exception ignored) {}
    }

    private void populateDisplayData() {
        if (currentProduct == null) return;

        nameLabel.setText(currentProduct.getName());
        typeLabel.setText("Type: " + currentProduct.getType());

        // -------- IMAGE (prefer per-user cache; then PrimaryController bytes; then path) --------
        boolean imageSet = false;

        try {
            // A) Try per-user cache FIRST (so an edited image shows immediately)
            String fileName = fileNameFromPath(currentProduct.getImagePath());
            if (fileName == null || fileName.isBlank()) {
                fileName = currentProduct.getId() + ".img";
            }
            File cached = new File(LOCAL_IMAGE_CACHE_DIR, fileName);
            if (cached.exists()) {
                try (InputStream is = new FileInputStream(cached)) {
                    productImageView.setImage(null); // bust previous reference
                    productImageView.setImage(new Image(is));
                    imageSet = true;
                } catch (Exception ignored) {}
            }

            // B) If cache not available, try bytes from PrimaryController
            if (!imageSet && currentProduct.getId() != null &&
                    PrimaryController.hasImageBytes(currentProduct.getId())) {
                byte[] data = PrimaryController.getImageBytes(currentProduct.getId());
                if (data != null && data.length > 0) {
                    // persist to cache with the same filename so future loads are cache-first
                    try (OutputStream os = new FileOutputStream(cached)) { os.write(data); } catch (Exception ignored) {}
                    try (InputStream is = new ByteArrayInputStream(data)) {
                        productImageView.setImage(null); // bust previous reference
                        productImageView.setImage(new Image(is));
                        imageSet = true;
                    }
                }
            }
        } catch (Exception ignore) {}

// C) Final fallback: resolve by path (this already checks per-user cache first internally)
        if (!imageSet) {
            setImageFromPath(currentProduct.getImagePath());
        }


        // 2) If we didn’t get bytes directly, resolve via path (which now checks per-user cache FIRST)
        if (!imageSet) {
            setImageFromPath(currentProduct.getImagePath());
        }

        // Color
        try {
            colorDisplayRect.setFill(Color.web(currentProduct.getColor()));
        } catch (Exception e) {
            colorDisplayRect.setFill(Color.GRAY);
        }

        // -------- PRICE (reuses FXML priceLabel) --------
        priceBox.getChildren().clear();
        priceLabel.setVisible(true);
        priceLabel.setManaged(true);

        BigDecimal price = currentProduct.getPrice();
        double discountPct = currentProduct.getDiscountPercentage();
        boolean discounted = discountPct > 0 && discountPct <= 100;

        if (discounted) {
            // Old price
            Text oldPriceText = new Text(CURRENCY.format(price));
            oldPriceText.setStrikethrough(true);
            oldPriceText.setStyle("-fx-fill: #7f8c8d;");

            // Sale price
            BigDecimal salePrice = currentProduct.getSalePrice();
            priceLabel.setText(formatCurrency(salePrice));
            priceLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b;");
            priceLabel.setFont(new Font("Bell MT", 16));

            priceBox.getChildren().addAll(oldPriceText, priceLabel);
            saleBadge.setVisible(true);
            saleBadge.setManaged(true);
        } else {
            priceLabel.setText(formatCurrency(price));
            priceLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
            priceLabel.setFont(new Font("Bell MT", 16));
            priceBox.getChildren().add(priceLabel);
            saleBadge.setVisible(false);
            saleBadge.setManaged(false);
        }
    }

    /**
     * Load an image robustly from:
     * - per-user cache (~/.ocsf_app/images/<file>)
     * - server-relative "images/<file>"
     * - file: URL (via InputStream for freshness)
     * - absolute filesystem path
     * - classpath resource (for seeded images)
     *
     * NOTE: http/https loading intentionally removed per your request.
     */
    private void setImageFromPath(String path) {
        if (path == null || path.isBlank()) {
            productImageView.setImage(null);
            return;
        }

        Image img = null;
        try {
            String fileName = fileNameFromPath(path);

            // A) ALWAYS check per-user cache first (this is the key change)
            if (fileName != null) {
                File cached = new File(LOCAL_IMAGE_CACHE_DIR, fileName);
                if (cached.exists()) {
                    try (InputStream is = new FileInputStream(cached)) {
                        img = new Image(is);
                    }
                }
            }

            // B) Server-relative images/ path: nothing else to do if cache already gave us the image
            if (img == null && (path.startsWith("images/") || path.startsWith("\\images\\") || path.startsWith("/images/"))) {
                // If not in cache, nothing more to load (we intentionally avoid http)
                // Fall through to file:/abs/classpath checks
            }

            // C) file: URL
            if (img == null && path.startsWith("file:")) {
                try {
                    File f = new File(java.net.URI.create(path));
                    if (f.exists()) {
                        try (InputStream is = new FileInputStream(f)) { img = new Image(is); }
                    } else {
                        System.err.println("file: URL does not exist: " + path);
                    }
                } catch (IllegalArgumentException badUri) {
                    // ignore (no http usage)
                }
            }

            // D) Absolute filesystem path
            if (img == null) {
                File f = new File(path);
                if (f.isAbsolute() && f.exists()) {
                    try (InputStream is = new FileInputStream(f)) { img = new Image(is); }
                }
            }

            // E) Classpath resource (e.g., "/il/.../client/images/foo.png")
            if (img == null && path.startsWith("/")) {
                URL res = getClass().getResource(path);
                if (res != null) {
                    img = new Image(res.toExternalForm(), true);
                } else {
                    System.err.println("Classpath resource not found: " + path);
                }
            }

        } catch (Exception e) {
            System.err.println("Error loading image '" + path + "': " + e);
        }

        if (img != null) {
            // Force a refresh even if JavaFX thinks it’s the same image
            productImageView.setImage(null);
            productImageView.setImage(img);
        } else {
            System.err.println("Failed to resolve image: " + path);
            productImageView.setImage(null);
        }

    }

    // === helpers ===

    /** Extracts the trailing file name from paths like "images/uuid.ext" or "C:\\...\\name.png". */
    private static String fileNameFromPath(String path) {
        if (path == null) return null;
        String norm = path.replace('\\', '/');
        int i = norm.lastIndexOf('/');
        return (i >= 0 && i < norm.length() - 1) ? norm.substring(i + 1) : norm;
    }

    /**
     * setData method for an Employee.
     */
    public void setData(Product product, Consumer<Product> saveAction, Runnable deleteAction) {
        this.currentProduct = product;
        populateDisplayData();
        buttonBox.getChildren().clear();

        Button editButton = new Button("Edit");
        editButton.setOnAction(e -> switchToEditMode(saveAction));

        Button deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white;");
        deleteButton.setOnAction(e -> deleteAction.run());

        buttonBox.getChildren().addAll(editButton, deleteButton);
    }

    /**
     * setData method for a Customer.
     */
    public void setData(Product product, Runnable viewAction, Runnable addToCartAction) {
        this.currentProduct = product;
        populateDisplayData();
        buttonBox.getChildren().clear();

        Button viewButton = new Button("View");
        viewButton.setOnAction(e -> viewAction.run());

        Button secondaryButton = new Button(secondaryActionLabel);
        secondaryButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        secondaryButton.setOnAction(e -> addToCartAction.run());

        buttonBox.getChildren().addAll(viewButton, secondaryButton);
        secondaryActionLabel = "Add to Cart"; // reset for next call
    }

    // Convenience overload (e.g., to show "Select")
    public void setData(Product product, Runnable viewAction, Runnable secondaryAction, String secondaryLabel) {
        setSecondaryActionLabel(secondaryLabel != null && !secondaryLabel.isEmpty() ? secondaryLabel : "Add to Cart");
        setData(product, viewAction, secondaryAction);
    }

    public void setSecondaryActionLabel(String text) {
        this.secondaryActionLabel = (text != null && !text.isEmpty()) ? text : "Add to Cart";
    }

    /** Show the add-product form with empty fields. */
    public void showAddProductForm(Consumer<Product> saveAction, Runnable cancelAction) {
        this.currentProduct = null;
        if (displayVBox != null) {
            displayVBox.setVisible(false);
            displayVBox.setManaged(false);
        }
        editVBox.setVisible(true);
        editVBox.setManaged(true);
        buildEditForm(saveAction, cancelAction, true);
    }

    private void switchToEditMode(Consumer<Product> saveAction) {
        displayVBox.setVisible(false);
        displayVBox.setManaged(false);
        editVBox.setVisible(true);
        editVBox.setManaged(true);
        buildEditForm(saveAction, this::switchToDisplayMode, false);
    }

    /**
     * Builds the edit form UI with the specified save and cancel actions.
     */
    private void buildEditForm(Consumer<Product> saveAction, Runnable cancelAction, boolean isNewProduct) {
        editVBox.getChildren().clear();
        editVBox.setPadding(new Insets(10));
        editVBox.setSpacing(10);

        TextField nameField = new TextField(isNewProduct ? "" : currentProduct.getName());
        TextField typeField = new TextField(isNewProduct ? "" : currentProduct.getType());

        // Price is BigDecimal – format safely
        String priceInit = "";
        if (!isNewProduct && currentProduct.getPrice() != null) {
            priceInit = currentProduct.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        TextField priceField = new TextField(isNewProduct ? "" : priceInit);

        // Discount is double
        TextField discountField = new TextField(isNewProduct ? "0" : String.valueOf(currentProduct.getDiscountPercentage()));

        ColorPicker colorPicker = new ColorPicker(isNewProduct ? Color.WHITE : Color.web(currentProduct.getColor()));
        TextField imagePathField = new TextField(isNewProduct ? "" : currentProduct.getImagePath());

        if (isNewProduct) {
            nameField.setPromptText("Enter product name");
            typeField.setPromptText("Enter product type");
            priceField.setPromptText("Enter price (e.g., 29.99)");
            discountField.setPromptText("Enter discount percentage (0-100)");
            imagePathField.setPromptText("Select or enter image path");
        }

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Product Image");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            Stage stage = (Stage) browseButton.getScene().getWindow();
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                imagePathField.setText(file.getAbsolutePath());
            }
        });

        HBox imagePathBox = new HBox(5, imagePathField, browseButton);

        Button saveButton = new Button(isNewProduct ? "Create Product" : "Save");
        saveButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

        Button cancelButton = new Button("Cancel");
        HBox actionButtons = new HBox(10, saveButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(10);
        grid.add(new Label("Name:"), 0, 0);       grid.add(nameField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);       grid.add(typeField, 1, 1);
        grid.add(new Label("Image Path:"), 0, 2); grid.add(imagePathBox, 1, 2);
        grid.add(new Label("Color:"), 0, 3);      grid.add(colorPicker, 1, 3);
        grid.add(new Label("Price:"), 0, 4);      grid.add(priceField, 1, 4);
        grid.add(new Label("Discount %:"), 0, 5); grid.add(discountField, 1, 5);

        editVBox.getChildren().addAll(grid, actionButtons);

        cancelButton.setOnAction(e -> cancelAction.run());

        saveButton.setOnAction(e -> {
            try {
                Product productToSave = isNewProduct ? new Product() : currentProduct;

                if (nameField.getText().trim().isEmpty()) {
                    new Alert(Alert.AlertType.ERROR, "Product name is required.").showAndWait();
                    return;
                }
                if (priceField.getText().trim().isEmpty()) {
                    new Alert(Alert.AlertType.ERROR, "Price is required.").showAndWait();
                    return;
                }

                productToSave.setName(nameField.getText().trim());
                productToSave.setType(typeField.getText().trim());

                // Price stays BigDecimal
                productToSave.setPrice(new BigDecimal(priceField.getText().trim()));

                // ---- Discount as double ----
                double discount = 0.0;
                String d = discountField.getText() == null ? "" : discountField.getText().trim();
                if (!d.isEmpty()) {
                    try {
                        d = d.replace("%", "")
                                .replace(",", ".")
                                .replaceAll("[^0-9.]", "");
                        if (!d.isEmpty()) discount = Double.parseDouble(d);
                    } catch (NumberFormatException ignored) {
                        discount = 0.0;
                    }
                }
                if (discount < 0.0) discount = 0.0;
                if (discount > 100.0) discount = 100.0;
                productToSave.setDiscountPercentage(discount);

                productToSave.setColor(toHexString(colorPicker.getValue()));

                // IMAGE PATH handling (best-effort)
                String pathFromField = imagePathField.getText() == null ? "" : imagePathField.getText().trim();
                if (pathFromField.isEmpty()) {
                    if (isNewProduct) {
                        productToSave.setImagePath("");
                    } else {
                        productToSave.setImagePath(currentProduct.getImagePath());
                    }
                } else if (pathFromField.startsWith("file:")) {
                    productToSave.setImagePath(pathFromField);
                } else if (pathFromField.startsWith("/")) {
                    productToSave.setImagePath(pathFromField);
                } else {
                    File sourceFile = new File(pathFromField);
                    if (sourceFile.exists()) {
                        productToSave.setImagePath(sourceFile.getAbsolutePath());
                    } else {
                        productToSave.setImagePath(pathFromField);
                    }
                }

                // Send to server
                saveAction.accept(productToSave);

                // On edit, update in-card model and re-render immediately
                if (!isNewProduct) {
                    this.currentProduct = productToSave;
                    switchToDisplayMode();
                }

            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Price and Discount must be valid numbers.").showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save product: " + ex.getMessage()).showAndWait();
            }
        });
    }

    private void switchToDisplayMode() {
        populateDisplayData();
        editVBox.setVisible(false);
        editVBox.setManaged(false);
        displayVBox.setVisible(true);
        displayVBox.setManaged(true);
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) return "0.00";
        return "₪" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
