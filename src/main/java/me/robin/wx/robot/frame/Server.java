package me.robin.wx.robot.frame;

import me.robin.wx.robot.frame.cookie.PersistCookieJar;
import me.robin.wx.robot.frame.util.ResposeReadUtils;
import me.robin.wx.robot.frame.util.WxUtil;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class Server implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private String appId;

    private String uuid;

    private String host = "wx.qq.com";

    private OkHttpClient client;

    public Server(String appId) {
        this.appId = appId;
        this.client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .cookieJar(new PersistCookieJar())
                .build();
    }

    @Override
    public void run() {
        this.queryNewUUID();
    }

    /**
     * 初始化
     */
    private void init() {

    }

    /**
     * 登录web微信
     */
    private void login(String loginPageUrl) {
        if (!loginPageUrl.contains("&fun=new&version=v2")) {
            loginPageUrl = loginPageUrl + "&fun=new&version=v2";
        }
        Request request = initRequest(loginPageUrl);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("{}", call.request().url().toString(), e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String content = ResposeReadUtils.read(response);
                String skey = StringUtils.substringBetween(content, "<skey>", "</skey>");
                String passTicket = StringUtils.substringBetween(content, "<pass_ticket>", "</pass_ticket>");
                logger.info("登录成功:  skey:{} passTicket:{}", skey, passTicket);
                Server.this.init();
            }
        });
    }

    /**
     * send status notify
     */
    private void statusNotify() {

    }

    /**
     * 获取二维码 以及 UUID
     */
    private void queryNewUUID() {
        Request request = initRequest(WxConst.QR_CODE_API, "appid", appId);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("{}", call.request().url().toString(), e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String content = ResposeReadUtils.read(response);
                int idx = content.indexOf("window.QRLogin.uuid");
                if (idx > -1) {
                    idx = content.indexOf("\"", idx);
                    int e_idx = content.indexOf("\"", idx + 1);
                    Server.this.uuid = content.substring(idx + 1, e_idx);
                    logger.warn("UUID获取成功 https://login.weixin.qq.com/qrcode/{}", Server.this.uuid);
                    Server.this.waitForLogin();
                } else {
                    logger.warn("没有正常获取到UUID");
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (Exception ignore) {
                    }
                    reCall(call, this);
                }
            }
        });
    }

    /**
     * 等待用户客户端点击登录
     */
    private void waitForLogin() {
        Request request = initRequest(WxConst.LOGIN_CHECK_API, "loginicon", "true", "uuid", this.uuid, "tip", "1", "r", WxUtil.random(10), "_", System.currentTimeMillis());
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.error("{}", call.request().url().toString(), e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String content = ResposeReadUtils.read(response);
                String status = StringUtils.substringBetween(content, "window.code=", ";");
                String url = StringUtils.substringBetween(content, "window.redirect_uri=\"", "\"");
                if ("200".equals(status)) {
                    HttpUrl httpUrl = HttpUrl.parse(url);
                    Server.this.host = httpUrl.host();
                    Server.this.login(url);
                } else if ("201".equals(status)) {
                    logger.info("请点击手机客户端确认登录");
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    reCall(call, this);
                } else {
                    logger.info("请用手机客户端扫码登录web微信");
                    reCall(call, this);
                }
            }
        });
    }


    private void reCall(Call call, Callback callback) {
        Request request = call.request().newBuilder().build();
        client.newCall(request).enqueue(callback);
    }


    /**
     * 初始化请求头
     *
     * @param path
     * @return
     */
    private Request initRequest(String path, Object... params) {
        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        if (path.startsWith("https://")) {
            urlBuilder = HttpUrl.parse(path).newBuilder();
        } else {
            urlBuilder.scheme("https");
            urlBuilder.host(this.host);
            urlBuilder.encodedPath(path);
        }
        if (null != params) {
            for (int i = 0; i < params.length; i += 2) {
                if (i + 1 < params.length) {
                    urlBuilder.addQueryParameter(String.valueOf(params[i]), String.valueOf(params[i + 1]));
                } else {
                    urlBuilder.addQueryParameter(String.valueOf(params[i]), "");
                }
            }
        }

        Request.Builder builder = new Request.Builder().url(urlBuilder.build());
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.98 Safari/537.36");
        builder.header("Referer", "https://" + this.host + "/");
        builder.header("Accept-Encoding", "gzip, deflate, br");
        builder.header("Connection", "keep-alive");
        builder.header("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6,en-US;q=0.4,zh-TW;q=0.2,ja;q=0.2");

        return builder.build();
    }
}
