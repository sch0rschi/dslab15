package entities;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Domain {
    private String domain;

    public Domain(String domain) {
        this.domain = domain;
    }

    public boolean hasSubdomain(){
        return domain.indexOf('.') >= 0;
    }

    public String getSubdomain(){
        return domain.substring(0, domain.lastIndexOf('.'));
    }

    public String getZone(){
        String[] split = domain.split("\\.");
        return split[split.length-1];
    }

    public String toString(){
        return domain;
    }

    public boolean isDomain(){
        try{
            InetAddress.getAllByName(domain);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
