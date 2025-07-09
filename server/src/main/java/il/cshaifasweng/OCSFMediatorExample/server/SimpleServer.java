package il.cshaifasweng.OCSFMediatorExample.server;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.AbstractServer;
import il.cshaifasweng.OCSFMediatorExample.server.ocsf.ConnectionToClient;

import java.io.IOException;
import java.util.ArrayList;

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

	private List<Product> getFlowerListFromDB() {
		List<Product> productList;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			productList = session.createQuery("from Product", Product.class).list();
		} catch (Exception e) {
			e.printStackTrace();
			productList = new ArrayList<>();
		}
		return productList;
	}

	@Override
	protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
		String msgString = msg.toString();

		if (msgString.equals("request_catalog")) {
			List<Product> productList = getFlowerListFromDB();
			catalog = new Catalog(productList);
			try {
				client.sendToClient(catalog);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if (msgString.startsWith("update_price")) {
			String[] parts = msgString.split(":");
			if (parts.length == 3) {
				try {
					int flowerId = Integer.parseInt(parts[1]);
					double newPrice = Double.parseDouble(parts[2]);
					updateFlowerPrice(flowerId, newPrice);
					this.catalog.setFlowers(getFlowerListFromDB());
					sendToAllClients(catalog);
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
            }
		}
		else if (msgString.startsWith("remove client")) {
			if (!SubscribersList.isEmpty()) {
				SubscribersList.removeIf(subscribedClient -> subscribedClient.getClient().equals(client));
			}
		} else if (msgString.contains("check existence")) {
			System.out.println("check existence");
			Transaction tx = null;
			try (Session session = HibernateUtil.getSessionFactory().openSession()) {
				String username;
				String password;
				String tmp;

				tx = session.beginTransaction();
				tmp = msgString.substring("check existence: ".length());
				username = tmp.split(" ")[0];
				password = tmp.split(" ")[1];

				List<User> users = session.createQuery("FROM User WHERE username = :username", User.class)
						.setParameter("username", username)
						.getResultList();

				System.out.println("Query result: " + users);  // debug line

				if (users.isEmpty()) {
					System.out.println("User not found");
					client.sendToClient("incorrect");
				} else {
					User user = users.get(0);
					System.out.println("User found: " + user.getClass().getSimpleName()); // will print "Employee" or "Customer"

					if (user.getPassword().equals(password)) {
						client.sendToClient("correct");
					} else {
						client.sendToClient("incorrect password");
					}
				}

				tx.commit();
			} catch (Exception e) {
				if (tx != null) tx.rollback();
				e.printStackTrace();
			}
		}
	}

	public void updateFlowerPrice(int flowerId, double newPrice) {
		Transaction tx = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			tx = session.beginTransaction();

			Product product = session.get(Product.class, flowerId);
			if (product != null) {
				product.setPrice(newPrice);
				session.update(product); // Optional
			} else {
				System.out.println("Flower not found with ID: " + flowerId);
			}

			tx.commit();
		} catch (Exception e) {
			if (tx != null) tx.rollback();
			e.printStackTrace();
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
