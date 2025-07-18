package il.cshaifasweng.OCSFMediatorExample.client;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ViewFlowerController {
    @FXML
    private ImageView flowerImage;
    @FXML
    private Label nameLabel;
    @FXML
    private Label typeLabel;
    @FXML
    private Label priceLabel;

    private static Product selectedProduct;

    public static void setSelectedFlower(Product product) {
        selectedProduct = product;
    }
    public void loadFlowerDetails(){
        Image image = new Image(String.valueOf(PrimaryController.class.getResource(selectedProduct.getImagePath()))); // Load from URL or file
        flowerImage.setImage(image);
        flowerImage.setFitWidth(400);
        flowerImage.setFitHeight(400);
        flowerImage.setPreserveRatio(true);
    }
    @FXML
    public void initialize() {
        if (selectedProduct != null) {
            nameLabel.setText("Name: " + selectedProduct.getName());
            typeLabel.setText("Type: " + selectedProduct.getType());
            priceLabel.setText(String.format("Price: $%.2f", selectedProduct.getPrice()));

            try {
                Image image = new Image(String.valueOf(SecondaryController.class.getResource(selectedProduct.getImagePath())));
                flowerImage.setImage(image);
            } catch (Exception e) {
                System.out.println("Could not load image for " + selectedProduct.getName());
            }
        }
    }

    @FXML
    private void handleBack() throws Exception {
        App.switchView("primary.fxml");
    }
}
