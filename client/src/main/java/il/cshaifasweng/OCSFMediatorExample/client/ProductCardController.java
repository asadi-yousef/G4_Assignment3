package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public class ProductCardController {

    // All your @FXML variables...
    @FXML private VBox displayVBox;
    @FXML private ImageView productImageView;
    @FXML private Label nameLabel;
    @FXML private Label typeLabel;
    @FXML private Rectangle colorDisplayRect;
    @FXML private HBox priceBox;
    @FXML private HBox buttonBox;
    @FXML private Label saleBadge;
    @FXML private VBox editVBox;

    private Product currentProduct;


    private void populateDisplayData() {
        // Set text and image data
        nameLabel.setText(currentProduct.getName());
        typeLabel.setText("Type: " + currentProduct.getType());
        try {
            Image image = new Image(Objects.requireNonNull(getClass().getResource(currentProduct.getImagePath())).toExternalForm());
            productImageView.setImage(image);
        } catch (Exception e) {
            System.err.println("Failed to load image: " + currentProduct.getImagePath());
        }

        // Set color display
        try {
            colorDisplayRect.setFill(Color.web(currentProduct.getColor()));
        } catch (Exception e) {
            colorDisplayRect.setFill(Color.GRAY);
        }

        // Set price display
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


    public void setData(Product product, boolean isEmployee, Runnable viewAction, Consumer<Product> saveAction, Runnable deleteAction) {
        this.currentProduct = product;

        // Call the shared helper to set up the display
        populateDisplayData();

        // Configure buttons for the advanced view
        buttonBox.getChildren().clear();
        if (isEmployee) {
            Button editButton = new Button("Edit");
            editButton.setOnAction(e -> {
                try {
                    switchToEditMode(saveAction);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }); // Pass the save action to the edit mode

            Button deleteBtn = new Button("Delete");
            deleteBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white;");
            deleteBtn.setOnAction(e -> deleteAction.run());

            buttonBox.getChildren().addAll(editButton, deleteBtn);
        }
        // ... any other logic for this advanced view
    }

    private void switchToEditMode(Consumer<Product> saveAction) throws IOException {
        EventBus.getDefault().unregister(this);
        App.setRoot("viewFlower");
    }

    /**
     * SIMPLE setData for the catalog grid (PrimaryController).
     * It only needs to handle basic display and button actions.
     */
    public void setData(Product product, boolean isEmployee, Runnable primaryAction, Runnable secondaryAction) {
        this.currentProduct = product;

        // Call the shared helper to set up the display
        populateDisplayData();

        // Configure buttons for the simple grid view
        buttonBox.getChildren().clear();
        if (isEmployee) {
            Button editButton = new Button("Edit");
            editButton.setOnAction(e -> primaryAction.run());
            Button deleteButton = new Button("Delete");
            deleteButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white;");
            deleteButton.setOnAction(e -> secondaryAction.run());
            buttonBox.getChildren().addAll(editButton, deleteButton);
        } else {
            Button viewButton = new Button("View");
            viewButton.setOnAction(e -> primaryAction.run());
            Button addToCartButton = new Button("Add to Cart");
            addToCartButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
            addToCartButton.setOnAction(e -> secondaryAction.run());
            buttonBox.getChildren().addAll(viewButton, addToCartButton);
        }
    }

    // ... all the other methods like switchToEditMode, handleSave, etc., remain here ...

}