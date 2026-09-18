package vn.io.codelearning.springapitester.client;

import okhttp3.HttpUrl;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.ParameterModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies endpoint query parameters to a URL without duplicating them.
 */
public final class QueryParameterUrlBuilder {

    private QueryParameterUrlBuilder() {
    }

    public static String applyQueryParameters(String url, List<ParameterModel> parameters) {
        return rebuildUrl(url, parameters, true);
    }

    public static String removeQueryParameters(String url, List<ParameterModel> parameters) {
        return rebuildUrl(url, parameters, false);
    }

    private static String rebuildUrl(String url, List<ParameterModel> parameters, boolean includeParameterValues) {
        if (url == null || url.isBlank()) {
            return url != null ? url : "";
        }

        Set<String> managedNames = getManagedQueryParameterNames(parameters);
        if (managedNames.isEmpty()) {
            return url;
        }

        UrlParts urlParts = UrlParts.parse(url);
        List<QueryParameter> existingParameters = parseExistingQuery(urlParts.query());
        if (existingParameters == null) {
            return url;
        }

        List<QueryParameter> mergedParameters = new ArrayList<>();
        for (QueryParameter parameter : existingParameters) {
            if (!managedNames.contains(parameter.name())) {
                mergedParameters.add(parameter);
            }
        }

        if (includeParameterValues && parameters != null) {
            for (ParameterModel parameter : parameters) {
                if (!isQueryParameterWithValue(parameter)) {
                    continue;
                }
                mergedParameters.add(new QueryParameter(
                        parameter.getName(),
                        RequestValidationUtil.resolveParamValue(parameter)
                ));
            }
        }

        String encodedQuery = encodeQuery(mergedParameters);
        return urlParts.baseUrl()
                + (encodedQuery.isEmpty() ? "" : "?" + encodedQuery)
                + urlParts.fragment();
    }

    private static Set<String> getManagedQueryParameterNames(List<ParameterModel> parameters) {
        Set<String> names = new LinkedHashSet<>();
        if (parameters == null) {
            return names;
        }
        for (ParameterModel parameter : parameters) {
            if (parameter != null
                    && parameter.getParamType() == ParamTypeEnum.QUERY_PARAM
                    && parameter.getName() != null
                    && !parameter.getName().isBlank()) {
                names.add(parameter.getName());
            }
        }
        return names;
    }

    private static boolean isQueryParameterWithValue(ParameterModel parameter) {
        if (parameter == null
                || parameter.getParamType() != ParamTypeEnum.QUERY_PARAM
                || !parameter.isEnabled()
                || parameter.getName() == null
                || parameter.getName().isBlank()) {
            return false;
        }
        return !RequestValidationUtil.resolveParamValue(parameter).isBlank();
    }

    private static List<QueryParameter> parseExistingQuery(String query) {
        if (query.isEmpty()) {
            return new ArrayList<>();
        }

        HttpUrl parsed = HttpUrl.parse("http://localhost/?" + query);
        if (parsed == null) {
            return null;
        }

        List<QueryParameter> parameters = new ArrayList<>();
        for (int index = 0; index < parsed.querySize(); index++) {
            parameters.add(new QueryParameter(
                    parsed.queryParameterName(index),
                    parsed.queryParameterValue(index)
            ));
        }
        return parameters;
    }

    private static String encodeQuery(List<QueryParameter> parameters) {
        HttpUrl.Builder builder = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost");
        for (QueryParameter parameter : parameters) {
            builder.addQueryParameter(parameter.name(), parameter.value());
        }
        String encodedQuery = builder.build().encodedQuery();
        return encodedQuery != null ? encodedQuery : "";
    }

    private record QueryParameter(String name, String value) {
    }

    private record UrlParts(String baseUrl, String query, String fragment) {

        private static UrlParts parse(String url) {
            int fragmentIndex = url.indexOf('#');
            String fragment = fragmentIndex >= 0 ? url.substring(fragmentIndex) : "";
            String urlWithoutFragment = fragmentIndex >= 0 ? url.substring(0, fragmentIndex) : url;

            int queryIndex = urlWithoutFragment.indexOf('?');
            if (queryIndex < 0) {
                return new UrlParts(urlWithoutFragment, "", fragment);
            }
            return new UrlParts(
                    urlWithoutFragment.substring(0, queryIndex),
                    urlWithoutFragment.substring(queryIndex + 1),
                    fragment
            );
        }
    }
}
