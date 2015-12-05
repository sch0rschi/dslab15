package chatserver;

public class User {
	
	private String name; //doesn't change after initialization
	private String password; //doesn't change after initialization
	private String ip;
	private Integer port;
	private TcpChannel channel;
	
	public User(String name, String password) {
		this.name = name;
		this.password = password;
	}
	
	public String getName() {
		return name;
	}
	
	/**
	 * Check if the given String equals the password.
	 * @param pass not null
	 * @return pass.equals(password)
	 */
	public boolean checkPassword(String pass) {
		return pass.equals(password);
	}
	
	/**
	 * Tell if user is currently logged in.
	 * @return true iff user is logged in
	 */
	public boolean isLoggedIn() {
		return channel != null;
	}
	
	/**
	 * Login the user iff password is correct and user is not already logged in
	 * @param pass not null
	 * @param channel not null
	 * @return true iff login was successful
	 */
	public synchronized boolean login(String pass, TcpChannel channel) {
		if (checkPassword(pass) && !isLoggedIn()) {
			this.channel = channel;
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Register IP and port of the user's serverSocket, where he can receive private messages.
	 * 
	 * @param ip not null, not empty
	 * @param port positive integer
	 */
	public synchronized void registerPrivateAddress(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}
	
	public synchronized void unregisterPrivateAddress() {
		this.ip = null;
		this.port = null;
	}
	
	public synchronized boolean isRegisteredWithPrivateAddress() {
		return (ip != null) && (port != null);
	}
	
	/**
	 * Return private address of this user. If user did not register a private address before, return {@code null}.
	 * @return IP and port in form "ip:form". If ip or port are {@code null}, return {@code null}.
	 */
	public synchronized String getPrivateAddress() {
		if(isRegisteredWithPrivateAddress()) {
			return ip + ":" + port;
		} else {
			return null;
		}
	}
	
	/**
	 * Log out user. User has to be logged in before.
	 * @param channel must match the currently used channel.
	 * @return true iff user was logged in and logout was successful.
	 */
	public synchronized boolean logout(TcpChannel channel) {
		if(isLoggedIn() && channel.equals(this.channel)) {
			this.channel = null;
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Return name and online status of this user
	 * @return name and online status
	 */
	@Override
	public String toString() {
		String onlineStatus = (isLoggedIn()) ? "online" : "offline"; 
		return name + ": " + onlineStatus + "\n";
	}
}
