package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import cli.Shell;

public class TcpPrivateChannelThread implements Runnable {

	private Socket socket;
	private Shell shell;
	private BufferedReader reader;
	private PrintWriter writer;

	public TcpPrivateChannelThread(Socket socket, Shell shell) throws IOException {
		this.socket = socket;
		this.shell = shell;

		// prepare the input reader for the socket
		reader = new BufferedReader(
				new InputStreamReader(socket.getInputStream()));
		// prepare the writer for responding to clients requests
		writer = new PrintWriter(socket.getOutputStream(), true);
	}

	@Override
	public void run() {
		String privateMessage;

		try {

			privateMessage = reader.readLine(); 
			shell.writeLine(privateMessage); //print it to shell
			writer.println("!ack"); //respond with acknowledgment message

		} catch (IOException e) {
			System.err
			.println("Error occurred while waiting for/communicating with other client: "
					+ e.getMessage());
		} finally {
			try{
				if (socket != null && !socket.isClosed()) {
					socket.getInputStream().close();
					socket.getOutputStream().close();
					socket.close();
				}
			} catch (IOException e) {
				// Ignored because we cannot handle it
			}
		}
	}
}
