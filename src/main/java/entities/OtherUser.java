package entities;

public class OtherUser {
	
	private String name;
	private String ip;
	private Integer port;
	
	public OtherUser(String name, String ip, int port) {
		this.name = name;
		this.ip = ip;
		this.port = port;
	}
	
	public String getName() {
		return name;
	}
	
	public String getIp() {
		return ip;
	}
	
	public Integer getPort() {
		return port;
	}

}
