package vn.io.codelearning.springapitester.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Động cơ thực thi HTTP Client (Singleton).
 */
public class HttpClientService {

    private static HttpClientService instance;

    private final OkHttpClient client;
    private final Gson gson;

    private final InMemoryCookieJar cookieJar;

    private HttpClientService() {
        this.cookieJar = new InMemoryCookieJar();
        
        X509TrustManager trustManager = createUnsafeTrustManager();
        SSLSocketFactory sslSocketFactory = createUnsafeSslSocketFactory(trustManager);

        this.client = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManager)
                .hostnameVerifier((hostname, session) -> true)
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
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

    public static synchronized HttpClientService getInstance() {
        if (instance == null) {
            instance = new HttpClientService();
        }
        return instance;
    }
    
    public void clearCookies() {
        cookieJar.clearAll();
    }

    /**
     * Thực thi request bất đồng bộ, trả về CompletableFuture không làm đơ giao diện.
     */
    public CompletableFuture<HttpResponseModel> executeAsync(Request request) {
        CompletableFuture<HttpResponseModel> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                // Sử dụng try-with-resources để đảm bảo responseBody luôn được close(), tránh rò rỉ bộ nhớ
                try (ResponseBody responseBody = response.body()) {
                    long timeTaken = System.currentTimeMillis() - startTime;

                    String bodyStr = (responseBody != null) ? responseBody.string() : "";

                    HttpResponseModel model = new HttpResponseModel();
                    model.setStatusCode(response.code());
                    model.setStatusMessage(response.message());
                    model.setBody(formatJson(bodyStr)); // Làm đẹp JSON
                    // toMultimap() trả về Map<String, List<String>> giữ nguyên các header trùng lặp
                    model.setHeaders(response.headers().toMultimap()); 
                    model.setTimeTakenMs(timeTaken);

                    future.complete(model);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    /**
     * Hỗ trợ format chuỗi JSON cho đẹp. Nếu không phải JSON thì trả về nguyên bản.
     */
    private String formatJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return rawJson;
        }
        try {
            JsonElement el = JsonParser.parseString(rawJson);
            return gson.toJson(el);
        } catch (Exception e) {
            return rawJson;
        }
    }
}
