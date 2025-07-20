package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.*;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class ManagerView implements Initializable {
    private Catalog catalog;
    private ObservableList<Product> productList;
    private FilteredList<Product> filteredProducts;
    private Product selectedProduct;
    private boolean isEditMode = false;

    // FXML Components
    @FXML private TextField searchField;
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Long> idColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, Double> priceColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, String> imagePathColumn;

    @FXML private TextField productIdField;
    @FXML private TextField productNameField;
    @FXML private TextField productPriceField;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private TextField imagePathField;

    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;
    @FXML private Button browseImageButton;

    @FXML private Label statusLabel;
    @FXML private Label totalProductsLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Register for EventBus
        EventBus.getDefault().register(this);

        // Initialize collections
        productList = FXCollections.observableArrayList();
        filteredProducts = new FilteredList<>(productList, p -> true);

        // Setup table columns
        setupTableColumns();

        // Setup table selection listener
        setupTableSelectionListener();

        // Setup search functionality
        setupSearchFilter();

        // Setup category combo box
        setupCategoryComboBox();

        // Connect to server and request catalog
        try {
            if (!SimpleClient.getClient().isConnected()) {
                SimpleClient.getClient().openConnection();
            }
            SimpleClient.getClient().sendToServer("request_catalog");
            updateStatus("Requesting catalog from server...");
        } catch (IOException e) {
            e.printStackTrace();
            updateStatus("Error: Could not connect to server");
        }
    }

    private void setupTableColumns() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        imagePathColumn.setCellValueFactory(new PropertyValueFactory<>("imagePath"));

        // Format price column to show currency
        priceColumn.setCellFactory(col -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", price));
                }
            }
        });

        // Format image path column to show only filename
        imagePathColumn.setCellFactory(col -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String imagePath, boolean empty) {
                super.updateItem(imagePath, empty);
                if (empty || imagePath == null || imagePath.isEmpty()) {
                    setText("No image");
                } else {
                    // Show only filename, not full path
                    String fileName = new File(imagePath).getName();
                    setText(fileName.isEmpty() ? "No image" : fileName);
                }
            }
        });

        productTable.setItems(filteredProducts);
    }

    private void setupTableSelectionListener() {
        productTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedProduct = newSelection;
                populateForm(newSelection);
                editButton.setDisable(false);
                deleteButton.setDisable(false);
            } else {
                selectedProduct = null;
                editButton.setDisable(true);
                deleteButton.setDisable(true);
            }
        });
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredProducts.setPredicate(product -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();
                return product.getName().toLowerCase().contains(lowerCaseFilter) ||
                        product.getType().toLowerCase().contains(lowerCaseFilter) ||
                        String.valueOf(product.getId()).contains(lowerCaseFilter);
            });
            updateProductCount();
        });
    }

    private void setupCategoryComboBox() {
        categoryComboBox.setItems(FXCollections.observableArrayList(
                "Electronics", "Clothing", "Books", "Home & Garden",
                "Sports", "Toys", "Food", "Health", "Other"
        ));
    }

    @Subscribe
    public void onMessage(Message message) {
        Platform.runLater(() -> {
            if (message.getMessage().equals("catalog")) {
                catalog = (Catalog) message.getObject();
                renderCatalog();
                updateStatus("Catalog loaded successfully");
            } else if (message.getMessage().equals("product_added")) {
                updateStatus("Product added successfully");
                refreshCatalog();
            } else if (message.getMessage().equals("product_updated")) {
                updateStatus("Product updated successfully");
                refreshCatalog();
            } else if (message.getMessage().equals("product_deleted")) {
                updateStatus("Product deleted successfully");
                refreshCatalog();
            } else if (message.getMessage().equals("error")) {
                updateStatus("Error: " + message.getObject().toString());
            }
        });
    }

    private void renderCatalog() {
        if (catalog != null && catalog.getFlowers() != null) {
            productList.clear();
            productList.addAll(catalog.getFlowers());
            updateProductCount();
        }
    }

    private void populateForm(Product product) {
        productIdField.setText(String.valueOf(product.getId()));
        productNameField.setText(product.getName());
        productPriceField.setText(String.valueOf(product.getPrice()));
        categoryComboBox.setValue(product.getType());
        imagePathField.setText(product.getImagePath() != null ? product.getImagePath() : "");
    }

    private void clearForm() {
        productIdField.clear();
        productNameField.clear();
        productPriceField.clear();
        categoryComboBox.setValue(null);
        imagePathField.clear();
        selectedProduct = null;
        isEditMode = false;
        saveButton.setDisable(true);
        addButton.setDisable(false);
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void updateProductCount() {
        totalProductsLabel.setText("Total Products: " + filteredProducts.size());
    }

    private void refreshCatalog() {
        try {
            SimpleClient.getClient().sendToServer("request_catalog");
        } catch (IOException e) {
            e.printStackTrace();
            updateStatus("Error refreshing catalog");
        }
    }

    // Event Handlers
    @FXML
    public void handleSearch(ActionEvent actionEvent) {
        // Search is handled automatically by the text field listener
        updateStatus("Search applied");
    }

    @FXML
    public void handleClearSearch(ActionEvent actionEvent) {
        searchField.clear();
        updateStatus("Search cleared");
    }

    @FXML
    public void handleAddProduct(ActionEvent actionEvent) {
        try {
            if (validateForm()) {
                Product newProduct = createProductFromForm();

                Message message = new Message("add_product", newProduct, null);
                SimpleClient.getClient().sendToServer(message);

                clearForm();
                updateStatus("Adding product...");
            }
        } catch (IOException e) {
            e.printStackTrace();
            updateStatus("Error adding product");
        }
    }

    @FXML
    public void handleEditProduct(ActionEvent actionEvent) throws IOException {
        if (selectedProduct != null) {
            isEditMode = true;
            saveButton.setDisable(false);
            addButton.setDisable(true);
            updateStatus("Edit mode enabled");
        }
    }

    @FXML
    public void handleSaveProduct(ActionEvent actionEvent) {
        try {
            if (validateForm() && isEditMode && selectedProduct != null) {
                Product updatedProduct = createProductFromForm();
                updatedProduct.setId(selectedProduct.getId());

                Message message = new Message("editProduct:id:"+selectedProduct.getId(), updatedProduct, null);
                System.out.println(message.getMessage()+" price:"+updatedProduct.getPrice());
                SimpleClient.getClient().sendToServer(message);

                isEditMode = false;
                saveButton.setDisable(true);
                addButton.setDisable(false);
                updateStatus("Saving product...");
            }
        } catch (IOException e) {
            e.printStackTrace();
            updateStatus("Error saving product");
        }
    }

    @FXML
    public void handleDeleteProduct(ActionEvent actionEvent) {
        if (selectedProduct != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Product");
            alert.setHeaderText("Are you sure you want to delete this product?");
            alert.setContentText("Product: " + selectedProduct.getName());

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    Message message = new Message("delete_product", selectedProduct.getId(), null);
                    SimpleClient.getClient().sendToServer(message);

                    clearForm();
                    updateStatus("Deleting product...");
                } catch (IOException e) {
                    e.printStackTrace();
                    updateStatus("Error deleting product");
                }
            }
        }
    }

    @FXML
    public void handleBrowseImage(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Product Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        Stage stage = (Stage) productTable.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            imagePathField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    public void handleClearForm(ActionEvent actionEvent) {
        clearForm();
        updateStatus("Form cleared");
    }

    @FXML
    public void handleRefresh(ActionEvent actionEvent) {
        refreshCatalog();
        updateStatus("Refreshing catalog...");
    }

    @FXML
    public void handleExport(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Catalog");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        Stage stage = (Stage) productTable.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            exportToCSV(file);
        }
    }

    @FXML
    public void handleImport(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Catalog");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        Stage stage = (Stage) productTable.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            importFromCSV(file);
        }
    }

    @FXML
    public void handleClose(ActionEvent actionEvent) throws IOException {
        EventBus.getDefault().unregister(this);
        App.setRoot("primary");
    }

    // Helper Methods
    private boolean validateForm() {
        if (productNameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Product name is required");
            return false;
        }

        try {
            Double.parseDouble(productPriceField.getText());
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Price must be a valid number");
            return false;
        }

        if (categoryComboBox.getValue() == null || categoryComboBox.getValue().trim().isEmpty()) {
            showAlert("Validation Error", "Category is required");
            return false;
        }

        return true;
    }

    private Product createProductFromForm() {
        Product product = new Product();
        product.setName(productNameField.getText().trim());
        product.setPrice(Double.parseDouble(productPriceField.getText()));
        product.setType(categoryComboBox.getValue());
        product.setImagePath(imagePathField.getText().trim().isEmpty() ? null : imagePathField.getText().trim());
        return product;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void exportToCSV(File file) {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("ID,Name,Price,Category,ImagePath");

            for (Product product : productList) {
                writer.printf("%d,\"%s\",%.2f,\"%s\",\"%s\"%n",
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getType(),
                        product.getImagePath() != null ? product.getImagePath() : "");
            }

            updateStatus("Catalog exported to " + file.getName());
        } catch (IOException e) {
            e.printStackTrace();
            updateStatus("Error exporting catalog");
        }
    }

    private void importFromCSV(File file) {
        // Implementation for CSV import would depend on your specific requirements
        // and server-side handling
        updateStatus("Import functionality not yet implemented");
    }
}