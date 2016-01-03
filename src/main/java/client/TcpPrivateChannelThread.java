package client;

import java.io.*;
import cli.Shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import cli.Shell;
import org.bouncycastle.util.encoders.Base64;
import util.Keys;

import javax.crypto.Mac;

public class TcpPrivateChannelThread implements SignatedChannel, Runnable {

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
		String privateMessage; //format: <HMAC> !msg <message>
		boolean messageValid;
		try {
			do {
				privateMessage = read();
				messageValid = verify(privateMessage);

				//extract actual message text
				int firstSpaceIndex = privateMessage.indexOf(' '); //space after <HMAC>
				int secondSpaceIndex = privateMessage.indexOf(' ', firstSpaceIndex + 1);
				String actualMessageText = privateMessage.substring(secondSpaceIndex + 1);

				if (messageValid) {
					//respond with "<HMAC> !ack"
					write("!ack " + actualMessageText);
				} else {
					//respond with "<HMAC> !tampered <message>"
					write("!tampered " + actualMessageText);
				}

				shell.writeLine(actualMessageText); //print it to shell

			} while (!messageValid);

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

	/**
	 * Prepend <HMAC> to wholeMessage and send it to corresponding client
	 * @param wholeMessage The message-construct for which the signature shall be created
	 */
	public void write(String wholeMessage) {
		byte[] hash = createHash(wholeMessage);
		byte[] hashBase64 = Base64.encode(hash);
		String hashBase64String = Arrays.toString(hashBase64);
		writer.println(hashBase64String + " " + wholeMessage); //respond with acknowledgment message
	}

	/**
	 * Wait for message from other client and return it.
	 * @return the received message
	 */
	public String read() throws IOException {
		return reader.readLine();
	}

	/**
	 * Checks correctness of prepended HMAC of a message
	 * @param message non-null non-empty String with a base64 encoded HMAC, a white-space and a message
	 * @return true iff message content matches the HMAC
	 */
	public boolean verify(String message) {
		try {
			String[] messageParts = message.split(" "); //messageParts[0] is <HMAC>
			//base64-decode <HMAC>
			byte[] receivedHash = Base64.decode(messageParts[0]);
			String otherMessageParts = message.substring(message.indexOf(' ') + 1); //= "!msg <message>"
			byte[] actualHash = createHash(otherMessageParts); //= hash calculated by message content

			return MessageDigest.isEqual(receivedHash, actualHash);
		} catch (Exception e) {
			//message format did not fit
			return false;
		}
	}

	/**
	 * Generate a hash signature for the given message with the HmacSHA256 algorithm.
	 *
	 * @param message not null
	 * @return hash value as byte array
	 */
	private byte[] createHash(String message) {
		Key secretKey;
		Mac hMac;
		try {
			secretKey = Keys.readSecretKey(new File("./keys/hmac.key"));
			hMac = Mac.getInstance(secretKey.getAlgorithm());
			hMac.init(secretKey);
		} catch (IOException e) {
			System.out.println("Failure during signation: Couldn't read \"hmac.key\" file: " + e.getMessage());
			return null;
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Failure during signation: Invalid algorithm");
			return null;
		} catch (InvalidKeyException e) {
			System.out.println("Failure during signation: InvalidKeyException");
			return null;
		}

		// generate hash
		hMac.update(message.getBytes());
		byte[] hash = hMac.doFinal();
		return hash;
	}
}
