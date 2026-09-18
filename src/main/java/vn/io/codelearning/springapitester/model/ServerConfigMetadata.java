package vn.io.codelearning.springapitester.model;

/**
 * Immutable configuration provenance prepared by the scanner for UI display.
 */
public record ServerConfigMetadata(
        String sourceFile,
        boolean fallback,
        boolean unresolvedPlaceholder
) {

    public ServerConfigMetadata {
        sourceFile = sourceFile != null ? sourceFile : "";
    }
}
