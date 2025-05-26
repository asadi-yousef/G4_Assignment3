package il.cshaifasweng.OCSFMediatorExample.client;
import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
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

    private static Flower selectedFlower;

    public static void setSelectedFlower(Flower flower) {
        selectedFlower = flower;
    }
    public void loadFlowerDetails(){
        Image image = new Image(String.valueOf(PrimaryController.class.getResource(selectedFlower.getImagePath()))); // Load from URL or file
        flowerImage.setImage(image);
        flowerImage.setFitWidth(400);
        flowerImage.setFitHeight(400);
        flowerImage.setPreserveRatio(true);
    }
    @FXML
    public void initialize() {
        if (selectedFlower != null) {
            nameLabel.setText("Name: " + selectedFlower.getName());
            typeLabel.setText("Type: " + selectedFlower.getType());
            priceLabel.setText(String.format("Price: $%.2f", selectedFlower.getPrice()));

            try {
                Image image = new Image(String.valueOf(SecondaryController.class.getResource(selectedFlower.getImagePath())));
                flowerImage.setImage(image);
            } catch (Exception e) {
                System.out.println("Could not load image for " + selectedFlower.getName());
            }
        }
    }

    @FXML
    private void handleBack() throws Exception {
        Main.switchToPrimaryView();
    }
}
