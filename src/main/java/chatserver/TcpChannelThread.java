package chatserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.org.apache.bcel.internal.generic.NEW;

import Channel.*;
import sun.misc.BASE64Encoder;
import sun.org.mozilla.javascript.internal.ast.Symbol;
import sun.security.krb5.Config;
import util.Keys;

public class TcpChannelThread implements TcpChannel, Runnable {
	private static Log LOGGER = LogFactory.getLog(TcpChannelThread.class);
	private Chatserver chatserver;
	private User client;
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;
	private boolean handshakePhase1;
	private boolean handshakePhase2;
	private PublicKey publicKey;
	private PrivateKey privateKey;
	private SecretKey key;
	private byte[] serverC;
	final byte[] iVrandom = new byte[16];

	public TcpChannelThread(Chatserver chatserver, Socket socket) throws IOException {
		this.chatserver = chatserver;
		this.socket = socket;
		privateKey = Keys.readPrivatePEM(new File(chatserver.getConfig().getString("key")));
		handshakePhase1 = false;
		handshakePhase2 = false;
		// prepare the input reader for the socket
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		// prepare the writer for responding to clients requests
		writer = new PrintWriter(socket.getOutputStream(), true);
	}

	public String handshakePhase1(String message) throws Exception {
		// decrypt the first message from client.
		byte[] decryption = (new RSACrypto(new Base64Crypto(null, message), "", null, privateKey)).decode();
		// split the decrypted message into three parts.
		String[] decryptionInArray = (new String(decryption)).split(" ");
		try {
			if (decryptionInArray.length == 3 && decryptionInArray[0].equals("!authenticate")) {
				handshakePhase1 = true;
				publicKey = Keys.readPublicPEM(new File(
						chatserver.getConfig().getString("keys.dir") + "\\" + decryptionInArray[1] + ".pub.pem"));
			}
		} catch (IOException e) {
			handshakePhase1 = false; // if public key for client not
										// find,handshakePhase1 fails.
		}

		// if public key find, the return message will be organized.
		byte[] encryption = null;
		byte[] secretKey = null;

		if (handshakePhase1 == true && publicKey != null) {
			// Server-Challenge generate
			SecureRandom secureRandom = new SecureRandom();
			final byte[] serverChallenge = new byte[32];
			secureRandom.nextBytes(serverChallenge);
			// Server-Challenge Base 64 encode
			serverC = new Base64Crypto(null, new String(serverChallenge)).encode();
			// Secret-Key
			try {
				KeyGenerator generator = KeyGenerator.getInstance("AES");
				generator.init(256);
				key = generator.generateKey();
				// Secret-Key Base 64 encode
				secretKey = new Base64Crypto(null, new String(key.getEncoded())).encode();
			} catch (Exception e) {
				System.out.println("Error while encoding Secret Key");
			}
			// IV - Parameter

			secureRandom.nextBytes(iVrandom);
			// iV Base 64 encode
			byte[] iV = new Base64Crypto(null, new String(iVrandom)).encode();
			String okMessage = "!ok " + decryptionInArray[2] + " " + new String(serverC) + " " + new String(secretKey)
					+ " " + new String(iV);
			// OK-Message with RSA encrypt
			try {
				// ciphertext
				encryption = (new Base64Crypto(new RSACrypto(null, okMessage, publicKey, privateKey), "")).encode();
			} catch (Exception e) {
				System.out.println("Exception while encoding ok-Message with RSA" + e);
			}

		}
		if (encryption != null)
			return new String(encryption);
		else {
			handshakePhase1 = false;
			return null;
		}

	}

	public String handshakePhase2(String message) {
		try {
			byte[] decypted = (new AESCrypto(new Base64Crypto(null, message), "", key.getEncoded(), iVrandom)).decode();
			if ((new String((decypted))).equals(new String(serverC))) {
				handshakePhase2 = true;
				return "authentication succesfully finished.";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Try to login user {@code username}.
	 * 
	 * @param username
	 *            not null
	 * @param password
	 *            not null
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
	 * 
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
	 * Verify format of {@code privateAddress} and register user for private
	 * Messaging.
	 * 
	 * @param privateAddress
	 *            not null; must be IP address and port separated by a colon (=
	 *            IP:port)
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
	 * Send a message to all other users who are currently online. Only possible
	 * if logged in.
	 * 
	 * @param publicMessage
	 *            not null
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
	 * Check whether user exists and if he/she provides a private address. If
	 * so, return it. If not, inform user about it.
	 * 
	 * @param name
	 *            not null
	 * @return String "IP:port" if it exists. Otherwise, return info message.
	 */
	public String lookup(String name) {
		if (client == null || !client.isLoggedIn()) {
			return "You have to be logged in for this action.";
		}

		User userOfInterest = chatserver.getUser(name);

		if (userOfInterest == null) {
			return "There is no user named \"" + name + "\"";
		} else if (!userOfInterest.isLoggedIn()) {
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
	 * Assume that all messages with known commands have a correct number of
	 * parameters and correct format.
	 */
	@Override
	public void run() {
		// System.out.println("TcpChannel started.");

		String request;
		String response;

		try {
			request = reader.readLine();
			LOGGER.info("Starting handshake.");
			response = handshakePhase1(request);
			if (response != null && handshakePhase1 == true) {
				write(response);
				LOGGER.info("Handshake Phase 1 finished successful");
				LOGGER.info("Starting handshake Phase 2.");
				request = reader.readLine();
				response = handshakePhase2(request);
				if (response != null && handshakePhase2 == true) {
					write(response);
					LOGGER.info("Handshake Phase 2 finished successful");
				} else {
					socket.close();
				}
			} else {
				socket.close();
			}

			while ((request = reader.readLine()) != null && handshakePhase1 == handshakePhase2 == true) {
				request = decryption(request);
				// System.out.println("Received message via TCP: " + request);
				if (request.startsWith("!login ")) {
					String[] messageParts = request.split("\\s+"); // split by
																	// spaces
					response = login(messageParts[1], messageParts[2]);
				} else if (request.equals("!logout")) {
					response = logout();
				} else if (request.startsWith("!register ")) {
					String privateAddress = request.substring(10); // remove
																	// "!register
																	// " part
					response = register(privateAddress);
				} else if (request.startsWith("!lookup ")) {
					String name = request.substring(8);
					response = lookup(name);
				} else if (request.startsWith("!send ")) {
					request = request.substring(6); // remove "!send " part
					response = send(request);
				} else {
					response = "Unknown command. I'm sorry.";
				}
				response = encryption(response);
				write(response);
			}

		} catch (IOException e) {
			/*
			 * System.out .println(
			 * "IOException while waiting for/communicating with client: " +
			 * e.getMessage());
			 */
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("An unexpected exception occurred during interaction with client: " + e.getMessage());
		} finally {
			try {
				chatserver.unregisterTcpChannel(this);
				logout(); // Logout client - no effect if already logged out
				if (socket != null && !socket.isClosed())
					// System.out.println("Connection finished. Closing
					// channel.");
					socket.close();
			} catch (IOException e) {
				// Ignored because we cannot handle it
			}
			// System.out.println("TcpChannel finished");
		}
	}

	private String encryption(String message) throws Exception {
		byte[] encryption = (new Base64Crypto(new AESCrypto(null, message, key.getEncoded(), iVrandom), "")).encode();
		return new String(encryption);
	}

	public String decryption(String message) throws Exception {

		byte[] decypted = (new AESCrypto(new Base64Crypto(null, message), "", key.getEncoded(), iVrandom)).decode();
		return new String(decypted);

	}

}
