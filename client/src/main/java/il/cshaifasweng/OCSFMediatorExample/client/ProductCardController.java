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

import java.io.*;
import java.util.Objects;
import java.util.function.Consumer;

public class ProductCardController {

    // FXML Variables
    @FXML private VBox displayVBox;
    @FXML private VBox editVBox;
    @FXML private ImageView productImageView;
    @FXML private Label nameLabel;
    @FXML private Label typeLabel;
    @FXML private Rectangle colorDisplayRect;
    @FXML private HBox priceBox;
    @FXML private HBox buttonBox;
    @FXML private Label saleBadge;

    private Product currentProduct;

    @FXML
    public void initialize() {
        if (editVBox != null) {
            editVBox.setVisible(false);
            editVBox.setManaged(false);
        }
    }

    private void populateDisplayData() {
        // This method remains the same.
        nameLabel.setText(currentProduct.getName());
        typeLabel.setText("Type: " + currentProduct.getType());
        try {
            Image image = new Image(Objects.requireNonNull(getClass().getResource(currentProduct.getImagePath())).toExternalForm());
            productImageView.setImage(image);
        } catch (Exception e) {
            System.err.println("Failed to load image: " + currentProduct.getImagePath());
        }
        try {
            colorDisplayRect.setFill(Color.web(currentProduct.getColor()));
        } catch (Exception e) {
            colorDisplayRect.setFill(Color.GRAY);
        }
        priceBox.getChildren().clear();
        if (currentProduct.getDiscountPercentage() > 0) {
            Text oldPriceText = new Text(String.format("$%.2f", currentProduct.getPrice()));
            oldPriceText.setStrikethrough(true);
            oldPriceText.setFill(Color.GREY);
            Label salePriceLabel = new Label(String.format("$%.2f", currentProduct.getSalePrice()));
            salePriceLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b;");
            salePriceLabel.setFont(new Font("Bell MT", 16));
            priceBox.getChildren().addAll(oldPriceText, salePriceLabel);
            saleBadge.setVisible(true);
        } else {
            Label price = new Label(String.format("$%.2f", currentProduct.getPrice()));
            price.setFont(new Font("Bell MT", 16));
            priceBox.getChildren().add(price);
            saleBadge.setVisible(false);
        }
    }

    /**
     * setData method for an Employee.
     * @param product The product to display.
     * @param saveAction The logic to execute when the Save button is clicked.
     * @param deleteAction The logic to execute when the Delete button is clicked.
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
     * @param product The product to display.
     * @param viewAction The logic for the "View" button.
     * @param addToCartAction The logic for the "Add to Cart" button.
     */
    public void setData(Product product, Runnable viewAction, Runnable addToCartAction) {
        this.currentProduct = product;
        populateDisplayData();
        buttonBox.getChildren().clear();

        Button viewButton = new Button("View");
        viewButton.setOnAction(e -> viewAction.run());

        Button addToCartButton = new Button("Add to Cart");
        addToCartButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        addToCartButton.setOnAction(e -> addToCartAction.run());

        buttonBox.getChildren().addAll(viewButton, addToCartButton);
    }

    // The rest of the file (switchToEditMode, switchToDisplayMode, etc.) remains exactly the same.
    // The saveButton's logic correctly calls saveAction.accept(currentProduct), which now triggers the real server logic.

    private void switchToEditMode(Consumer<Product> saveAction) {
        displayVBox.setVisible(false);
        displayVBox.setManaged(false);
        editVBox.setVisible(true);
        editVBox.setManaged(true);
        editVBox.getChildren().clear();
        editVBox.setPadding(new Insets(10));
        editVBox.setSpacing(10);
        TextField nameField = new TextField(currentProduct.getName());
        TextField typeField = new TextField(currentProduct.getType());
        TextField priceField = new TextField(String.format("%.2f", currentProduct.getPrice()));
        TextField discountField = new TextField(String.valueOf(currentProduct.getDiscountPercentage()));
        ColorPicker colorPicker = new ColorPicker(Color.web(currentProduct.getColor()));
        TextField imagePathField = new TextField(currentProduct.getImagePath());
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
        Button saveButton = new Button("Save");
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
        cancelButton.setOnAction(e -> switchToDisplayMode());
        saveButton.setOnAction(e -> {
            try {
                currentProduct.setName(nameField.getText());
                currentProduct.setType(typeField.getText());
                currentProduct.setPrice(Double.parseDouble(priceField.getText()));
                currentProduct.setDiscountPercentage(Double.parseDouble(discountField.getText()));
                currentProduct.setColor(toHexString(colorPicker.getValue()));
                String pathFromField = imagePathField.getText().trim();
                if (!pathFromField.isEmpty() && !pathFromField.startsWith("/")) {
                    File sourceFile = new File(pathFromField);
                    if (sourceFile.exists()) {
                        File destDir = new File("src/main/resources/il/cshaifasweng/OCSFMediatorExample/client/images");
                        if (!destDir.exists()) destDir.mkdirs();
                        String fileName = sourceFile.getName();
                        File destFile = new File(destDir, fileName);
                        try (InputStream in = new FileInputStream(sourceFile); OutputStream out = new FileOutputStream(destFile)) {
                            in.transferTo(out);
                        }
                        currentProduct.setImagePath("/il/cshaifasweng/OCSFMediatorExample/client/images/" + fileName);
                    }
                } else {
                    currentProduct.setImagePath(pathFromField);
                }
                saveAction.accept(currentProduct);
                switchToDisplayMode();
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Price and Discount must be valid numbers.").showAndWait();
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not copy image file: " + ex.getMessage()).showAndWait();
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

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}