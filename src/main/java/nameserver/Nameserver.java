package nameserver;

import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import util.Config;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Nameserver implements INameserverCli, Runnable {

	private static final Log LOGGER = LogFactory.getLog(Nameserver.class);

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private Shell shell;
	private NameserverRequests nameserver;
	private ConcurrentHashMap<String, INameserver> subzones;
	private ConcurrentHashMap<String, String> registeredUsers;

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

		shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		shell.register(this);
	}

	@Override
	public void run() {
		LOGGER.info(componentName + " started");

		//register nameserver
		if (config.listKeys().contains("domain")) { 	// non-root nameserver
			try {
				subzones = new ConcurrentHashMap<>();
				registeredUsers = new ConcurrentHashMap<>();
				nameserver = new NameserverRequests(subzones, registeredUsers);
				Registry registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
				INameserver root = (INameserver) registry.lookup(config.getString("root_id"));
				INameserver nameserverRemote = (INameserver) UnicastRemoteObject.exportObject(nameserver, 0);
				root.registerNameserver(config.getString("domain"), nameserverRemote, nameserverRemote);
			} catch (RemoteException | NotBoundException | InvalidDomainException | AlreadyRegisteredException e) {
				LOGGER.warn("Registration for " + config.getString("domain") + " failed", e);
			}
		} else{											// root name server
			try {
				subzones = new ConcurrentHashMap<>();
				registeredUsers = null;
				nameserver = new NameserverRequests(subzones, registeredUsers);
				Registry registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
				INameserver nameserverRemote = (INameserver) UnicastRemoteObject.exportObject(nameserver, 0);
				registry.bind(config.getString("root_id"), nameserverRemote);
			} catch (RemoteException | AlreadyBoundException e) {
				LOGGER.warn("Registration for root failed", e);
			}
		}

		//start shell
		new Thread(shell).start();
		System.out.println(componentName + " up and waiting for commands!");
	}

	@Command
	@Override
	public String nameservers() {
		List<String> zonesList = new LinkedList<>();
		String zonesString = "";
		int counter = 1;

		for(String iNameserver : subzones.keySet()){
			zonesList.add(iNameserver);
		}
		Collections.sort(zonesList);

		for (String zone : zonesList) {
			zonesString += counter + ": " + zone + "\n";
			counter++;
		}
		return zonesString.trim();
	}

	@Command
	@Override
	public String addresses() {
		List<String> usersList = new LinkedList<>();
		String userString = "";
		int counter = 1;

		for(Map.Entry<String, String> user : registeredUsers.entrySet()){
			usersList.add(user.getKey() + " " + user.getValue());
		}
		Collections.sort(usersList);

		for (String user : usersList) {
			userString += counter + ": " + user + "\n";
		}
		return userString.trim();
	}

	@Command
	@Override
	public String exit() {
		shell.close();

		try {
			if (!UnicastRemoteObject.unexportObject(nameserver, false)) {
				// force unexport
				if (!UnicastRemoteObject.unexportObject(nameserver, true)) {
					LOGGER.info("unexport of " + componentName + " failed");
				}
			}
		} catch (NoSuchObjectException e) {
			LOGGER.warn("unexport of " + componentName + " failed", e);
		}

		return "Shutting down " + componentName;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);
		nameserver.run();
	}

}
