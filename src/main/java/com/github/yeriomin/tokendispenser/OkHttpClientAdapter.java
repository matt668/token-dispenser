package com.github.yeriomin.tokendispenser;

import com.github.yeriomin.playstoreapi.AuthException;
import com.github.yeriomin.playstoreapi.GooglePlayAPI;
import com.github.yeriomin.playstoreapi.GooglePlayException;
import com.github.yeriomin.playstoreapi.HttpClientAdapter;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

class OkHttpClientAdapter extends HttpClientAdapter {

    OkHttpClient client;

    public OkHttpClientAdapter() {
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .cipherSuites(
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256,)
            .build();
        
        setClient(new OkHttpClient.Builder()
            .connectionSpecs(Collections.singletonList(spec))
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .cookieJar(new CookieJar() {
                private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<HttpUrl, List<Cookie>>();

                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    cookieStore.put(url, cookies);
                }

                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url);
                    return cookies != null ? cookies : new ArrayList<Cookie>();
                }
            })
            .build()
        );
    }

    public void setClient(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public byte[] getEx(String url, Map<String, List<String>> params, Map<String, String> headers) throws IOException {
        return request(new Request.Builder().url(buildUrlEx(url, params)).get(), headers);
    }

    @Override
    public byte[] get(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return request(new Request.Builder().url(buildUrl(url, params)).get(), headers);
    }

    @Override
    public byte[] postWithoutBody(String url, Map<String, String> urlParams, Map<String, String> headers) throws IOException {
        return post(buildUrl(url, urlParams), new HashMap<String, String>(), headers);
    }

    @Override
    public byte[] post(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        FormBody.Builder bodyBuilder = new FormBody.Builder();
        if (null != params && !params.isEmpty()) {
            for (String name: params.keySet()) {
                bodyBuilder.add(name, params.get(name));
            }
        }

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(bodyBuilder.build());

        return post(url, requestBuilder, headers);
    }

    @Override
    public byte[] post(String url, byte[] body, Map<String, String> headers) throws IOException {
        if (!headers.containsKey("Content-Type")) {
            headers.put("Content-Type", "application/x-protobuf");
        }

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(RequestBody.create(MediaType.parse("application/x-protobuf"), body));

        return post(url, requestBuilder, headers);
    }

    byte[] post(String url, Request.Builder requestBuilder, Map<String, String> headers) throws IOException {
        requestBuilder.url(url);

        return request(requestBuilder, headers);
    }

    byte[] request(Request.Builder requestBuilder, Map<String, String> headers) throws IOException {
        Request request = requestBuilder
            .headers(Headers.of(headers))
            .build();
        Server.LOG.info("Requesting: " + request.url().toString());
        
        GooglePlayException ex = null;
        final int MAX_TRIES = 8;
        for (int i = 0; i < MAX_TRIES; i++) {
            Response response = client.newCall(request).execute();
    
            int code = response.code();
            byte[] content = response.body().bytes();
    
            if (code >= 400) {
                ex = new GooglePlayException("Malformed request", code);
                if (code == 401 || code == 403) {
                    ex = new AuthException("Auth error", code);
                    Map<String, String> authResponse = GooglePlayAPI.parseResponse(new String(content));
                    if (authResponse.containsKey("Error") && authResponse.get("Error").equals("NeedsBrowser")) {
                        ((AuthException) ex).setTwoFactorUrl(authResponse.get("Url"));
                    }
                } else if (code >= 500) {
                    ex = new GooglePlayException("Server error", code);
                }
                ex.setRawResponse(content);
                
                Server.LOG.info("Request failed, retrying (" + (i+1) + "/" + MAX_TRIES + ")");
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    throw new IOException("interrupted while sleeping", e);
                }
                continue;
            }
    
            return content;
        }
    
        Server.LOG.warn("Giving up on " + request.url().toString() + " after " + MAX_TRIES + " failed tries");
        throw ex;
    }

    public String buildUrl(String url, Map<String, String> params) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (null != params && !params.isEmpty()) {
            for (String name: params.keySet()) {
                urlBuilder.addQueryParameter(name, params.get(name));
            }
        }
        return urlBuilder.build().toString();
    }

    public String buildUrlEx(String url, Map<String, List<String>> params) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (null != params && !params.isEmpty()) {
            for (String name: params.keySet()) {
                for (String value: params.get(name)) {
                    urlBuilder.addQueryParameter(name, value);
                }
            }
        }
        return urlBuilder.build().toString();
    }
}
