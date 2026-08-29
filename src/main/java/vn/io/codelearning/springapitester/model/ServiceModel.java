package vn.io.codelearning.springapitester.model;

public class ServiceModel {
    private String name;
    private String directBaseUrl;
    private boolean isGateway;

    public ServiceModel(String name, String directBaseUrl, boolean isGateway) {
        this.name = name;
        this.directBaseUrl = directBaseUrl;
        this.isGateway = isGateway;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDirectBaseUrl() { return directBaseUrl; }
    public void setDirectBaseUrl(String directBaseUrl) { this.directBaseUrl = directBaseUrl; }

    public boolean isGateway() { return isGateway; }
    public void setGateway(boolean gateway) { isGateway = gateway; }
    
    @Override
    public String toString() {
        return name;
    }
}
