package nameserver;

public class Domain {
    private String domain;

    public Domain(String domain) {
        this.domain = domain;
    }

    public String getSubdomain(){
        return domain.substring(0, domain.lastIndexOf('.'));
    }

    public boolean hasSubdomain(){
        return domain.indexOf('.') >= 0;
    }

    public String getZone(){
        String[] split = domain.split("\\.");
        return split[split.length-1];
    }
}
