package nameserver;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * Created by user on 08.12.2015.
 */
public class NameserverRequests implements INameserver, Serializable {

    private Map<String, INameserverForChatserver> zones;
    private Map<String, String> users;
    private String domain;

    public NameserverRequests(Map<String, INameserverForChatserver> zones,
                              Map<String, String> users,
                              String domain){
        this.zones = zones;
        this.users = users;
        this.domain = domain;
    }

    @Override
    public void registerNameserver(String domainName, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        Domain domain = new Domain(domainName);
        if(domain.hasSubdomain()){
            ((NameserverRequests)zones.get(domain.getZone())).registerNameserver(domain.getSubdomain(), nameserver, nameserverForChatserver);
        } else{
            this.zones.put(domain.getZone(), nameserverForChatserver);
        }
    }

    @Override
    public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        Domain domain = new Domain(username);
        if(domain.hasSubdomain()){
            (zones.get(domain.getZone())).registerUser(username, address);
        } else{
            this.users.put(domain.getZone(), address);
        }
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
        String [] zones = domain.split("\\.");
        if(zones.length == 0){
            return "root";
        } else{
            return zones[0];
        }
    }
}
