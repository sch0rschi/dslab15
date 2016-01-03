package chatserver;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpListenerThread implements Runnable {

	private static Log LOGGER = LogFactory.getLog(TcpListenerThread.class);
	Chatserver chatserver;
	ServerSocket serverSocket;


	public TcpListenerThread(Chatserver chatserver, ServerSocket serverSocket) {
		this.chatserver = chatserver;
		this.serverSocket = serverSocket;
	}

	@Override
	public void run() {
		LOGGER.info("TcpListenerThread started");

		ExecutorService channelPool = Executors.newFixedThreadPool(chatserver.getUsernames().size() + 1);
		Socket socket = null;
		Set<Socket> sockets = new HashSet<>();


		try {
			while (true) {
				// wait for Client to connect
				socket = serverSocket.accept();

				//assign socket to new TcpChannelThread
				channelPool.execute(new TcpChannelThread(chatserver, socket));
				sockets.add(socket);
			}
		} catch (SocketException e) {
			//System.out.println("SocketException caught in TcpListenerThread");
		} catch (IOException e) {
			System.err
			.println("IOException caught while waiting for client: "
					+ e.getMessage());
		} finally {
			channelPool.shutdown();
			//System.out.println("Shutting down TcpChannelPool");

			for (Socket sock : sockets) {
				try {
					sock.getInputStream().close();
					sock.getOutputStream().close();
					sock.close();
				} catch (IOException e) {
					//ignore
				}
			}

			if (socket != null && !socket.isClosed())
				try {
					socket.close();
				} catch (IOException ex) {
					// Ignored because we cannot handle it
				}
			//System.out.println("INFO: TcpListenerThread finished");
		}

	}

}
