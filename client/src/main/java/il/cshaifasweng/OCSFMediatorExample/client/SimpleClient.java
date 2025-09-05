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

    @Override
    protected void handleMessageFromServer(Object msg) {
        if (msg instanceof Message) {
			Message message = (Message) msg;
			if(message.getMessage().equals("account_banned")){
				try{
					client.sendToServer(new Message("force_logout",SessionManager.getInstance().getCurrentUser().getUsername(),null));
					SessionManager.getInstance().logout();
					App.setRoot("primary");
				}catch(Exception ignored){}
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