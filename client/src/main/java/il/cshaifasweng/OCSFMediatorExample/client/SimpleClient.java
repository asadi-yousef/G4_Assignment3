package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.scene.shape.CubicCurve;
import org.greenrobot.eventbus.EventBus;

import il.cshaifasweng.OCSFMediatorExample.client.ocsf.AbstractClient;
import il.cshaifasweng.OCSFMediatorExample.entities.Warning;
import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;

import java.math.BigDecimal;
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
			// 1) Handle account-ban or account-deleted
			if (key != null && (key.equals(ban_msg) || key.equals(delete_msg))) {
				if (key.equals(ban_msg)) {
					try {
						var current = SessionManager.getInstance().getCurrentUser();
						if (current != null) {
							client.sendToServer(new Message("force_logout", current.getUsername(), null));
						}
					} catch (Exception ignored) {}
				}

				Platform.runLater(() -> {
					try {
						SessionManager.getInstance().logout();
						App.setRoot("primary");
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
				return; // IMPORTANT
			}

			// 2) Budget refresh: make it global & sticky so *any* screen can update
			if ("budget".equals(key) || "budget_updated".equals(key)) {
				// If you keep a balance inside SessionManager, update it here (optional)
				try {
					java.math.BigDecimal bal = java.math.BigDecimal.ZERO;
					Object obj = message.getObject();

					// Supports either: Budget entity, BigDecimal, or Number
					if (obj instanceof il.cshaifasweng.OCSFMediatorExample.entities.Budget) {
						var b = (il.cshaifasweng.OCSFMediatorExample.entities.Budget) obj;
						if (b.getBalance() != null) bal = b.getBalance();
					} else if (obj instanceof java.math.BigDecimal) {
						bal = (java.math.BigDecimal) obj;
					} else if (obj instanceof Number) {
						bal = java.math.BigDecimal.valueOf(((Number) obj).doubleValue());
					}

					// OPTIONAL: if you have these methods in SessionManager, keep a single source of truth
					try {
						Customer c = (Customer) SessionManager.getInstance().getCurrentUser();
						c.getBudget().setBalance(bal);
					} catch (Throwable ignored) {
						// Safe to ignore if you haven't added setBudgetBalance; sticky event alone is enough
					}
				} catch (Throwable ignored) {
					// don't block UI updates if parsing fails; controllers can still read the payload directly
				}

				// Post as STICKY so any controller (now or later) will receive it immediately
				// Wrap on FX thread to be safe for UI subscribers
				Platform.runLater(() -> {
					org.greenrobot.eventbus.EventBus.getDefault().postSticky(message);
				});
				return; // handled
			}

			// 3) Route all other messages to EventBus (non-sticky)
			org.greenrobot.eventbus.EventBus.getDefault().post(message);

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