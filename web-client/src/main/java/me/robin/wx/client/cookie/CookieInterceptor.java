package me.robin.wx.client.cookie;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xuanlubin on 2017/4/19.
 */
@Slf4j
public class CookieInterceptor implements Interceptor {

    private final CookieStore cookieStore;

    public CookieInterceptor(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        URI uri = request.url().uri();
        List<HttpCookie> cookieList = this.cookieStore.get(uri);
        Response networkResponse;
        if (null != cookieList && !cookieList.isEmpty()) {
            Request.Builder requestBuilder = request.newBuilder();
            StringBuilder sb = new StringBuilder();
            for (HttpCookie httpCookie : cookieList) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(httpCookie.getName()).append("=").append(httpCookie.getValue());
            }
            requestBuilder.header("Cookie", sb.toString());
            networkResponse = chain.proceed(requestBuilder.build());
            networkResponse = networkResponse.newBuilder().request(request).build();
        } else {
            networkResponse = chain.proceed(request);
        }
        Headers headers = networkResponse.headers();
        log.debug("url:{} status:{}", uri.toString(), networkResponse.code());
        List<String> rspCookieList = new ArrayList<>();
        rspCookieList.addAll(headers.values("set-cookie"));
        rspCookieList.addAll(headers.values("set-cookie2"));
        if (rspCookieList.size() > 0) {
            log.info("update cookie: {}", StringUtils.join(rspCookieList, ","));
            for (String cookie : rspCookieList) {
                List<HttpCookie> httpCookieList = HttpCookie.parse(cookie);
                httpCookieList.forEach(ck -> this.cookieStore.add(uri, ck));
            }
        }
        return networkResponse;
    }
}
