package client;

import util.Keys;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import cli.Command;
import cli.Shell;
import util.Config;
import org.bouncycastle.util.encoders.Base64;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private String username;
	private DatagramSocket datagramSocket;
	private ServerSocket privateComServerSocket; //for private messaging
	private TcpPrivateListenerThread tcpPrivateListenerThread;
	private Socket socket; //socket for communication with server
	private BufferedReader serverReader;
	private PrintWriter serverWriter;
	private Shell shell;
	private TcpServerListenerThread tcpServerListenerThread;

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
	public Client(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		shell.register(this);
	}

	@Override
	public void run() {			
		//connect to server (tcp)
		String host = config.getString("chatserver.host");
		int tcpPort = config.getInt("chatserver.tcp.port");

		try {
			//create socket for tcp connection to server
			socket = new Socket(host, tcpPort);
			serverWriter = new PrintWriter(socket.getOutputStream(), true);
			serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			tcpServerListenerThread = new TcpServerListenerThread(socket, serverReader, shell);
			new Thread(tcpServerListenerThread).start();

			//create datagramSocket for communication with server via UDP
			datagramSocket = new DatagramSocket();
		} catch (UnknownHostException e) {
			System.err.println("Don't know about host " + host);
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to " +
					host);
			System.exit(1);
		}

		//start shell
		new Thread(shell).start();
		System.out.println(getClass().getName()
				+ " up and waiting for commands!");
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {
		try {
			synchronized(tcpServerListenerThread) {
				serverWriter.println("!login " + username + " " + password);
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}

		String response = tcpServerListenerThread.getLastResponse();
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
			synchronized(tcpServerListenerThread) {
				serverWriter.println("!logout");
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}

		String response = tcpServerListenerThread.getLastResponse();
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
			synchronized(tcpServerListenerThread) {
				serverWriter.println("!send " + message);
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}
		return tcpServerListenerThread.getLastResponse();
	}

	@Override
	@Command
	public String list() throws IOException {
		String request = "!list";
		byte[] buffer = request.getBytes();

		DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
				InetAddress.getByName(config.getString("chatserver.host")),
				config.getInt("chatserver.udp.port"));

		datagramSocket.send(packet);

		//now receive response
		buffer = new byte[1024];
		packet = new DatagramPacket(buffer, buffer.length);
		datagramSocket.receive(packet);

		String response = new String(packet.getData());
		return response;
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {

		//privateAddress: either an actual address or a failure message. We need to find out.
		String privateAddress = lookup(username);

		if(privateAddress.matches("((localhost)|(\\d+\\.\\d+\\.\\d+\\.\\d+)):(\\d+)")) {
			String[] addressParts = privateAddress.split(":"); // [0] = ip, [1] = port
			String ip = addressParts[0];
			int port;
			try {
				port = Integer.parseInt(addressParts[1]);
			} catch (NumberFormatException e) {
				return "For some weird reason, the received port wasn't a number";
			}

			String response;
			Socket privateMsgSocket = null;
			SignatedChannel channel; //Channel with <HMAC> message signation

			//establish connection and write message
			try {
				privateMsgSocket = new Socket(ip, port);
				channel = new TcpPrivateChannelThread(socket, shell);

				channel.write(" !msg " + message); //format: <HMAC> !msg <message>
				response = channel.read();
			} catch (IOException e) {
				response = "Could not set up communication with " + username;
				return response;
			} finally {
				if (privateMsgSocket != null && !privateMsgSocket.isClosed()) {
					privateMsgSocket.close();
				}
			}

			//has response been tampered?
			if (!channel.verify(response)) {
				return username + "'s response has been tampered.";
			}

			//response is authentic
				response = response.substring(response.indexOf(' ') + 1);
			if (response.startsWith("!ack")) {
				return username + " replied with !ack";
			} else {
				return username + " informs you that your message has been tampered.";
			}
		} else {
			//Return failure message
			return privateAddress;
		}
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		try {
			synchronized(tcpServerListenerThread) {
				serverWriter.println("!lookup " + username);
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}
		return tcpServerListenerThread.getLastResponse();
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
			//close existing serverSocket
			privateComServerSocket.close();
		}
		privateComServerSocket = newPrivateComServerSocket;
		tcpPrivateListenerThread = new TcpPrivateListenerThread(privateComServerSocket, shell);
		//start new Thread that listens to incoming connections and handles them
		new Thread(tcpPrivateListenerThread).start(); //will stop when privateComServerSocket is closed

		try {
			synchronized(tcpServerListenerThread) {
				serverWriter.println("!register " + privateAddress);
				tcpServerListenerThread.wait(5000);
			}
		} catch (InterruptedException e) {
			return "interrupted";
		}

		return tcpServerListenerThread.getLastResponse();
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
			//close existing serverSocket
			privateComServerSocket.close();
		}

		return "exited properly";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in,
				System.out);
		new Thread(client).start();
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
}
