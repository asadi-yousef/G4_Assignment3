package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

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
    @Subscribe
    public void onMessageFromServer(Message message){
        String price = message.getMessage().split(":")[2];
        if(message.getMessage().startsWith("update_price")) {
            priceLabel.setText(price);
        }
    }
    @FXML
    public void initialize() {
        EventBus.getDefault().register(this);
        if (selectedProduct != null) {
            nameLabel.setText("Name: " + selectedProduct.getName());
            typeLabel.setText("Type: " + selectedProduct.getType());
            priceLabel.setText(String.format("Price: $%.2f", selectedProduct.getPrice()));

            try {
                Image image = new Image(String.valueOf(SecondaryController.class.getResource(selectedProduct.getImagePath())));
                flowerImage.setImage(image);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleBack() throws Exception {
        App.setRoot("primary");
        EventBus.getDefault().unregister(this);
    }
}
