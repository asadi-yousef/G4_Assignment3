package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.AddProductRequest;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import javafx.application.Platform;

import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class AddProductController implements Initializable {

    // FXML Components
    @FXML private TextField nameField;
    @FXML private TextField typeField;
    @FXML private TextField imagePathField;
    @FXML private ColorPicker colorPicker;
    @FXML private TextField priceField;
    @FXML private TextField discountField;
    @FXML private Button browseButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Label statusLabel;
    @FXML private Label discountInfoLabel;

    // Callbacks (optional)
    private Consumer<Product> onProductSaved;
    private Runnable onCancel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupValidation();
        setupDiscountListener();
        colorPicker.setValue(Color.WHITE);
        if (discountField != null && discountField.getText().isBlank()) {
            discountField.setText("0");
        }
    }

    public void setCallbacks(Consumer<Product> onProductSaved, Runnable onCancel) {
        this.onProductSaved = onProductSaved;
        this.onCancel = onCancel;
    }

    public void setProductData(Product product) {
        if (product != null) {
            nameField.setText(product.getName());
            typeField.setText(product.getType());
            imagePathField.setText(product.getImagePath());
            try {
                colorPicker.setValue(Color.web(product.getColor()));
            } catch (Exception e) {
                colorPicker.setValue(Color.WHITE);
            }
            priceField.setText(String.format("%.2f", product.getPrice()));
            discountField.setText(String.valueOf(product.getDiscountPercentage()));
            saveButton.setText("Update Product");
        }
    }

    @FXML
    private void handleBrowseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Product Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPG Files", "*.jpg", "*.jpeg")
        );

        Stage stage = (Stage) browseButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            imagePathField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleSaveProduct() {
        if (!validateForm()) return;

        saveButton.setDisable(true);
        showStatus("Creating product...", false);

        Task<Product> createProductTask = new Task<>() {
            @Override
            protected Product call() throws Exception {
                // Build product (meta only; no copying to resources)
                Product product = new Product();
                product.setName(nameField.getText().trim());
                product.setType(typeField.getText().trim());
                product.setPrice(Double.parseDouble(priceField.getText().trim()));

                double discount = 0.0;
                String d = discountField.getText() == null ? "" : discountField.getText().trim();
                if (!d.isEmpty()) {
                    try { discount = Double.parseDouble(d); } catch (NumberFormatException ignored) {}
                }
                discount = Math.max(0, Math.min(100, discount));
                product.setDiscountPercentage(discount);

                product.setColor(toHexString(colorPicker.getValue()));
                product.setImagePath(null); // stop using classpath path for new items
                product.setImagePath(null);   // will be filled by server if an image exists

                // Pack the selected image as bytes (optional)
                byte[] imageBytes = null;
                String imageName  = null;
                String chosen = imagePathField.getText() == null ? "" : imagePathField.getText().trim();
                if (!chosen.isEmpty()) {
                    File f = new File(chosen);
                    if (f.exists()) {
                        imageName = f.getName();
                        try (InputStream in = new FileInputStream(f);
                             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            in.transferTo(baos);
                            imageBytes = baos.toByteArray();
                        }
                    }
                }

                // Send one message: product meta + (optional) image bytes + original name
                AddProductRequest req = new AddProductRequest();
                req.productMeta = product;
                req.imageBytes  = imageBytes;   // may be null
                req.imageName   = imageName;    // may be null

                Message msg = new Message("add_product", req, null);
                SimpleClient.getClient().sendToServer(msg);

                return product;
            }
        };

        createProductTask.setOnSucceeded(e -> {
            showStatus("Product create request sent.", false);
            if (onProductSaved != null) onProductSaved.accept(createProductTask.getValue());
            clearForm();
            saveButton.setDisable(false);
        });

        createProductTask.setOnFailed(e -> {
            Throwable ex = createProductTask.getException();
            showStatus("Failed to create product: " + (ex != null ? ex.getMessage() : "Unknown error"), true);
            saveButton.setDisable(false);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error Creating Product");
            alert.setHeaderText("Failed to create the product");
            alert.setContentText(ex != null ? ex.getMessage() : "Unknown error");
            alert.showAndWait();
        });

        Thread thread = new Thread(createProductTask);
        thread.setDaemon(true);
        thread.start();
    }


    private void sendProductToServer(Product product) throws Exception {
        try {
            Message msg = new Message("add_product", product, null);
            SimpleClient.getClient().sendToServer(msg);
            System.out.println("DEBUG: Sending product to server: " + product.getName());
            System.out.println("DEBUG: Product save request sent to server");
        } catch (Exception e) {
            System.err.println("Error sending product to server: " + e.getMessage());
            throw new Exception("Failed to save product to server: " + e.getMessage(), e);
        }
    }

    @FXML
    private void handleCancel() {
        if (hasFormData()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Cancel");
            confirmAlert.setHeaderText("Discard Changes?");
            confirmAlert.setContentText("You have unsaved changes. Are you sure you want to cancel?");

            if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        clearForm();
        if (onCancel != null) {
            onCancel.run();
        }
        try {
            App.setRoot("primary");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        if (nameField.getText().trim().isEmpty()) {
            errors.append("• Product name is required\n");
        }
        if (typeField.getText().trim().isEmpty()) {
            errors.append("• Product type is required\n");
        }
        if (priceField.getText().trim().isEmpty()) {
            errors.append("• Price is required\n");
        } else {
            try {
                double price = Double.parseDouble(priceField.getText().trim());
                if (price < 0) {
                    errors.append("• Price must be positive\n");
                }
            } catch (NumberFormatException e) {
                errors.append("• Price must be a valid number\n");
            }
        }

        // Discount is optional; if provided, validate the range
        String d = discountField.getText() == null ? "" : discountField.getText().trim();
        if (!d.isEmpty()) {
            try {
                double discount = Double.parseDouble(d);
                if (discount < 0 || discount > 100) {
                    errors.append("• Discount must be between 0 and 100\n");
                }
            } catch (NumberFormatException e) {
                errors.append("• Discount must be a valid number\n");
            }
        }

        if (errors.length() > 0) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Please correct the following errors:");
            alert.setContentText(errors.toString());
            alert.showAndWait();
            return false;
        }
        return true;
    }

    // ==== OLD IMAGE HANDLING LOGIC, adapted here ====
    private Product createProductFromForm() {
        Product product = new Product();

        // Standard fields
        product.setName(nameField.getText().trim());
        product.setType(typeField.getText().trim());
        product.setPrice(Double.parseDouble(priceField.getText().trim()));

        // Discount: default to 0 if empty, clamp to [0, 100]
        double discount = 0.0;
        String d = discountField.getText() == null ? "" : discountField.getText().trim();
        if (!d.isEmpty()) {
            try {
                discount = Double.parseDouble(d);
            } catch (NumberFormatException ignored) {
                discount = 0.0;
            }
        }
        discount = Math.max(0, Math.min(100, discount));
        product.setDiscountPercentage(discount);

        // Color
        product.setColor(toHexString(colorPicker.getValue()));

        // ---- EXACT old image logic starts here ----
        String originalPath = imagePathField.getText() == null ? "" : imagePathField.getText().trim();
        if (!originalPath.isEmpty()) {
            File sourceFile = new File(originalPath);
            if (sourceFile.exists()) {
                try {
                    // Destination directory in resources
                    File destDir = new File("src/main/resources/il/cshaifasweng/OCSFMediatorExample/client/images");
                    if (!destDir.exists()) destDir.mkdirs();

                    // Use original filename (or a unique one if you prefer later)
                    String fileName = sourceFile.getName();
                    File destFile = new File(destDir, fileName);

                    // Copy file (byte buffer, as in your old code)
                    try (InputStream in = new FileInputStream(sourceFile);
                         OutputStream out = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                    }

                    // Save only the relative CLASSPATH path
                    product.setImagePath("/il/cshaifasweng/OCSFMediatorExample/client/images/" + fileName);
                    showStatus("Image copied successfully", false);
                } catch (IOException e) {
                    e.printStackTrace();
                    showStatus("Failed to copy image file", true);
                    product.setImagePath(null);
                }
            } else {
                showStatus("Image file does not exist", true);
                product.setImagePath(null);
            }
        } else {
            product.setImagePath(null);
        }
        // ---- old image logic ends here ----

        return product;
    }
    // ==============================================

    private void setupValidation() {
        nameField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.trim().isEmpty()) {
                nameField.setStyle("-fx-border-color: red;");
            } else {
                nameField.setStyle("");
            }
        });

        priceField.textProperty().addListener((obs, oldText, newText) -> {
            try {
                if (!newText.trim().isEmpty()) {
                    double price = Double.parseDouble(newText.trim());
                    if (price < 0) {
                        priceField.setStyle("-fx-border-color: red;");
                    } else {
                        priceField.setStyle("");
                    }
                } else {
                    priceField.setStyle("");
                }
            } catch (NumberFormatException e) {
                priceField.setStyle("-fx-border-color: red;");
            }
        });
    }

    private void setupDiscountListener() {
        discountField.textProperty().addListener((obs, oldText, newText) -> {
            try {
                if (!newText.trim().isEmpty()) {
                    double discount = Double.parseDouble(newText.trim());
                    if (discount > 0 && discount <= 100) {
                        discountInfoLabel.setText("(" + String.format("%.0f", discount) + "% off)");
                        discountInfoLabel.setTextFill(Color.GREEN);
                    } else if (discount == 0) {
                        discountInfoLabel.setText("(No discount)");
                        discountInfoLabel.setTextFill(Color.GRAY);
                    } else {
                        discountInfoLabel.setText("(Invalid discount)");
                        discountInfoLabel.setTextFill(Color.RED);
                    }
                } else {
                    discountInfoLabel.setText("(0 for no discount)");
                    discountInfoLabel.setTextFill(Color.GRAY);
                }
            } catch (NumberFormatException e) {
                discountInfoLabel.setText("(Invalid number)");
                discountInfoLabel.setTextFill(Color.RED);
            }
        });
    }

    private boolean hasFormData() {
        return !nameField.getText().trim().isEmpty() ||
                !typeField.getText().trim().isEmpty() ||
                !imagePathField.getText().trim().isEmpty() ||
                !priceField.getText().trim().isEmpty() ||
                !(discountField.getText() != null && discountField.getText().trim().equals("0"));
    }

    private void clearForm() {
        nameField.clear();
        typeField.clear();
        imagePathField.clear();
        priceField.clear();
        discountField.setText("0");
        colorPicker.setValue(Color.WHITE);
        statusLabel.setVisible(false);

        nameField.setStyle("");
        priceField.setStyle("");
        discountField.setStyle("");
    }

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setTextFill(isError ? Color.RED : Color.GREEN);
            statusLabel.setVisible(true);

            if (!isError) {
                Task<Void> hideTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(3000);
                        return null;
                    }
                };
                hideTask.setOnSucceeded(e -> statusLabel.setVisible(false));
                new Thread(hideTask).start();
            }
        });
    }

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
