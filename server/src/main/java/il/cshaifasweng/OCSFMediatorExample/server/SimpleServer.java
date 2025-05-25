package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;

import java.io.IOException;
import java.util.ArrayList;

import il.cshaifasweng.OCSFMediatorExample.entities.Warning;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.SubscribedClient;

import org.hibernate.Session;
import org.hibernate.Transaction;
import java.util.List;


public class SimpleServer extends AbstractServer {
	private static ArrayList<SubscribedClient> SubscribersList = new ArrayList<>();
	private static Catalog catalog = new Catalog(new ArrayList<>());

	public SimpleServer(int port) {
		super(port);
		catalog.setFlowers(getFlowerListFromDB());
	}

	private List<Flower> getFlowerListFromDB() {
		List<Flower> flowerList;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			flowerList = session.createQuery("from Flower", Flower.class).list();
		} catch (Exception e) {
			e.printStackTrace();
			flowerList = new ArrayList<>();
		}
		return flowerList;
	}

	@Override
	protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
		String msgString = msg.toString();

		System.out.println("Recieved from client : " + msgString);
		if (msgString.startsWith("add client")) {
			SubscribedClient connection = new SubscribedClient(client);
			SubscribersList.add(connection);
			try {
				client.sendToClient("client added successfully");
				client.sendToClient(catalog);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else if (msgString.equals("request_catalog")) {
			List<Flower> flowerList = getFlowerListFromDB();
			catalog = new Catalog(flowerList);
			try {
				client.sendToClient(catalog);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (msgString.startsWith("remove client")) {
			if (!SubscribersList.isEmpty()) {
				SubscribersList.removeIf(subscribedClient -> subscribedClient.getClient().equals(client));
			}
		} else if (msgString.contains("price")) {
			// update the price logic goes here later
		}
	}


	public void sendToAllClients(String message) {
		try {
			for (SubscribedClient subscribedClient : SubscribersList) {
				subscribedClient.getClient().sendToClient(message);
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

}
