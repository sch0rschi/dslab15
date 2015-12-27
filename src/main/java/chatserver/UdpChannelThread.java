package chatserver;

import entities.User;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Set;

public class UdpChannelThread implements UdpChannel, Runnable {

	private Chatserver chatserver;
	private DatagramSocket datagramSocket;
	private DatagramPacket packet;
	
	public UdpChannelThread(Chatserver chatserver, DatagramSocket datagramSocket, DatagramPacket packet) {
		this.chatserver = chatserver;
		this.datagramSocket = datagramSocket;
		this.packet = packet;
	}
	
	/**
	 * Send a datagram packet to the sender of the originally received packet.
	 */
	@Override
	public void write(String message) throws IOException {
		InetAddress address = packet.getAddress();
		int port = packet.getPort();
		byte[] buffer = message.getBytes();
		
		DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length, address,
				port);
		
		
		datagramSocket.send(responsePacket);
	}
	
	/**
	 * List online users in alphabetical order.
	 * @return alphabetical list of users who are online
	 */
	private String list() {
		String onlineUsers = "Users online:";
		Set<String> usernames = chatserver.getUsernames();
		for (String username : usernames) {
			User user = chatserver.getUser(username);
			if (user.isLoggedIn()) {
				onlineUsers += "\n    " + username;
			}
		}
		return onlineUsers;
	}

	@Override
	public void run() {
		//System.out.println("UdpChannel started.");
		
		String request = new String(packet.getData());
		//System.out.println("Received request-packet from client: " + request);
		
		String response;
		if (request.startsWith("!list")) {
			response = list();
		} else {
			response = "Unknown command. I'm sorry.";
		}
		
		try {
			write(response);
		} catch (IOException e) {
			//System.out.println("Sending datagram packet failed: " + e.getMessage());
		} finally {
			//System.out.println("UdpChannel finished.");
		}
	}

}
