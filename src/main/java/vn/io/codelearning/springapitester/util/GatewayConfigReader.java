package vn.io.codelearning.springapitester.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.scanner.SpringConfigResolutionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GatewayConfigReader {

    public static boolean hasGatewayDependency(Module module) {
        if (module == null || module.isDisposed()) return false;
        try {
            OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
            for (OrderEntry entry : orderEntries) {
                if (entry instanceof LibraryOrderEntry libraryEntry) {
                    String libraryName = libraryEntry.getLibraryName();
                    if (libraryName != null) {
                        String normalizedName = libraryName.toLowerCase(Locale.ROOT);
                        if (normalizedName.contains("gateway") || normalizedName.contains("zuul")) {
                            return true;
                        }
                    }
                }
            }
            String moduleName = module.getName();
            return moduleName != null && moduleName.toLowerCase(Locale.ROOT).contains("gateway");
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static GatewayConfig findGatewayConfig(Project project) {
        if (project == null || project.isDisposed()) return new GatewayConfig();
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(project);
        return service != null ? service.resolveGatewayConfig() : new GatewayConfig();
    }

    public static class GatewayConfig {
        public String port = "8080";
        public boolean discoveryLocatorEnabled = false;
        public List<GatewayRouteModel> routes = new ArrayList<>();
        public String sourceFile = "";
        public boolean isFallback = true;
        public boolean gatewayDetected = false;
        public List<String> diagnostics = new ArrayList<>();
    }
}
