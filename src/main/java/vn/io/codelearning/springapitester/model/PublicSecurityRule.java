package vn.io.codelearning.springapitester.model;

import org.jetbrains.annotations.Nullable;

public class PublicSecurityRule {
    private String pathPattern;
    private HttpMethodEnum httpMethod;

    public PublicSecurityRule(String pathPattern, @Nullable HttpMethodEnum httpMethod) {
        this.pathPattern = pathPattern;
        this.httpMethod = httpMethod;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    @Nullable
    public HttpMethodEnum getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(@Nullable HttpMethodEnum httpMethod) {
        this.httpMethod = httpMethod;
    }

    @Override
    public String toString() {
        return "PublicSecurityRule{" +
                "pathPattern='" + pathPattern + '\'' +
                ", httpMethod=" + httpMethod +
                '}';
    }
}
