package entities;

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
        return domain.matches("[A-z0-9.]+") && !domain.contains("..");
    }
}
