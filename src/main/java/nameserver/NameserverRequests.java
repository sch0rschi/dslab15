package nameserver;

import entities.Domain;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

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

        if(domain.isDomain()){
            if (domain.hasSubdomain()) {
                subzones.get(domain.getZone()).registerNameserver(domain.getSubdomain().toString(), nameserver, nameserverForChatserver);
            } else if(subzones.containsKey(domain.getZone())){
                throw new AlreadyRegisteredException(domain.getZone() + " already registered in this domain.");
            } else{
                subzones.put(domain.getZone(), nameserver);
            }
        } else {
            throw new InvalidDomainException(domain.toString() + " is no valid domain.");
        }

    }

    @Override
    public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

        Domain domain = new Domain(username);

        if(domain.isDomain()){
            if (domain.hasSubdomain()) {
                subzones.get(domain.getZone()).registerUser(domain.getSubdomain().toString(), address);
            } else if(registeredUsers.containsKey(username)){
                throw new AlreadyRegisteredException(username + " is already registered in this domain.");
            } else {
                registeredUsers.put(username, address);
            }
        }else {
            throw new InvalidDomainException(domain.toString() + " is no valid domain.");
        }
    }

    @Override
    public INameserverForChatserver getNameserver(String zone) throws RemoteException {
        if(subzones.containsKey(zone)){
            return subzones.get(zone);
        } else{
            throw new RemoteException("No Subdomain " + zone + " registered.");
        }

    }

    @Override
    public String lookup(String username) throws RemoteException {
        if(registeredUsers.containsKey(username)){
            return registeredUsers.get(username);
        } else{
            return username + " is not registered.";
        }
    }
}
