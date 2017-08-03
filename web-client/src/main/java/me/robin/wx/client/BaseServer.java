package me.robin.wx.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.alibaba.fastjson.util.TypeUtils;
import me.robin.wx.client.cookie.CookieInterceptor;
import me.robin.wx.client.cookie.MemoryCookieStore;
import me.robin.wx.client.listener.EmptyServerStatusListener;
import me.robin.wx.client.listener.ServerStatusListener;
import me.robin.wx.client.model.LoginUser;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxUser;
import me.robin.wx.client.service.ContactService;
import me.robin.wx.client.service.DefaultContactService;
import me.robin.wx.client.util.RequestBuilder;
import me.robin.wx.client.util.ResponseReadUtils;
import me.robin.wx.client.util.WxUtil;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.CookieStore;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by xuanlubin on 2017/4/18.
 * 微信WEB客户端
 */
public abstract class BaseServer implements Runnable, WxApi {

    private static final Logger logger = LoggerFactory.getLogger(BaseServer.class);

    private static final Timer delayTaskScheduler = new Timer("DelayTaskScheduler");

    private static final ExpireListener DEFAULT_EXPIRE_LISTENER = new ExpireListener() {
        @Override
        public boolean expire(BaseServer server) {
            return false;
        }
    };

    private static final ServerStatusListener DEFAULT_SERVER_STATUS_LISTENER = new EmptyServerStatusListener();

    private Timer timer;

    private String qrBaseUrl = "https://login.weixin.qq.com/qrcode/";

    private String appId;

    private final String instanceId;

    final LoginUser user = new LoginUser();

    OkHttpClient client;

    protected ServerStatusListener statusListener = DEFAULT_SERVER_STATUS_LISTENER;

    private ExpireListener expireListener = DEFAULT_EXPIRE_LISTENER;

    protected ContactService contactService = new DefaultContactService();

    protected CookieStore cookieStore;

    private volatile boolean login = false;

    protected LinkedBlockingQueue<String[]> lazyUpdateGroupQueue = new LinkedBlockingQueue<>();
    protected LinkedBlockingQueue<String[]> lazyUpdateGroupMemberQueue = new LinkedBlockingQueue<>();

    private boolean groupMemberDetail = true;

    private final CountDownLatch NO_WAIT = new CountDownLatch(0);

    private static final BiConsumer<Boolean, String> DEFAULT = (aBoolean, s) -> {

    };

    public BaseServer(String instanceId, String appId, ContactService contactService) {
        this.appId = appId;
        this.instanceId = instanceId;
        this.cookieStore = new MemoryCookieStore(user);
        this.contactService = contactService;
        this.client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(new CookieInterceptor(this.cookieStore))
                .build();
        this.timer = new Timer("GroupContactSyncThread-" + instanceId);
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
        RequestBuilder builder = initRequestBuilder(WxConst.INIT_URL);
        builder.query("r", WxUtil.random(10));
        builder.query("lang", "zh_CN");
        builder.query("pass_ticket", user.getPassTicket());
        baseRequest(builder);
        Callback callback = new BaseJsonCallback() {
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
                    BaseServer.this.timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                batchGetContact();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }, 2000, 2000);
                } else {
                    String message = TypeUtils.castToString(JSONPath.eval(responseJson, "BaseResponse.ErrMsg"));
                    logger.warn("[{}]web微信初始化失败 ret:{} errMsg:{}", instanceId, ret, message);
                }
            }
        };
        builder.execute(client, callback);
    }


    /**
     * 获取通讯录
     */
    private void getContact() {
        RequestBuilder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxgetcontact");
        builder.query("lang", "zh_CN");
        builder.query("r", System.currentTimeMillis());
        builder.query("seq", "0");
        builder.query("skey", user.getSkey());
        Callback callback = new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject content) {
                logger.info("[{}]获取到联系人列表", instanceId);
                syncCheck();
                contactService.updateContact(content.getJSONArray("MemberList"));
                login = true;
                synchronized (BaseServer.this.user) {
                    BaseServer.this.user.notifyAll();
                }
                List<WxGroup> wxGroupList = contactService.listAllGroup();
                wxGroupList.forEach(g -> lazyUpdateGroupQueue.offer(new String[]{g.getUserName()}));
            }
        };
        builder.execute(client, callback);
    }

    /**
     * 获取通讯录
     */
    private void batchGetContact() throws InterruptedException {
        List<Map<String, String>> updateContactList = new ArrayList<>();
        while (true) {
            List<String[]> groupNameList = new ArrayList<>();
            int rows = lazyUpdateGroupQueue.drainTo(groupNameList, 10);
            if (rows < 1) {
                rows = lazyUpdateGroupMemberQueue.drainTo(groupNameList, 20);
            }
            if (rows < 1) {
                return;
            }
            for (String[] contactInfo : groupNameList) {
                Map<String, String> param = new HashMap<>();
                param.put("EncryChatRoomId", contactInfo.length > 1 ? contactInfo[1] : "");
                param.put("UserName", contactInfo[0]);
                updateContactList.add(param);
            }
            CountDownLatch latch = batchGetContactTask(updateContactList);
            latch.await();
            updateContactList.clear();
        }
    }

    private CountDownLatch batchGetContactTask(List<Map<String, String>> updateContactList) {
        if (updateContactList.isEmpty()) {
            return NO_WAIT;
        }
        CountDownLatch latch = new CountDownLatch(1);
        logger.info("开始同步通讯录详情:{}", JSON.toJSONString(updateContactList));
        RequestBuilder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxbatchgetcontact");
        builder.query("type", "ex");
        builder.query("r", System.currentTimeMillis());
        builder.query("lang", "zh_CN");
        builder.json("Count", updateContactList.size());
        builder.json("List", updateContactList);
        baseRequest(builder);
        Callback callback = new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject content) {
                latch.countDown();
                Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
                if (null != ret && ret == 0) {
                    logger.info("[{}]同步到群组信息", instanceId);
                    JSONArray contactList = content.getJSONArray("ContactList");
                    for (int i = 0; i < contactList.size(); i++) {
                        WxUser wxUser = WxUtil.parse(contactList.getJSONObject(i));
                        if (wxUser instanceof WxGroup) {
                            WxGroup group = (WxGroup) wxUser;
                            WxGroup wxGroup = (WxGroup) contactService.queryUserByUserName(group.getUserName());
                            if (wxGroup == null) {
                                contactService.addWxUser(group);
                                wxGroup = group;
                            } else {
                                wxGroup.setMemberCount(group.getMemberCount());
                                wxGroup.setMemberList(group.getMemberList());
                                wxGroup.setEncryChatRoomId(group.getEncryChatRoomId());
                            }
                            if (groupMemberDetail) {
                                //群组成员信息加入等待更新列表
                                for (WxUser user : wxGroup.getMemberList()) {
                                    lazyUpdateGroupMemberQueue.offer(new String[]{user.getUserName(), group.getEncryChatRoomId()});
                                }
                            }
                        } else {
                            if (StringUtils.isNotBlank(wxUser.getEncryChatRoomId())) {
                                contactService.updateGroupUserInfo(wxUser);
                            }
                        }
                    }
                } else {
                    logger.warn("[{}]群组信息获取异常:{}", instanceId, JSON.toJSONString(content));
                }
            }
        };
        builder.execute(client, callback);
        return latch;
    }


    /**
     * long poll sync
     */
    private void syncCheck() {
        //https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck?r=1492576604964&skey=%40crypt_cfbf95a5_ddaa708f9a9ac2e2d7a95a0a433b3c67&sid=GQ7GDgvoL6Y8FrQY&uin=1376796829&deviceid=e644133084693151&synckey=1_657703788%7C2_657703818%7C3_657703594%7C1000_1492563241&_=1492576604148
        String url = "https://webpush.{host}/cgi-bin/mmwebwx-bin/synccheck";
        RequestBuilder builder = initRequestBuilder(url);
        builder.query("r", System.currentTimeMillis());
        builder.query("skey", user.getSkey());
        builder.query("uin", user.getUin());
        builder.query("sid", user.getSid());
        builder.query("deviceid", WxUtil.randomDeviceId());
        builder.query("synckey", syncKey());
        builder.query("_", System.currentTimeMillis());
        Callback callback = new BaseCallback() {
            @Override
            void process(Call call, Response response, String content) {
                String rsp = StringUtils.substringAfter(content, "window.synccheck=");
                JSONObject syncStatus = JSON.parseObject(rsp);
                int selector = syncStatus.getIntValue("selector");
                int retcode = syncStatus.getIntValue("retcode");
                switch (retcode) {
                    case 0:
                        SyncMonitor.updateSyncTime(BaseServer.this);
                        switch (selector) {
                            case 0:
                                break;
                            default:
                                logger.info("[{}]有信息需要同步 selector:{}", instanceId, selector);
                                sync();
                                return;
                        }
                        break;
                    case 1101:
                        login = false;
                        logger.info("[{}]客户端退出了", instanceId);
                        WxUtil.deleteImgTmpDir(BaseServer.this.user.getUin());
                        BaseServer.this.contactService.clearContact();
                        if (!expireListener.expire(BaseServer.this)) {
                            queryNewUUID();
                        } else {
                            SyncMonitor.evict(BaseServer.this);
                        }
                        return;
                    default:
                        logger.warn("[{}]没有正常获取到同步信息 : {}", instanceId, content);
                }
                syncCheck();
            }
        };
        builder.execute(client, callback);
    }

    /**
     * sync messages
     */
    private void sync() {
        //https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsync?sid=GQ7GDgvoL6Y8FrQY&skey=@crypt_cfbf95a5_ddaa708f9a9ac2e2d7a95a0a433b3c67
        RequestBuilder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxsync");
        builder.query("sid", user.getSid());
        builder.query("skey", user.getSkey());
        baseRequest(builder);
        builder.json("SyncKey", user.getSyncKey());
        builder.json("rr", WxUtil.random(10));
        Callback callback = new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject syncRsp) {
                Integer ret = TypeUtils.castToInt(JSONPath.eval(syncRsp, "BaseResponse.Ret"));
                boolean syncNow = true;
                if (null != ret && 0 == ret) {
                    logger.debug("[{}]同步成功", instanceId);
                    JSONObject syncKey = syncRsp.getJSONObject("SyncCheckKey");
                    if (StringUtils.equals(syncKey.toJSONString(), user.getSyncKey().toJSONString())) {
                        logger.warn("[{}]同步key没有更新", instanceId);
                        syncNow = false;
                    } else {
                        user.setSyncKey(syncKey);
                    }
                    String skey = syncRsp.getString("SKey");
                    if (StringUtils.isNotBlank(skey)) {
                        logger.info("[{}]更新用户SKey", instanceId);
                        user.setSkey(skey);
                    }
                    if (null != statusListener) {
                        JSONArray addMsgList = syncRsp.getJSONArray("AddMsgList");
                        checkGroupNotInContactList(addMsgList);
                        statusListener.onAddMsgList(addMsgList, BaseServer.this);
                        statusListener.onDelContactList(syncRsp.getJSONArray("ModContactList"), BaseServer.this);
                        statusListener.onModChatRoomMemberList(syncRsp.getJSONArray("DelContactList"), BaseServer.this);
                        statusListener.onModContactList(syncRsp.getJSONArray("ModChatRoomMemberList"), BaseServer.this);
                    }
                } else {
                    logger.warn("[{}]同步异常:{}", instanceId, syncRsp.toJSONString());
                }

                if (syncNow) {
                    syncCheck();
                } else {
                    delayTask(BaseServer.this::syncCheck, 3);
                }
            }
        };
        builder.execute(client, callback);
    }

    private void checkGroupNotInContactList(JSONArray msgList) {
        for (int i = 0; i < msgList.size(); i++) {
            JSONObject message = msgList.getJSONObject(i);
            String statusNotifyUserName = message.getString("StatusNotifyUserName");
            String[] userNames = StringUtils.split(statusNotifyUserName, ",");
            for (String userName : userNames) {
                if (StringUtils.startsWith(statusNotifyUserName, "@@") && !contactService.groupInitialized(statusNotifyUserName)) {
                    lazyUpdateGroupQueue.offer(new String[]{userName});
                }
            }
        }
    }


    /**
     * 登录web微信
     */
    private void login(String loginPageUrl) {
        if (!loginPageUrl.contains("&fun=new&version=v2")) {
            loginPageUrl = loginPageUrl + "&fun=new&version=v2";
        }
        RequestBuilder builder = initRequestBuilder(loginPageUrl);
        Callback callback = new BaseCallback() {
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
                        logger.info("[{}]登录成功", instanceId);
                        BaseServer.this.init();
                        return;
                    } else {
                        logger.warn("[{}]微信登录异常,没有读取到wxsid", instanceId);
                    }
                } else {
                    String message = WxUtil.getValueFromXml(content, "handler");
                    logger.error("[{}]WEB微信登录异常 ret:{} handler:{}", instanceId, ret, message);
                }
                reCall(call, this);
            }
        };
        builder.execute(client, callback);
    }

    /**
     * send status notify
     */
    private void statusNotify() {
        RequestBuilder builder = initRequestBuilder(WxConst.STATUS_NOTIFY);
        builder.query("lang", "zh_CN");
        builder.query("pass_ticket", user.getPassTicket());
        baseRequest(builder);
        builder.json("Code", 3);
        builder.json("FromUserName", user.getUserName());
        builder.json("ToUserName", user.getUserName());
        builder.json("ClientMsgId", System.currentTimeMillis());
        builder.execute(client, new BaseCallback() {
            @Override
            public void process(Call call, Response response, String content) {
                logger.info("[{}]WX登录StatusNotify send success", instanceId);
            }
        });
    }

    /**
     * 获取二维码 以及 UUID
     */
    public void queryNewUUID() {
        this.queryNewUUID(DEFAULT);
    }

    public void queryNewUUID(BiConsumer<Boolean, String> biConsumer) {

        if (checkLogin()) {
            biConsumer.accept(true, null);
            return;
        }

        if (!this.login && StringUtils.isNotBlank(this.user.getUuid())) {
            String qrCodeUrl = getQrCodeUrl();
            statusListener.onUUIDSuccess(qrCodeUrl);
            biConsumer.accept(false, qrCodeUrl);
            return;
        }

        RequestBuilder builder = initRequestBuilder(WxConst.QR_CODE_API);
        builder.query("appid", appId);
        builder.execute(client, new BaseCallback() {
            @Override
            public void process(Call call, Response response, String content) {
                int idx = content.indexOf("window.QRLogin.uuid");
                if (idx > -1) {
                    idx = content.indexOf("\"", idx);
                    int e_idx = content.indexOf("\"", idx + 1);
                    BaseServer.this.user.setUuid(content.substring(idx + 1, e_idx));
                    String qrCodeUrl = getQrCodeUrl();
                    statusListener.onUUIDSuccess(qrCodeUrl);
                    expireListener.setLastLoginRequest(System.currentTimeMillis());
                    BaseServer.this.waitForLogin();
                    biConsumer.accept(false, qrCodeUrl);
                } else {
                    logger.warn("[{}]没有正常获取到UUID", instanceId);
                    delayTask(() -> reCall(call, this), 2);
                }
            }
        });
    }

    private String getQrCodeUrl() {
        if (StringUtils.isBlank(BaseServer.this.user.getUuid())) {
            return null;
        }
        return this.qrBaseUrl + BaseServer.this.user.getUuid();
    }


    /**
     * 等待用户客户端点击登录
     */
    private void waitForLogin() {
        RequestBuilder builder = initRequestBuilder(WxConst.LOGIN_CHECK_API);
        builder.query("loginicon", "true");
        builder.query("uuid", this.user.getUuid());
        builder.query("tip", "1");
        builder.query("r", WxUtil.random(10));
        builder.query("_", System.currentTimeMillis());
        builder.execute(client, new BaseCallback() {
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
                        logger.info("[{}]请点击手机客户端确认登录", instanceId);
                        BaseServer.this.user.setUserAvatar(StringUtils.substringBetween(content, "window.userAvatar = '", "';"));
                        delayTask(BaseServer.this::waitForLogin, 2);
                        break;
                    case "408":
                        logger.info("[{}]请用手机客户端扫码登录web微信", instanceId);
                        waitForLogin();
                        break;
                    case "400":
                        logger.info("[{}]二维码失效", instanceId);
                        BaseServer.this.user.setUuid(null);
                        if (expireListener.expire() && expireListener.expire(BaseServer.this)) {
                            SyncMonitor.evict(BaseServer.this);
                            return;
                        }
                        BaseServer.this.queryNewUUID();
                        break;
                    default:
                        logger.info("[{}]扫码登录发生未知异常 服务器响应:{}", instanceId, content);
                        if (expireListener.expire(BaseServer.this)) {
                            SyncMonitor.evict(BaseServer.this);
                        }
                }
            }
        });
    }

    public void downloadImg(WxGroup group, WxUser wxUser, Consumer<byte[]> consumer) {
        String tmpPath = this.user.getUin() + "/" + (null == group ? "" : (group.getUserName() + "-")) + wxUser.getUserName() + ".jpg";
        byte[] data = WxUtil.getHeaderImg(tmpPath);
        if (null != data) {
            consumer.accept(data);
        } else {
            RequestBuilder builder;
            if (StringUtils.isNotBlank(wxUser.getHeadImgUrl())) {
                builder = initRequestBuilder("https://" + this.user.getLoginHost() + wxUser.getHeadImgUrl());
            } else {
                builder = initRequestBuilder("https://" + this.user.getLoginHost() + "/cgi-bin/mmwebwx-bin/webwxgeticon");
                builder.query("seq", 0).query("username", wxUser.getUserName());
                if (null != group) {
                    builder.query("chatroomid", group.getEncryChatRoomId());
                }
                if (StringUtils.isNotBlank(user.getSkey())) {
                    builder.query("skey", user.getSkey());
                }
            }

            builder.execute(client, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    reCall(call, this);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        byte[] bytes = response.body().bytes();
                        WxUtil.saveImg(bytes, tmpPath);
                        consumer.accept(bytes);
                    } finally {
                        IOUtils.closeQuietly(response);
                    }
                }
            });
        }
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
    RequestBuilder initRequestBuilder(String path) {
        String url;
        if (path.startsWith("https://") || path.startsWith("http://")) {
            url = path.replace("{host}", user.getLoginHost());
        } else {
            url = "https://" + user.getLoginHost() + (path.startsWith("/") ? path : ("/" + path));
        }
        RequestBuilder builder = RequestBuilder.api(url);
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
    void baseRequest(RequestBuilder requestBuilder) {
        baseRequest().forEach(requestBuilder::json);
    }

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
        this.statusListener = null == statusListener ? DEFAULT_SERVER_STATUS_LISTENER : statusListener;
    }

    public void setGroupMemberDetail(boolean groupMemberDetail) {
        this.groupMemberDetail = groupMemberDetail;
    }

    public void setExpireListener(ExpireListener expireListener) {
        this.expireListener = null == expireListener ? DEFAULT_EXPIRE_LISTENER : expireListener;
    }

    public void setQrBaseUrl(String qrBaseUrl) {
        this.qrBaseUrl = null == qrBaseUrl ? "" : qrBaseUrl;
    }

    public String getInstanceId() {
        return instanceId;
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


    static {
        System.setProperty("jsse.enableSNIExtension", "false");
        //启动时清除临时文件
        WxUtil.deleteTmp();
    }
}
