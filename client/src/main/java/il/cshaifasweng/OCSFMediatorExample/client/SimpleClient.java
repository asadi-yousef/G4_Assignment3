package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
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
        if (msg instanceof Message) {
			Message message = (Message) msg;
			if(message.getMessage().equals(ban_msg)) {
				try{
					client.sendToServer(new Message("force_logout",SessionManager.getInstance().getCurrentUser().getUsername(),null));
					SessionManager.getInstance().logout();
					App.setRoot("primary");
				}catch(Exception ignored){}
			}
			else if(message.getMessage().equals(delete_msg)) {
				try {
					SessionManager.getInstance().logout();
					App.setRoot("primary");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			else{
				EventBus.getDefault().post(msg);
			}
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