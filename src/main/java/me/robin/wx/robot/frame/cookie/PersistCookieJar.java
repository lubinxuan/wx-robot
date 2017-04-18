package me.robin.wx.robot.frame.cookie;

import com.sun.jndi.toolkit.url.Uri;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class PersistCookieJar implements CookieJar {

    private final CookieStore cookieStore = new CookieManager().getCookieStore();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (null == cookies || cookies.isEmpty()) {
            return;
        }
        URI uri = url.uri();
        for (Cookie cookie : cookies) {
            HttpCookie httpCookie = new HttpCookie(cookie.name(), cookie.value());
            httpCookie.setMaxAge(cookie.expiresAt());
            httpCookie.setDomain(cookie.domain());
            httpCookie.setPath(cookie.path());
            httpCookie.setSecure(cookie.secure());
            httpCookie.setHttpOnly(cookie.httpOnly());
            cookieStore.add(uri, httpCookie);
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<HttpCookie> cookieList = cookieStore.get(url.uri());
        if (null == cookieList || cookieList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Cookie> _cookieList = new ArrayList<>(cookieList.size());
        for (HttpCookie httpCookie : cookieList) {
            Cookie.Builder builder = new Cookie.Builder();
            builder.name(httpCookie.getName());
            builder.value(httpCookie.getValue());
            builder.expiresAt(httpCookie.getMaxAge());
            builder.domain(httpCookie.getDomain());
            builder.path(httpCookie.getPath());
            _cookieList.add(builder.build());
        }
        return _cookieList;
    }
}
