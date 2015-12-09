package nameserver;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Encapsulates the nameserver's remotely executable methods
 */
public class NameserverRequests implements INameserver {
    private static final Logger LOGGER = Logger.getAnonymousLogger();

    private ConcurrentHashMap<String, INameserver> subzones;
    private ConcurrentHashMap<String, String> registeredUsers;

    public NameserverRequests(ConcurrentHashMap<String, INameserver> subzones, ConcurrentHashMap<String, String> registeredUsers) {
        this.subzones = subzones;
        this.registeredUsers = registeredUsers;
    }

    @Override
    public void registerNameserver(String domainString, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

        Domain domain = new Domain(domainString);

        if (domain.hasSubdomain()) {
            subzones.get(domain.getZone()).registerNameserver(domain.getSubdomain().toString(), nameserver, nameserverForChatserver);
        } else {
            subzones.put(domain.getZone(), nameserver);
        }
    }

    @Override
    public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

        Domain domain = new Domain(username);

        if (domain.hasSubdomain()) {
            subzones.get(domain.getZone()).registerUser(domain.getSubdomain().toString(), address);
        } else {
            registeredUsers.put(username, address);
        }
    }

    @Override
    public INameserverForChatserver getNameserver(String zone) throws RemoteException {
        return subzones.get(zone);
    }

    @Override
    public String lookup(String username) throws RemoteException {
        return registeredUsers.get(username);
    }
}
