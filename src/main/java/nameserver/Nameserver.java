package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import util.Config;

public class Nameserver implements INameserverCli, Runnable {

	private static Log LOGGER = LogFactory.getLog(Nameserver.class);

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private Shell shell;

	private String domain;
	private INameserver nameserver;
	private Map<String, INameserverForChatserver> zones;
	private Map<String, String> users;

	private Registry registry;

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
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		zones = Collections.synchronizedMap(new HashMap<String, INameserverForChatserver>());

		try{
			if(config.listKeys().contains("domain")){ 			// not Root Nameserver
				domain = config.getString("domain");
				users = Collections.synchronizedMap(new HashMap<String, String>());
				nameserver = new NameserverRequests(zones, users, domain);
				nameserver.registerNameserver(domain, nameserver, nameserver);
			} else{ 											// Root Nameserver
				domain = "";
				users = null;
				nameserver = new NameserverRequests(zones, users, domain);

				int port = config.getInt("registry.port");
				registry = LocateRegistry.createRegistry(port);
				registry.bind(config.getString("root-id"), nameserver);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			e.printStackTrace();
		} catch (AlreadyRegisteredException e) {
			e.printStackTrace();
		} catch (InvalidDomainException e) {
			e.printStackTrace();
		}

		shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		shell.register(this);
	}

	@Override
	public void run() {
		LOGGER.info("Nameserver started");

		//start shell
		new Thread(shell).start();
		System.out.println(getClass().getName()
				+ " up and waiting for commands!");
	}

	@Override
	@Command
	public String nameservers() throws IOException {
		String nameserversString = "";
		List<String> nameserversList = new LinkedList<>();
		for (INameserverForChatserver zone : zones.values()) {
			nameserversList.add(((NameserverRequests) zone).zone());
		}
		Collections.sort(nameserversList);
		for (String zone : nameserversList){
			nameserversString += zone + "\n";
		}

		return nameserversString.trim();
	}

	@Override
	@Command
	public String addresses() throws IOException {
		String usersString = "";
		List<String> usersList = new LinkedList<>();
		for(Map.Entry<String, String> user : users.entrySet()){
			usersList.add(user.getKey() + " " + user.getValue());
		}
		Collections.sort(usersList);
		for(String user : usersList){
			usersString += user;
		}
		return usersString.trim();
	}

	@Override
	@Command
	public String exit() throws IOException {
		LOGGER.debug("Preparing exit");
		shell.close();
		return "Shutting down program...";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);
		new Thread(nameserver).start();
	}
}
