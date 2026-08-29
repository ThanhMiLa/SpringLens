package vn.io.codelearning.springapitester.model;

import java.util.ArrayList;
import java.util.List;

public class GatewayRouteModel {
    private String id;
    private String uri;
    private List<String> pathPredicates = new ArrayList<>();
    private int stripPrefix = 0;
    private String rewritePathRegex;
    private String rewritePathReplacement;
    private String prefixPath;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public List<String> getPathPredicates() { return pathPredicates; }
    public void setPathPredicates(List<String> pathPredicates) { this.pathPredicates = pathPredicates; }

    public int getStripPrefix() { return stripPrefix; }
    public void setStripPrefix(int stripPrefix) { this.stripPrefix = stripPrefix; }

    public String getRewritePathRegex() { return rewritePathRegex; }
    public void setRewritePathRegex(String rewritePathRegex) { this.rewritePathRegex = rewritePathRegex; }

    public String getRewritePathReplacement() { return rewritePathReplacement; }
    public void setRewritePathReplacement(String rewritePathReplacement) { this.rewritePathReplacement = rewritePathReplacement; }

    public String getPrefixPath() { return prefixPath; }
    public void setPrefixPath(String prefixPath) { this.prefixPath = prefixPath; }
}
