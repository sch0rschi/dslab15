package chatserver;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpListenerThread implements Runnable {

	private static Log LOGGER = LogFactory.getLog(UdpListenerThread.class);
	Chatserver chatserver;
	DatagramSocket datagramSocket;
	
	public UdpListenerThread(Chatserver chatserver, DatagramSocket datagramSocket) {
		this.chatserver = chatserver;
		this.datagramSocket = datagramSocket;
	}
	
	@Override
	public void run() {
		LOGGER.info("UdpListenerThread started");
		
		ExecutorService channelPool = Executors.newFixedThreadPool(2);
		byte[] buffer;
		DatagramPacket packet;
		
		try {
			while (true) {
				buffer = new byte[256]; //"!list" isn't even that long
				packet = new DatagramPacket(buffer, buffer.length);

				// wait for incoming packets from client
				datagramSocket.receive(packet);
				
				// start new Thread for request handling and response
				channelPool.execute(new UdpChannelThread(chatserver, datagramSocket, packet));
			}

		} catch (SocketException e) {
			////System.out.println("SocketException caught in UdpListenerThread");
		} catch (IOException e) {
			System.err
					.println("Error occurred while waiting for/handling packets: "
							+ e.getMessage());
		} finally {
			channelPool.shutdown();
			////System.out.println("Shutting down UdpChannelPool");
			////System.out.println("INFO: UdpListenerThread finished");
		}
	}

}
