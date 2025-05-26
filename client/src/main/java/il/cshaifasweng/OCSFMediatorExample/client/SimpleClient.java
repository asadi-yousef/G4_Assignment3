package il.cshaifasweng.OCSFMediatorExample.client;

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
		EventBus.getDefault().post(msg);
	}


	public static SimpleClient getClient() {
		//Scanner scanner = new Scanner(System.in);

		//System.out.print("Enter server IP address: ");
		//String ipAddress = scanner.nextLine();
		if (client == null) {
			client = new SimpleClient("localhost", 3000);
		}
		return client;
	}

}