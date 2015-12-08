package nameserver;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.rmi.RemoteException;
import java.util.HashMap;

/**
 * Created by user on 08.12.2015.
 */
public class NameserverRequests implements INameserver {

    private HashMap<String, INameserverForChatserver> zones;
    private HashMap<String, String> users;
    private String domain;

    public NameserverRequests(HashMap<String, INameserverForChatserver> zones,
                              HashMap<String, String> users,
                              String domain){
        this.zones = zones;
        this.users = users;
        this.domain = domain;
    }

    @Override
    public void registerNameserver(String domain, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

        String subdomain = domain.replace(this.domain, "");
        String[] split = subdomain.split(".");
        String zone = split[split.length-1];
        if(zone.length() == 1){
            zones.put(zone, nameserverForChatserver);
        } else{
            ((NameserverRequests)zones.get(zone)).registerNameserver(domain, nameserver, nameserverForChatserver);
        }
    }

    @Override
    public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        users.put(username, address);
    }

    @Override
    public INameserverForChatserver getNameserver(String zone) throws RemoteException {
        return zones.get(zone);
    }

    @Override
    public String lookup(String username) throws RemoteException {
        return users.get(username);
    }

    public String toString(){
        return domain;
    }

    public String zone(){
        String [] zones = domain.split(".");
        return zones[0];
    }
}
