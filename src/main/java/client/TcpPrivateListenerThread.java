package client;

import cli.Shell;
import util.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpPrivateListenerThread implements Runnable {

	private ServerSocket serverSocket;
	private Shell shell;
	private Config config;

	public TcpPrivateListenerThread(ServerSocket serverSocket, Shell shell, Config config) {
		this.serverSocket = serverSocket;
		this.shell = shell;
		this.config = config;
	}

	@Override
	public void run() {
		ExecutorService channelPool = Executors.newFixedThreadPool(2);
		Socket socket = null;

		try {
			while (true) {
				// wait for other clients to connect
				socket = serverSocket.accept();

				//assign socket to new TcpChannelThread
				channelPool.execute(new TcpPrivateChannelThread(socket, shell, config));
			}
		} catch (SocketException e) {
			//System.out.println("SocketException caught in TcpPrivateListenerThread");
		} catch (IOException e) {
			System.err
			.println("Error occurred while waiting for other client: "
					+ e.getMessage());
		} finally {
			channelPool.shutdown();
			//System.out.println("Shutting down TcpChannelPool");

			if (socket != null && !socket.isClosed()) {
				try {
					socket.close();
				} catch (IOException ex) {
					// Ignored because we cannot handle it
				}
			}
		}
	}

}
