package vn.io.codelearning.springapitester.scanner;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.execution.ParametersListUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RunConfigurationProfileReader {

    private RunConfigurationProfileReader() {
    }

    static Map<String, String> readProfiles(Project project, @Nullable Module module, List<String> diagnostics) {
        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings selected = runManager.getSelectedConfiguration();
        List<Element> candidates = new ArrayList<>();
        for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
            Element configuration = new Element("configuration");
            configuration.setAttribute("type", settings.getType().getId());
            try {
                settings.getConfiguration().writeExternal(configuration);
            } catch (WriteExternalException exception) {
                diagnostics.add("Unable to read run configuration: " + settings.getName());
                continue;
            }
            if (!isApplicationConfiguration(configuration) || !matchesModule(configuration, module)) continue;
            if (settings == selected) return readProfileProperties(configuration);
            candidates.add(configuration);
        }
        if (candidates.size() == 1) return readProfileProperties(candidates.get(0));
        if (candidates.size() > 1) {
            diagnostics.add("Multiple run configurations match this application. Select its run configuration and reload.");
        }
        return Map.of();
    }

    private static boolean isApplicationConfiguration(Element configuration) {
        String type = configuration.getAttributeValue("type");
        return "Application".equals(type) || "SpringBootApplicationConfigurationType".equals(type);
    }

    private static boolean matchesModule(Element configuration, @Nullable Module module) {
        if (module == null) return true;
        Element configuredModule = configuration.getChild("module");
        if (configuredModule == null) return false;
        String moduleName = module.getName();
        String configuredName = configuredModule.getAttributeValue("name", "");
        if (moduleName.equals(configuredName)) return true;
        return moduleName.endsWith(".main") && moduleName.substring(0, moduleName.length() - 5).equals(configuredName);
    }

    static Map<String, String> readProfileProperties(Element configuration) {
        Map<String, String> profiles = new LinkedHashMap<>();
        Element envs = configuration.getChild("envs");
        if (envs != null) {
            for (Element env : envs.getChildren("env")) {
                String name = env.getAttributeValue("name");
                if ("SPRING_PROFILES_ACTIVE".equals(name)) {
                    profiles.put("spring.profiles.active", env.getAttributeValue("value", "").trim());
                } else if ("SPRING_PROFILES_DEFAULT".equals(name)) {
                    profiles.put("spring.profiles.default", env.getAttributeValue("value", "").trim());
                }
            }
        }
        readParameterProfiles(optionValue(configuration, "VM_PARAMETERS"), "-D", profiles);
        String activeProfiles = optionValue(configuration, "ACTIVE_PROFILES");
        if (!activeProfiles.isBlank()) profiles.put("spring.profiles.active", activeProfiles.trim());
        readParameterProfiles(optionValue(configuration, "PROGRAM_PARAMETERS"), "--", profiles);
        return profiles;
    }

    private static String optionValue(Element configuration, String name) {
        for (Element option : configuration.getChildren("option")) {
            if (name.equals(option.getAttributeValue("name"))) return option.getAttributeValue("value", "");
        }
        return "";
    }

    private static void readParameterProfiles(String parameters, String prefix, Map<String, String> profiles) {
        for (String argument : ParametersListUtil.parse(parameters)) {
            if (!argument.startsWith(prefix)) continue;
            int separator = argument.indexOf('=');
            if (separator < 0) continue;
            String key = argument.substring(prefix.length(), separator);
            if ("spring.profiles.active".equals(key) || "spring.profiles.default".equals(key)) {
                profiles.put(key, argument.substring(separator + 1).trim());
            }
        }
    }
}
