package vn.io.codelearning.springapitester.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Động cơ thực thi HTTP Client, được quản lý theo phạm vi Project (Project Service).
 */
public class HttpClientService implements Disposable {

    private final Project project;
    private final OkHttpClient secureClient;
    private volatile OkHttpClient unsafeLocalClient;
    private final Gson gson;
    private final InMemoryCookieJar cookieJar;
    private final Set<Call> activeCalls = ConcurrentHashMap.newKeySet();

    public HttpClientService() {
        this(null);
    }

    public HttpClientService(Project project) {
        this.project = project;
        this.cookieJar = new InMemoryCookieJar();
        
        this.secureClient = new OkHttpClient.Builder()
                .addInterceptor(new CookieInterceptor(cookieJar))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static HttpClientService getInstance(@NotNull Project project) {
        if (project == null) {
            throw new IllegalArgumentException("Project must not be null for HttpClientService");
        }
        HttpClientService service = project.getService(HttpClientService.class);
        if (service == null) {
            throw new IllegalStateException("HttpClientService is not registered for project: " + project.getName());
        }
        return service;
    }

    public Project getProject() {
        return project;
    }

    public InMemoryCookieJar getCookieJar() {
        return cookieJar;
    }

    private static X509TrustManager createUnsafeTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private static SSLSocketFactory createUnsafeSslSocketFactory(X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create unsafe SSL socket factory", e);
        }
    }
    
    public void clearCookies() {
        cookieJar.clearAll();
    }

    public void cancelAll() {
        for (Call call : activeCalls) {
            try {
                call.cancel();
            } catch (Exception ignored) {}
        }
        activeCalls.clear();
    }

    @Override
    public void dispose() {
        cancelAll();
        if (secureClient != null) {
            secureClient.dispatcher().cancelAll();
            secureClient.connectionPool().evictAll();
            secureClient.dispatcher().executorService().shutdown();
        }
        if (unsafeLocalClient != null) {
            unsafeLocalClient.dispatcher().cancelAll();
            unsafeLocalClient.connectionPool().evictAll();
            unsafeLocalClient.dispatcher().executorService().shutdown();
        }
        if (cookieJar != null) {
            cookieJar.clearAll();
        }
    }

    public CompletableFuture<HttpResponseModel> executeAsync(Request request) {
        return execute(request, (InsecureTlsConsent) null).future();
    }

    public CompletableFuture<HttpResponseModel> executeAsync(Request request, InsecureTlsConsent consent) {
        return execute(request, consent).future();
    }

    public RequestHandle execute(Request request, InsecureTlsConsent consent) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        OkHttpClient client = secureClient;
        if (consent != null) {
            String host = request.url().host();
            if (!isLocalDevelopmentHost(host)) {
                throw new IllegalArgumentException("Insecure TLS is only allowed for localhost or loopback addresses");
            }
            if (!consent.matchesHost(host)) {
                throw new SecurityException("Insecure TLS consent host '" + consent.getNormalizedHost() + "' does not match request host '" + host + "'");
            }
            client = getUnsafeLocalClient();
        }

        CompletableFuture<HttpResponseModel> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        Call call = client.newCall(request);
        activeCalls.add(call);
        RequestHandle handle = new RequestHandle(call, future);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                activeCalls.remove(call);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                activeCalls.remove(call);
                // Sử dụng try-with-resources để đảm bảo responseBody luôn được close(), tránh rò rỉ bộ nhớ
                try (ResponseBody responseBody = response.body()) {
                    long timeTaken = System.currentTimeMillis() - startTime;
                    String contentType = response.header("Content-Type");

                    ResponseReader.ReadResult readResult = ResponseReader.readBody(
                            responseBody, contentType, ResponseReader.DEFAULT_MAX_PREVIEW_BYTES);

                    HttpResponseModel model = new HttpResponseModel();
                    model.setStatusCode(response.code());
                    model.setStatusMessage(response.message());
                    model.setTruncated(readResult.isTruncated());
                    model.setBinary(readResult.isBinary());
                    model.setTotalBytes(readResult.getTotalBytes());
                    model.setRawBytes(readResult.getRawBytes());
                    model.setContentType(contentType);

                    String bodyText = readResult.getText();
                    if (!readResult.isBinary() && !readResult.isTruncated()) {
                        bodyText = formatJson(bodyText);
                    }
                    model.setBody(bodyText);
                    // toMultimap() trả về Map<String, List<String>> giữ nguyên các header trùng lặp
                    model.setHeaders(response.headers().toMultimap()); 
                    model.setTimeTakenMs(timeTaken);

                    future.complete(model);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });

        return handle;
    }

    private OkHttpClient getUnsafeLocalClient() {
        OkHttpClient local = unsafeLocalClient;
        if (local != null) return local;
        synchronized (this) {
            if (unsafeLocalClient == null) {
                X509TrustManager trustManager = createUnsafeTrustManager();
                unsafeLocalClient = secureClient.newBuilder()
                        .sslSocketFactory(createUnsafeSslSocketFactory(trustManager), trustManager)
                        .hostnameVerifier((hostname, session) -> isLocalDevelopmentHost(hostname))
                        .build();
            }
            return unsafeLocalClient;
        }
    }

    public static boolean isLocalDevelopmentHost(String host) {
        if (host == null || host.isBlank()) return false;
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return "localhost".equals(normalized)
                || isIpv4Loopback(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private static boolean isIpv4Loopback(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4 || !"127".equals(parts[0])) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    /**
     * Hỗ trợ format chuỗi JSON cho đẹp. Nếu không phải JSON thì trả về nguyên bản.
     */
    private String formatJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return rawJson;
        }
        if (rawJson.length() > ResponseReader.MAX_JSON_PRETTY_PRINT_CHARS) {
            return rawJson;
        }
        try {
            JsonElement el = JsonParser.parseString(rawJson);
            return gson.toJson(el);
        } catch (Exception e) {
            return rawJson;
        }
    }

    public static final class RequestHandle {
        private final Call call;
        private final CompletableFuture<HttpResponseModel> future;

        RequestHandle(Call call, CompletableFuture<HttpResponseModel> future) {
            this.call = call;
            this.future = future;
        }

        public CompletableFuture<HttpResponseModel> future() {
            return future;
        }

        public void cancel() {
            call.cancel();
            future.cancel(false);
        }

        public boolean isCanceled() {
            return call.isCanceled() || future.isCancelled();
        }
    }
}
