package chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TcpChannelThread implements TcpChannel, Runnable {

	private Chatserver chatserver;
	private User client;
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;

	public TcpChannelThread(Chatserver chatserver, Socket socket) throws IOException {
		this.chatserver = chatserver;
		this.socket = socket;

		// prepare the input reader for the socket
		reader = new BufferedReader(
				new InputStreamReader(socket.getInputStream()));
		// prepare the writer for responding to clients requests
		writer = new PrintWriter(socket.getOutputStream(),
				true);
	}

	/**
	 * Try to login user {@code username}.
	 * 
	 * @param username not null
	 * @param password not null
	 * @return info about login success or failure notice
	 */
	public String login(String username, String password) {

		if (client != null && client.isLoggedIn()) {
			return "You are already logged in!";
		}

		client = chatserver.getUser(username);
		if (client == null) {
			return "User \"" + username + "\" does not exist.";
		} else if (!client.checkPassword(password)) {
			client = null;
			return "Password incorrect.";
		} else if (client.isLoggedIn()) {
			client = null;
			return "User already logged in.";
		}

		boolean isLoginSuccessful = client.login(password, this);
		if (isLoginSuccessful) {
			chatserver.registerTcpChannel(this);
			return "[success]Successfully logged in.";
		} else {
			client = null;
			return "Login failed. Please try again.";
		}
	}

	/**
	 * Try to logout user. User must be logged in to call this action.
	 * @return info message about success of this action.
	 */
	public String logout() {
		if (client == null || !client.isLoggedIn()) {
			return "You have to be logged in for this action.";
		}

		boolean logoutSuccessful = client.logout(this);
		if (logoutSuccessful) {
			chatserver.unregisterTcpChannel(this);
			client.unregisterPrivateAddress();
			return "[success]Successfully logged out.";
		} else {
			return "Logout failed. Please try again.";
		}
	}

	/**
	 * Verify format of {@code privateAddress} and register user for private Messaging.
	 * @param privateAddress not null; must be IP address and port separated by a colon (= IP:port)
	 * @return info message about success of registration
	 */
	public String register(String privateAddress) {
		if (client == null || !client.isLoggedIn()) {
			return "You have to be logged in for this action.";
		}

		if (!privateAddress.matches("((localhost)|(\\d+\\.\\d+\\.\\d+\\.\\d+)):(\\d+)")) {
			return "Invalid format of private address.";
		}

		String[] addressParts = privateAddress.split(":");
		String ip = addressParts[0];
		int port;
		try {
			port = Integer.parseInt(addressParts[1]);
		} catch (NumberFormatException e) {
			return "Wrong address format. Port must be an integer.";
		}

		client.registerPrivateAddress(ip, port);

		return "Successfully registered for private messaging. IP: " + ip + ", Port: " + port;

	}

	/**
	 * Send a message to all other users who are currently online. Only possible if logged in.
	 * @param publicMessage not null
	 * @return info message about success of action
	 */
	public String send(String publicMessage) {
		if (client == null || !client.isLoggedIn()) {
			return "You have to be logged in for this action.";
		}

		publicMessage = client.getName() + ": " + publicMessage;
		
		try {
			chatserver.writeAll(this, publicMessage);
		} catch (IOException e) {
			return "Sending of public message unexpectedly failed.";
		}

		return "Public message was successfully sent.";
	}

	/**
	 * Check whether user exists and if he/she provides a private address. If so, return it.
	 * If not, inform user about it.
	 * @param name not null
	 * @return String "IP:port" if it exists. Otherwise, return info message.
	 */
	public String lookup(String name) {
		if (client == null || !client.isLoggedIn()) {
			return "You have to be logged in for this action.";
		}

		User userOfInterest = chatserver.getUser(name);

		if (userOfInterest == null) {
			return "There is no user named \"" + name + "\"";
		} else if(!userOfInterest.isLoggedIn()) {
			return name + " is currently offline";
		}

		if (userOfInterest.isRegisteredWithPrivateAddress()) {
			return userOfInterest.getPrivateAddress();
		} else {
			return name + " does not provide a private address.";
		}
	}


	@Override
	public void write(String message) {
		synchronized (writer) {
			writer.println(message);
		}
	}

	/**
	 * Assume that all messages with known commands have a correct number of parameters and correct format.
	 */
	@Override
	public void run() {
		//System.out.println("TcpChannel started.");

		String request;
		String response;

		try {

			while((request = reader.readLine()) != null) {
				//System.out.println("Received message via TCP: " + request);

				if (request.startsWith("!login ")) {
					String[] messageParts = request.split("\\s+"); //split by spaces
					response = login(messageParts[1], messageParts[2]);
				} else if (request.equals("!logout")) {
					response = logout();
				} else if (request.startsWith("!register ")) {
					String privateAddress = request.substring(10); //remove "!register " part
					response = register(privateAddress);
				} else if (request.startsWith("!lookup ")) {
					String name = request.substring(8);
					response = lookup(name);
				} else if (request.startsWith("!send ")) {
					request = request.substring(6); //remove "!send " part
					response = send(request);
				} else {
					response = "Unknown command. I'm sorry.";
				}

				write(response);
			}

		} catch (IOException e) {
			/* System.out
			.println("IOException while waiting for/communicating with client: "
					+ e.getMessage()); */
		} catch (Exception e) {
			System.err.println("An unexpected exception occurred during interaction with client: "
					+ e.getMessage());
		}
		finally {
			try{
				chatserver.unregisterTcpChannel(this);
				logout(); //Logout client - no effect if already logged out
				if (socket != null && !socket.isClosed())
					//System.out.println("Connection finished. Closing channel.");
				socket.close();
			} catch (IOException e) {
				// Ignored because we cannot handle it
			}
			//System.out.println("TcpChannel finished");
		}
	}

}
