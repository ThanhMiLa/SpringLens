package vn.io.codelearning.springapitester.client;

import java.util.Locale;
import java.util.Objects;

/**
 * Encapsulates explicit user consent for disabling certificate validation on a specific local development host.
 */
public final class InsecureTlsConsent {
    public static final int CURRENT_POLICY_VERSION = 1;

    private final String normalizedHost;
    private final int policyVersion;

    public InsecureTlsConsent(String host) {
        this(host, CURRENT_POLICY_VERSION);
    }

    public InsecureTlsConsent(String host, int policyVersion) {
        this.normalizedHost = normalizeHost(host);
        this.policyVersion = policyVersion;
    }

    public static String normalizeHost(String host) {
        if (host == null || host.isBlank()) return "";
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String getNormalizedHost() {
        return normalizedHost;
    }

    public int getPolicyVersion() {
        return policyVersion;
    }

    public boolean matchesHost(String host) {
        if (host == null || host.isBlank()) return false;
        return this.policyVersion == CURRENT_POLICY_VERSION
                && Objects.equals(this.normalizedHost, normalizeHost(host));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsecureTlsConsent that)) return false;
        return policyVersion == that.policyVersion && Objects.equals(normalizedHost, that.normalizedHost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedHost, policyVersion);
    }

    @Override
    public String toString() {
        return "InsecureTlsConsent{" +
                "normalizedHost='" + normalizedHost + '\'' +
                ", policyVersion=" + policyVersion +
                '}';
    }
}
