package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import entities.User;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cli.Command;
import cli.Shell;
import util.Config;

public class Chatserver implements IChatserverCli, Runnable {
	
	private static Log LOGGER = LogFactory.getLog(Chatserver.class);

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private Shell shell;
	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;
	private Thread tcpListenerThread;
	private Thread udpListenerThread;
	private Map<String, User> users; //Map<username, User>
	private Set<TcpChannel> activeTcpChannels;

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
	public Chatserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		this.activeTcpChannels = new HashSet<>();

		try {
			this.serverSocket = new ServerSocket(this.config.getInt("tcp.port"));
		} catch (IOException e) {
			throw new Error("Instantiation of server socket failed.", e);
		}
		try {
			this.datagramSocket = new DatagramSocket(this.config.getInt("udp.port"));
		} catch (IOException e) {
			throw new Error("Instantiation of datagram socket failed.", e);
		}
		
		tcpListenerThread = new Thread(new TcpListenerThread(this, serverSocket));
		udpListenerThread = new Thread(new UdpListenerThread(this, datagramSocket));
		

		/* Retrieve user name and password for all registered users */
		Config userConfig = new Config("user");
		Set<String> userKeys = userConfig.listKeys(); //set of registered users
		users = new HashMap<>(); //Map<username, password>

		//erase the ".password" at the end of each user name
		for(String userKey : userKeys) {
			int suffixPosition = userKey.lastIndexOf(".password");
			String username = userKey.substring(0, suffixPosition);
			String password = userConfig.getString(userKey);
			users.put(username, new User(username, password));
		}
		
		shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		shell.register(this);
	}

	@Override
	public void run() {
		Chatserver.LOGGER.info("Chatserver started");
		
		//start shell
		new Thread(shell).start();
		System.out.println(getClass().getName()
				+ " up and waiting for commands!");
		
		tcpListenerThread.start();
		udpListenerThread.start();
	}

	@Override
	@Command
	public String users() {
		String userList = new String();
		for (String username : getUsernames()) {
			User user = getUser(username);
			userList += user.toString();
		}
		return userList;
	}

	@Override
	@Command
	public String exit() throws IOException {
		Chatserver.LOGGER.debug("Preparing exit");
		
		serverSocket.close();
		datagramSocket.close();
		
		shell.close();
		return "Shutting down program...";
	}
	
	/**
	 * Return the User object corresponding to {@code username}
	 * @param username not null
	 * @return User object or null (if no such user exists)
	 */
	public User getUser(String username) {
		return users.get(username);
	}
	
	/**
	 * Return an alphabetically ordered set with all user names.
	 * @return an alphabetically ordered set with all user names.
	 */
	public Set<String> getUsernames() {
		return new TreeSet<>(users.keySet());
	}
	
	/**
	 * Add {@code channel} to set of active TcpChannels.
	 * 
	 * @param channel not null
	 */
	public void registerTcpChannel(TcpChannel channel) {
		synchronized (activeTcpChannels) {
			activeTcpChannels.add(channel);
		}
	}
	
	/**
	 * Remove {@code channel} from set of active TcpChannels.
	 * 
	 * @param channel not null
	 */
	public void unregisterTcpChannel(TcpChannel channel) {
		synchronized (activeTcpChannels) {
			activeTcpChannels.remove(channel);
		}
	}
	
	/**
	 * Write message to all active TcpChannels except {@code sender}.
	 * 
	 * @param sender not null
	 * @param message not null
	 */
	public void writeAll(TcpChannel sender, String message) throws IOException {
		synchronized (activeTcpChannels) {
			for (TcpChannel ch : activeTcpChannels) {
				if (!(ch.equals(sender))) {
					ch.write("[public]" + message);
				}
			}
		}
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0],
				new Config("chatserver"), System.in, System.out);
		new Thread(chatserver).start();
	}

	public Config getConfig() {
		return config;
	}
}
