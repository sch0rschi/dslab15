package nameserver;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface INameserverForChatserver extends Remote {

	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException,
			InvalidDomainException;

	public INameserverForChatserver getNameserver(String zone)
			throws RemoteException;

	public String lookup(String username) throws RemoteException;

}
