package me.robin.wx.robot.frame;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.alibaba.fastjson.util.TypeUtils;
import me.robin.wx.robot.frame.cookie.CookieInterceptor;
import me.robin.wx.robot.frame.cookie.MemoryCookieStore;
import me.robin.wx.robot.frame.listener.ServerStatusListener;
import me.robin.wx.robot.frame.model.LoginUser;
import me.robin.wx.robot.frame.service.ContactService;
import me.robin.wx.robot.frame.util.ResponseReadUtils;
import me.robin.wx.robot.frame.util.WxUtil;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.CookieStore;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public abstract class BaseServer implements Runnable, WxApi {

    private static final Logger logger = LoggerFactory.getLogger(BaseServer.class);

    private static final Timer delayTaskScheduler = new Timer("DelayTaskScheduler");

    private String appId;

    final LoginUser user = new LoginUser();

    OkHttpClient client;

    ServerStatusListener statusListener;

    ContactService contactService;

    private CookieStore cookieStore;

    private volatile boolean login = false;

    public BaseServer(String appId, ContactService contactService) {
        this.appId = appId;
        this.cookieStore = new MemoryCookieStore();
        this.contactService = contactService;
        this.client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(new CookieInterceptor(this.cookieStore))
                .build();
    }

    @Override
    public void run() {
        this.queryNewUUID();
    }

    boolean checkLogin() {
        return login;
    }

    public void waitLoginDone() {
        if (checkLogin()) {
            return;
        }
        synchronized (user) {
            try {
                user.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public LoginUser loginUser() {
        return login ? user : null;
    }

    /**
     * 初始化
     */
    private void init() {
        Request.Builder builder = initRequestBuilder(WxConst.INIT_URL, "r", WxUtil.random(10), "lang", "zh_CN", "pass_ticket", user.getPassTicket());
        WxUtil.jsonRequest(baseRequest(), builder::post);
        client.newCall(builder.build()).enqueue(new BaseJsonCallback() {
            @Override
            public void process(Call call, Response response, JSONObject responseJson) {
                Integer ret = TypeUtils.castToInt(JSONPath.eval(responseJson, "BaseResponse.Ret"));
                if (null != ret && 0 == ret) {
                    JSONObject user = responseJson.getJSONObject("User");
                    BaseServer.this.user.setUserName(user.getString("UserName"));
                    BaseServer.this.user.setNickName(user.getString("NickName"));
                    BaseServer.this.user.setSyncKey(responseJson.getJSONObject("SyncKey"));
                    statusNotify();
                    contactService.updateContact(responseJson.getJSONArray("ContactList"));
                    getContact();
                } else {
                    String message = TypeUtils.castToString(JSONPath.eval(responseJson, "BaseResponse.ErrMsg"));
                    logger.warn("web微信初始化失败 ret:{} errMsg:{}", ret, message);
                }
            }
        });
    }


    /**
     * 获取通讯录
     */
    private void getContact() {
        Request request = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxgetcontact", "lang", "zh_CN", "r", System.currentTimeMillis(), "seq", "0", "skey", user.getSkey()).build();
        client.newCall(request).enqueue(new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject content) {
                logger.info("获取到联系人列表");
                syncCheck();
                batchGetContact();
                contactService.updateContact(content.getJSONArray("MemberList"));
                login = true;
                synchronized (BaseServer.this.user) {
                    BaseServer.this.user.notifyAll();
                }
            }
        });
    }

    /**
     * 获取通讯录
     */
    private void batchGetContact() {

    }


    /**
     * long poll sync
     */
    private void syncCheck() {
        //https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck?r=1492576604964&skey=%40crypt_cfbf95a5_ddaa708f9a9ac2e2d7a95a0a433b3c67&sid=GQ7GDgvoL6Y8FrQY&uin=1376796829&deviceid=e644133084693151&synckey=1_657703788%7C2_657703818%7C3_657703594%7C1000_1492563241&_=1492576604148
        String url = "https://webpush.{host}/cgi-bin/mmwebwx-bin/synccheck";
        Request request = initRequestBuilder(url, "r", System.currentTimeMillis(), "skey", user.getSkey(), "sid", user.getSid(), "uin", user.getUin(), "deviceid", WxUtil.randomDeviceId(), "synckey", syncKey(), "_", System.currentTimeMillis()).build();
        client.newCall(request).enqueue(new BaseCallback() {
            @Override
            void process(Call call, Response response, String content) {
                String rsp = StringUtils.substringAfter(content, "window.synccheck=");
                JSONObject syncStatus = JSON.parseObject(rsp);
                int selector = syncStatus.getIntValue("selector");
                int retcode = syncStatus.getIntValue("retcode");
                switch (retcode) {
                    case 0:
                        switch (selector) {
                            case 0:
                                logger.info("没有信息需要同步");
                                break;
                            default:
                                logger.info("有信息需要同步 selector:{}", selector);
                                sync();
                                return;
                        }
                        break;
                    case 1101:
                        login = false;
                        queryNewUUID();
                        logger.info("客户端退出了");
                        return;
                    default:
                        logger.warn("没有正常获取到同步信息 : {}", content);
                }
                syncCheck();
            }
        });
    }

    /**
     * sync messages
     */
    private void sync() {
        //https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsync?sid=GQ7GDgvoL6Y8FrQY&skey=@crypt_cfbf95a5_ddaa708f9a9ac2e2d7a95a0a433b3c67
        Request.Builder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxsync", "sid", user.getSid(), "skey", user.getSkey());
        Map<String, Object> requestBody = baseRequest();
        requestBody.put("SyncKey", user.getSyncKey());
        requestBody.put("rr", WxUtil.random(10));
        WxUtil.jsonRequest(requestBody, builder::post);
        client.newCall(builder.build()).enqueue(new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject syncRsp) {
                Integer ret = TypeUtils.castToInt(JSONPath.eval(syncRsp, "BaseResponse.Ret"));
                boolean syncNow = true;
                if (null != ret && 0 == ret) {
                    logger.debug("同步成功");
                    JSONObject syncKey = syncRsp.getJSONObject("SyncCheckKey");
                    if (StringUtils.equals(syncKey.toJSONString(), user.getSyncKey().toJSONString())) {
                        logger.warn("同步key没有更新");
                        syncNow = false;
                    } else {
                        user.setSyncKey(syncKey);
                    }
                    String skey = syncRsp.getString("SKey");
                    if (StringUtils.isNotBlank(skey)) {
                        logger.info("更新用户SKey");
                        user.setSkey(skey);
                    }
                    if (null != statusListener) {
                        statusListener.onAddMsgList(syncRsp.getJSONArray("AddMsgList"), (Server) BaseServer.this);
                        statusListener.onDelContactList(syncRsp.getJSONArray("ModContactList"), (Server) BaseServer.this);
                        statusListener.onModChatRoomMemberList(syncRsp.getJSONArray("DelContactList"), (Server) BaseServer.this);
                        statusListener.onModContactList(syncRsp.getJSONArray("ModChatRoomMemberList"), (Server) BaseServer.this);
                    }
                } else {
                    logger.warn("同步异常:{}", syncRsp.toJSONString());
                }

                if (syncNow) {
                    syncCheck();
                } else {
                    delayTask(BaseServer.this::syncCheck, 3);
                }
            }
        });
    }

    /**
     * 登录web微信
     */
    private void login(String loginPageUrl) {
        if (!loginPageUrl.contains("&fun=new&version=v2")) {
            loginPageUrl = loginPageUrl + "&fun=new&version=v2";
        }
        Request request = initRequestBuilder(loginPageUrl).build();
        client.newCall(request).enqueue(new BaseCallback() {
            @Override
            public void process(Call call, Response response, String content) {
                String ret = WxUtil.getValueFromXml(content, "ret");
                if ("0".equals(ret)) {
                    user.setSkey(WxUtil.getValueFromXml(content, "skey"));
                    user.setPassTicket(WxUtil.getValueFromXml(content, "pass_ticket"));
                    user.setUin(WxUtil.getValueFromXml(content, "wxuin"));
                    List<Cookie> cookies = Cookie.parseAll(call.request().url(), response.headers());
                    Optional<Cookie> wxSidCookie = cookies.stream().filter(cookie -> "wxsid".equals(cookie.name())).findFirst();
                    if (wxSidCookie.isPresent()) {
                        user.setSid(wxSidCookie.get().value());
                        logger.info("登录成功");
                        BaseServer.this.init();
                    } else {
                        logger.warn("微信登录异常,没有读取到wxsid");
                    }
                } else {
                    String message = WxUtil.getValueFromXml(content, "message");
                    logger.error("WEB微信登录异常 ret:{} message:{}", ret, message);
                }
            }
        });
    }

    /**
     * send status notify
     */
    private void statusNotify() {
        Request.Builder builder = initRequestBuilder(WxConst.STATUS_NOTIFY, "lang", "zh_CN", "pass_ticket", user.getPassTicket());
        Map<String, Object> requestBody = baseRequest();
        requestBody.put("Code", 3);
        requestBody.put("FromUserName", user.getUserName());
        requestBody.put("ToUserName", user.getUserName());
        requestBody.put("ClientMsgId", System.currentTimeMillis());
        WxUtil.jsonRequest(requestBody, builder::post);
        client.newCall(builder.build()).enqueue(new BaseCallback() {
            @Override
            public void process(Call call, Response response, String content) {
                logger.info("WX登录StatusNotify send success");
            }
        });
    }

    /**
     * 获取二维码 以及 UUID
     */
    private void queryNewUUID() {
        Request request = initRequestBuilder(WxConst.QR_CODE_API, "appid", appId).build();
        client.newCall(request).enqueue(new BaseCallback() {
            @Override
            public void process(Call call, Response response, String content) {
                int idx = content.indexOf("window.QRLogin.uuid");
                if (idx > -1) {
                    idx = content.indexOf("\"", idx);
                    int e_idx = content.indexOf("\"", idx + 1);
                    BaseServer.this.user.setUuid(content.substring(idx + 1, e_idx));
                    statusListener.onUUIDSuccess("https://login.weixin.qq.com/qrcode/" + BaseServer.this.user.getUuid());
                    BaseServer.this.waitForLogin();
                } else {
                    logger.warn("没有正常获取到UUID");
                    delayTask(() -> reCall(call, this), 2);
                }
            }
        });
    }

    /**
     * 等待用户客户端点击登录
     */
    private void waitForLogin() {
        Request request = initRequestBuilder(WxConst.LOGIN_CHECK_API, "loginicon", "true", "uuid", this.user.getUuid(), "tip", "1", "r", WxUtil.random(10), "_", System.currentTimeMillis()).build();
        client.newCall(request).enqueue(new BaseCallback() {
            @Override
            void process(Call call, Response response, String content) {
                String status = StringUtils.substringBetween(content, "window.code=", ";");
                switch (status) {
                    case "200":
                        String url = StringUtils.substringBetween(content, "window.redirect_uri=\"", "\"");
                        HttpUrl httpUrl = HttpUrl.parse(url);
                        user.setLoginHost(httpUrl.host());
                        BaseServer.this.login(url);
                        break;
                    case "201":
                        logger.info("请点击手机客户端确认登录");
                        BaseServer.this.user.setUserAvatar(StringUtils.substringBetween(content, "window.userAvatar = '", "';"));
                        delayTask(BaseServer.this::waitForLogin, 2);
                        break;
                    case "408":
                        logger.info("请用手机客户端扫码登录web微信");
                        waitForLogin();
                        break;
                    case "400":
                        logger.info("二维码失效");
                        BaseServer.this.queryNewUUID();
                        break;
                    default:
                        logger.info("扫码登录发生未知异常 服务器响应:{}", content);
                }
            }
        });
    }

    /**
     * 重新执行请求
     *
     * @param call
     * @param callback
     */
    void reCall(Call call, Callback callback) {
        Request request = call.request().newBuilder().build();
        client.newCall(request).enqueue(callback);
    }


    /**
     * 初始化请求头
     *
     * @param path
     * @return
     */
    Request.Builder initRequestBuilder(String path, Object... params) {
        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        if (path.startsWith("https://")) {
            urlBuilder = HttpUrl.parse(path.replace("{host}", user.getLoginHost())).newBuilder();
        } else {
            urlBuilder.scheme("https");
            urlBuilder.host(user.getLoginHost());
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
        builder.header("Referer", "https://" + user.getLoginHost() + "/");
        builder.header("Accept-Encoding", "gzip, deflate, br");
        builder.header("Connection", "keep-alive");
        builder.header("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6,en-US;q=0.4,zh-TW;q=0.2,ja;q=0.2");
        return builder;
    }

    /**
     * 请求基本信息
     *
     * @return
     */
    Map<String, Object> baseRequest() {
        Map<String, String> baseRequest = new HashMap<>();
        baseRequest.put("Uin", user.getUin());
        baseRequest.put("Sid", user.getSid());
        baseRequest.put("Skey", user.getSkey());
        baseRequest.put("DeviceID", WxUtil.randomDeviceId());
        Map<String, Object> wrap = new HashMap<>();
        wrap.put("BaseRequest", baseRequest);
        return wrap;
    }

    /**
     * 组装SyncKey参数
     *
     * @return
     */
    private String syncKey() {
        JSONArray array = user.getSyncKey().getJSONArray("List");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            JSONObject key = array.getJSONObject(i);
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(key.getString("Key")).append("_").append(key.getString("Val"));
        }
        return sb.toString();
    }

    /**
     * 延时任务
     *
     * @param delayTask   任务
     * @param delaySecond 延时时间
     */
    void delayTask(DelayTask delayTask, float delaySecond) {
        delayTaskScheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                delayTask.execute();
            }
        }, (long) (delaySecond * 1000));
    }

    public void setStatusListener(ServerStatusListener statusListener) {
        this.statusListener = statusListener;
    }

    public abstract class BaseCallback implements Callback {

        @Override
        public void onFailure(Call call, IOException e) {
            logger.error("{}", call.request().url().toString(), e);
            reCall(call, this);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                String content = ResponseReadUtils.read(response);
                this.process(call, response, content);
            } finally {
                IOUtils.closeQuietly(response);
            }
        }

        abstract void process(Call call, Response response, String content);
    }

    public abstract class BaseJsonCallback implements Callback {

        @Override
        public void onFailure(Call call, IOException e) {
            logger.error("{}", call.request().url().toString(), e);
            reCall(call, this);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                String content = ResponseReadUtils.read(response);
                this.process(call, response, JSON.parseObject(content));
            } finally {
                IOUtils.closeQuietly(response);
            }
        }

        abstract void process(Call call, Response response, JSONObject content);
    }
}
