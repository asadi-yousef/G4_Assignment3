package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import org.greenrobot.eventbus.EventBus;

import il.cshaifasweng.OCSFMediatorExample.client.ocsf.AbstractClient;
import il.cshaifasweng.OCSFMediatorExample.entities.Warning;
import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;

import java.util.Scanner;

public class SimpleClient extends AbstractClient {

	private static SimpleClient client = null;

	private SimpleClient(String host, int port) {
		super(host, port);
	}

	private static String delete_msg = "account_deleted";
	public static String ban_msg = "account_banned";

	@Override
	protected void handleMessageFromServer(Object msg) {
		if (!(msg instanceof Message)) return;

		Message message = (Message) msg;
		String key = message.getMessage();

		try {
			// Handle account-ban or account-deleted
			if (key != null && (key.equals(ban_msg) || key.equals(delete_msg))) {

				// If you really want to inform the server once, guard it:
				if (key.equals(ban_msg)) {
					try {
						var current = SessionManager.getInstance().getCurrentUser();
						if (current != null) {
							client.sendToServer(new Message("force_logout",
									current.getUsername(), null));
						}
					} catch (Exception ignored) {}
				}

				// Always do UI work on the FX thread
				Platform.runLater(() -> {
					try {
						SessionManager.getInstance().logout();
						App.setRoot("primary"); // single navigation
					} catch (Exception e) {
						e.printStackTrace();
					}
				});

				return; // IMPORTANT: don't fall through to EventBus
			}

			// Route all other messages to the EventBus
			EventBus.getDefault().post(message);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public static SimpleClient getClient() {
		if (client == null) {
			client = new SimpleClient("localhost", 3000);
		}
		return client;
	}
	public static SimpleClient getClient(String host, int port) {
		if (client == null) {
			client = new SimpleClient(host, port);
		}
		return client;
	}

}