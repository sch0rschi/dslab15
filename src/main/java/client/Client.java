package client;

import crypto.AESCrypto;
import crypto.Base64Crypto;
import crypto.RSACrypto;
import cli.Command;
import cli.Shell;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import util.Config;
import util.Keys;

import java.io.*;
import java.net.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;

public class Client implements IClientCli, Runnable {

	private static Log LOGGER = LogFactory.getLog(Client.class);

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private String username;
	private DatagramSocket datagramSocket;
	private ServerSocket privateComServerSocket; // for private messaging
	private TcpPrivateListenerThread tcpPrivateListenerThread;
	private Socket socket; // socket for communication with server
	private BufferedReader serverReader;
	private PrintWriter serverWriter;
	private Shell shell;
	private TcpServerListenerThread tcpServerListenerThread;
	private PublicKey publicKey;
	private PrivateKey privateKey;
	private byte[] secretkey;
	private byte[] iv;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Client(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		try {
			this.publicKey = Keys.readPublicPEM(new File("./" + config.getString("chatserver.key")));
		} catch (IOException e) {
			System.out.println("Server error!");
		}
		shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		shell.register(this);
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}

	@Override
	public void run() {
		// connect to server (tcp)
		String host = config.getString("chatserver.host");
		int tcpPort = config.getInt("chatserver.tcp.port");

		try {
			// create socket for tcp connection to server
			socket = new Socket(host, tcpPort);
			serverWriter = new PrintWriter(socket.getOutputStream(), true);
			serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			tcpServerListenerThread = new TcpServerListenerThread(socket, serverReader, shell);
			new Thread(tcpServerListenerThread).start();

			// create datagramSocket for communication with server via UDP
			datagramSocket = new DatagramSocket();
		} catch (UnknownHostException e) {
			System.err.println("Don't know about host " + host);
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to " + host);
			System.exit(1);
		}

		// start shell
		new Thread(shell).start();
		System.out.println(getClass().getName() + " up and waiting for commands!");
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {
		try {
			synchronized (tcpServerListenerThread) {
				authenticate(username);
				serverWriter.println(encryption("!login " + username + " " + password));
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}

		String response = decryption(tcpServerListenerThread.getLastResponse());
		if (response.startsWith("[success]")) {
			this.username = username;
			response = response.substring(9);
		}

		return response;
	}

	@Override
	@Command
	public String logout() throws IOException {
		try {
			synchronized (tcpServerListenerThread) {
				serverWriter.println(encryption("!logout"));
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}

		String response = decryption(tcpServerListenerThread.getLastResponse());
		if (response.startsWith("[success]")) {
			this.username = null;
			response = response.substring(9);
		}

		return response;
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		try {
			synchronized (tcpServerListenerThread) {
				serverWriter.println(encryption("!send " + message));
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}
		return decryption(tcpServerListenerThread.getLastResponse());
	}

	@Override
	@Command
	public String list() throws IOException {
		String request = "!list";
		byte[] buffer = request.getBytes();

		DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
				InetAddress.getByName(config.getString("chatserver.host")), config.getInt("chatserver.udp.port"));

		datagramSocket.send(packet);

		// now receive response
		buffer = new byte[1024];
		packet = new DatagramPacket(buffer, buffer.length);
		datagramSocket.receive(packet);

		String response = new String(packet.getData());
		return response;
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {
		// privateAddress: either an actual address or a failure message. We
		// need to find out.
		String privateAddress = lookup(username);

		if (privateAddress.matches("((localhost)|(\\d+\\.\\d+\\.\\d+\\.\\d+)):(\\d+)")) {
			String[] addressParts = privateAddress.split(":"); // [0] = ip, [1]
																// = port
			String ip = addressParts[0];
			int port;
			try {
				port = Integer.parseInt(addressParts[1]);
			} catch (NumberFormatException e) {
				return "For some weird reason, the received port wasn't a number";
			}

			String response;
			Socket privateMsgSocket = null;

			// establish connection and write message
			try {
				privateMsgSocket = new Socket(ip, port);
				BufferedReader reader = new BufferedReader(new InputStreamReader(privateMsgSocket.getInputStream()));
				PrintWriter writer = new PrintWriter(privateMsgSocket.getOutputStream(), true);
				writer.println(this.username + " (private): " + message);
				response = reader.readLine();
			} catch (IOException e) {
				response = "Could not set up communication with " + username;
			} finally {
				if (privateMsgSocket != null && !privateMsgSocket.isClosed()) {
					privateMsgSocket.close();
				}
			}
			if (response.equals("!ack")) {
				return username + " replied with !ack";
			} else {
				return "Failure: " + response;
			}
		} else {
			// Return failure message
			return privateAddress;
		}
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		try {
			synchronized (tcpServerListenerThread) {
				serverWriter.println(encryption("!lookup " + username));
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}
		return decryption(tcpServerListenerThread.getLastResponse());
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if (!privateAddress.matches("((localhost)|(\\d+\\.\\d+\\.\\d+\\.\\d+)):(\\d+)")) {
			return "Failure: Incorrect format for address.";
		}

		String[] addressParts = privateAddress.split(":");
		String portString = addressParts[1];
		int port = Integer.parseInt(portString);

		ServerSocket newPrivateComServerSocket = new ServerSocket(port);

		if (privateComServerSocket != null && !privateComServerSocket.isClosed()) {
			// close existing serverSocket
			privateComServerSocket.close();
		}
		privateComServerSocket = newPrivateComServerSocket;
		tcpPrivateListenerThread = new TcpPrivateListenerThread(privateComServerSocket, shell);
		// start new Thread that listens to incoming connections and handles
		// them
		new Thread(tcpPrivateListenerThread).start(); // will stop when
														// privateComServerSocket
														// is closed

		try {
			synchronized (tcpServerListenerThread) {

				serverWriter.println(encryption("!register " + privateAddress));
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		} catch (Exception e) {

			e.printStackTrace();
		}

		return decryption(tcpServerListenerThread.getLastResponse());
	}

	@Override
	@Command
	public String lastMsg() throws IOException {

		return tcpServerListenerThread.getLastMsg();

	}

	@Override
	@Command
	public String exit() throws IOException {
		shell.close();
		if (!socket.isClosed()) {
			socket.close();
		}
		if (privateComServerSocket != null && !privateComServerSocket.isClosed()) {
			// close existing serverSocket
			privateComServerSocket.close();
		}

		return "exited properly";
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	@Command
	public String authenticate(String username) {
		// authenticate phase 1
		byte[] encryption = null;
		String client_c = null;
		try {

			privateKey = Keys.readPrivatePEM(new File("./" + config.getString("keys.dir") + "/" + username + ".pem"));
		} catch (IOException e1) {
			LOGGER.error("Read privatekey from file failed.");
			return "no user found with the given username";
		}
		// generates a 32 byte secure random number
		SecureRandom secureRandom = new SecureRandom();
		final byte[] clientChallenge = new byte[32];
		secureRandom.nextBytes(clientChallenge);

		try {
			encryption = (new Base64Crypto(null, new String(clientChallenge))).encode();
			client_c = new String(encryption);
			encryption = (new Base64Crypto(new RSACrypto(null,
					"!authenticate " + username + " " + (new String(encryption)), publicKey, privateKey), "")).encode();

		} catch (Exception e) {
			return "Server error";
		}
		try {
			synchronized (tcpServerListenerThread) {
				serverWriter.println(new String(encryption));
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}

		// authenticate phase 2
		String message_2nd = tcpServerListenerThread.getLastResponse();
		String[] message_2nd_parts = null;
		try {
			message_2nd = new String(
					(new RSACrypto(new Base64Crypto(null, message_2nd), "", null, privateKey)).decode());
			message_2nd_parts = message_2nd.split(" ");
			if (message_2nd_parts.length == 5 && message_2nd_parts[0].equals("!ok")
					&& message_2nd_parts[1].equals(client_c)) {
				secretkey = (new Base64Crypto(null, message_2nd_parts[3])).decode();
				iv = (new Base64Crypto(null, message_2nd_parts[4])).decode();
				encryption = (new Base64Crypto(new AESCrypto(null, message_2nd_parts[2],
						secretkey, iv), "")).encode();
				// send the encrypted chatserverChallenge(AES)
				try {
					synchronized (tcpServerListenerThread) {
						serverWriter.println(new String(encryption));
						tcpServerListenerThread.wait(5000);
					}
				} catch (InterruptedException e) {
					return "interrupted";
				}
				return tcpServerListenerThread.getLastResponse();

			} else {
				return "Handshake failed!";
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return message_2nd;

	}

	private String encryption(String message) {
		byte[] encryption = null;
		try {
			encryption = (new Base64Crypto(new AESCrypto(null, message, secretkey, iv), "")).encode();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new String(encryption);
	}

	public String decryption(String message) {

		byte[] decypted = null;
		try {
			decypted = (new AESCrypto(new Base64Crypto(null, message), "", secretkey, iv)).decode();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new String(decypted);

	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client("Client", new Config("client"), System.in, System.out);
		new Thread(client).start();
	}

}
