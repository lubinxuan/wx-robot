package me.robin.wx.client.cookie;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xuanlubin on 2017/4/19.
 */
@Slf4j
public class CookieInterceptor implements Interceptor {

    private CookieHandler cookieHandler = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    public CookieInterceptor(CookieStore cookieStore) {
        this.cookieHandler = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        URI uri = request.url().uri();
        List<String> cookies = cookieHandler.get(uri, Collections.emptyMap()).get("Cookie");

        Response networkResponse;
        if (null != cookies && !cookies.isEmpty()) {
            Request.Builder requestBuilder = request.newBuilder();
            requestBuilder.header("Cookie", StringUtils.join(cookies, "; "));
            networkResponse = chain.proceed(requestBuilder.build());
            networkResponse = networkResponse.newBuilder().request(request).build();
        } else {
            networkResponse = chain.proceed(request);
        }
        Headers headers = networkResponse.headers();
        if (headers.size() > 0) {
            Map<String, List<String>> headersMap = new HashMap<>();
            for (String name : headers.names()) {
                if (!StringUtils.startsWith(StringUtils.lowerCase(name), "set-cookie")) {
                    continue;
                }
                List<String> values = headers.values(name);
                headersMap.put(name, values);
                log.info("update cookie:{} {}", name, StringUtils.join(values, ","));
            }
            cookieHandler.put(uri, headersMap);
        }
        return networkResponse;
    }
}
