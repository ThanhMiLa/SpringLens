package vn.io.codelearning.springapitester.client;

import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.ParameterModel;
import vn.io.codelearning.springapitester.scanner.SpringUrlUtils;

import java.util.List;

/**
 * Resolves populated Spring path-variable placeholders in a URL template.
 */
public final class PathVariableUrlResolver {

    private PathVariableUrlResolver() {
    }

    public static String resolvePathVariables(String urlTemplate, List<ParameterModel> parameters) {
        if (urlTemplate == null || parameters == null) {
            return urlTemplate;
        }

        String resolvedUrl = urlTemplate;
        for (ParameterModel parameter : parameters) {
            if (parameter == null || parameter.getParamType() != ParamTypeEnum.PATH_VARIABLE) {
                continue;
            }

            String value = RequestValidationUtil.resolveParamValue(parameter);
            if (!value.trim().isEmpty()) {
                resolvedUrl = SpringUrlUtils.replacePathVariable(resolvedUrl, parameter.getName(), value);
            }
        }
        return resolvedUrl;
    }
}
