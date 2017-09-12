package me.robin.wx.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.alibaba.fastjson.util.TypeUtils;
import lombok.Getter;
import lombok.Setter;
import me.robin.wx.client.cookie.CookieInterceptor;
import me.robin.wx.client.cookie.MemoryCookieStore;
import me.robin.wx.client.listener.EmptyServerStatusListener;
import me.robin.wx.client.listener.LoginProcessListener;
import me.robin.wx.client.listener.MessageSendListener;
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
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.Closeable;
import java.io.IOException;
import java.net.CookieStore;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by xuanlubin on 2017/4/18.
 * 微信WEB客户端
 */
public abstract class BaseServer implements Runnable, WxApi, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(BaseServer.class);

    private static final Timer delayTaskScheduler = new Timer("DelayTaskScheduler");

    private static final ExpireListener DEFAULT_EXPIRE_LISTENER = new ExpireListener() {
        @Override
        public boolean expire(BaseServer server, String uid) {
            return false;
        }
    };

    private static final ServerStatusListener DEFAULT_SERVER_STATUS_LISTENER = new EmptyServerStatusListener();

    private static final BiConsumer<Boolean, String> DEFAULT = (aBoolean, s) -> {

    };

    private static final LoginProcessListener DEFAULT_LOGIN_PROCESS = new LoginProcessListener() {
    };

    private String qrBaseUrl = "https://login.weixin.qq.com/qrcode/";

    private String appId;

    private final String instanceId;

    final LoginUser user = new LoginUser();

    OkHttpClient client;

    private volatile boolean closed = false;

    protected ServerStatusListener statusListener = DEFAULT_SERVER_STATUS_LISTENER;
    protected LoginProcessListener loginProcessListener = DEFAULT_LOGIN_PROCESS;

    private ExpireListener expireListener = DEFAULT_EXPIRE_LISTENER;

    protected ContactService contactService = new DefaultContactService();

    protected CookieStore cookieStore;

    private volatile boolean login = false;

    protected LinkedBlockingQueue<String[]> lazyUpdateGroupQueue = new LinkedBlockingQueue<>();
    protected LinkedBlockingQueue<String[]> lazyUpdateGroupMemberQueue = new LinkedBlockingQueue<>();

    private boolean groupMemberDetail = true;

    private Thread thread;

    private final AtomicLong idCounter = new AtomicLong(0);

    private final DateFormat dateFormat = new SimpleDateFormat("EEE d MMM yyyy HH:mm:ss 'GMT'", Locale.CHINA);

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

        this.dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
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
                    JSONObject syncKey = responseJson.getJSONObject("SyncKey");
                    BaseServer.this.user.setSyncKey(syncKey);
                    BaseServer.this.user.setSyncCheckKey(syncKey);
                    statusNotify();
                    contactService.updateContact(responseJson.getJSONArray("ContactList"));
                    getContact();
                } else {
                    String message = TypeUtils.castToString(JSONPath.eval(responseJson, "BaseResponse.ErrMsg"));
                    logger.warn("web微信初始化失败 ret:{} errMsg:{}", ret, message);
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
                logger.info("获取到联系人列表");
                syncCheck();
                contactService.updateContact(content.getJSONArray("MemberList"));
                login = true;
                loginProcessListener.onContactSuccess();
                synchronized (BaseServer.this.user) {
                    BaseServer.this.user.notifyAll();
                }
                List<WxGroup> wxGroupList = contactService.listAllGroup();
                wxGroupList.forEach(g -> addGroupLazyUpdateTask(g.getUserName()));
                if (null != BaseServer.this.thread) {
                    return;
                }
                BaseServer.this.thread = new Thread(new ContactUpdateTask());
                BaseServer.this.thread.setName("GroupContactSyncThread-" + instanceId);
                BaseServer.this.thread.start();
            }
        };
        builder.execute(client, callback);
    }

    /**
     * 获取通讯录
     */
    private void batchGetContact() {
        Map<String, String> updateContactMap = new HashMap<>();
        while (!closed) {
            List<String[]> groupNameList = new ArrayList<>();
            boolean fromGroup = true;
            int rows = lazyUpdateGroupQueue.drainTo(groupNameList, 10);
            if (rows < 1) {
                rows = lazyUpdateGroupMemberQueue.drainTo(groupNameList, 50);
                fromGroup = false;
            }
            if (rows < 1) {
                return;
            }
            for (String[] contactInfo : groupNameList) {
                updateContactMap.put(contactInfo[0], contactInfo.length > 1 ? contactInfo[1] : "");
            }
            try {
                batchGetContactTask(updateContactMap, null);
                updateContactMap.clear();
            } catch (Exception e) {
                logger.warn("获取通讯录详情");
            }
            if (!updateContactMap.isEmpty()) {
                if (fromGroup) {
                    lazyUpdateGroupQueue.addAll(groupNameList);
                } else {
                    lazyUpdateGroupMemberQueue.addAll(groupNameList);
                }
                synchronized (instanceId) {
                    instanceId.notify();
                }
                break;
            }
        }
    }

    public boolean updateUserContact(String userName, Consumer<WxUser> successCallback) throws IOException {
        Consumer<List<WxUser>> listConsumer = null == successCallback ? null : wxUsers -> {
            if (null != wxUsers && !wxUsers.isEmpty()) {
                successCallback.accept(wxUsers.get(0));
            }
        };
        return batchGetContactTask(Collections.singletonMap(userName, null), listConsumer);
    }

    public void offerGroupUpdateTask(WxGroup wxGroup) {

        WxUser group = this.contactService.queryUserByUserName(wxGroup.getUserName());

        if (null == group) {
            this.contactService.addWxUser(wxGroup);
        }

        if (null == group || StringUtils.isBlank(group.getEncryChatRoomId())) {
            this.addGroupLazyUpdateTask(wxGroup.getUserName());
        } else if (groupMemberDetail) {
            Set<String> memberFilter = group.getMemberList().stream().map(WxUser::getNickName).collect(Collectors.toSet());
            for (WxUser user : wxGroup.getMemberList()) {
                if (memberFilter.add(user.getUserName())) {
                    lazyUpdateGroupMemberQueue.offer(new String[]{user.getUserName(), group.getEncryChatRoomId()});
                    synchronized (instanceId) {
                        instanceId.notify();
                    }
                }
            }
        }
    }


    public void addGroupLazyUpdateTask(String groupUserName) {
        if (!contactService.groupInitialized(groupUserName)) {
            this.lazyUpdateGroupQueue.offer(new String[]{groupUserName});
            synchronized (instanceId) {
                instanceId.notify();
            }
        }
    }


    private boolean batchGetContactTask(Map<String, String> updateContactMap, Consumer<List<WxUser>> listConsumer) throws IOException {
        if (updateContactMap.isEmpty()) {
            return true;
        }
        logger.info("开始同步通讯录详情:{}", JSON.toJSONString(updateContactMap));
        RequestBuilder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxbatchgetcontact");
        builder.query("type", "ex");
        builder.query("r", System.currentTimeMillis());
        builder.query("lang", "zh_CN");
        builder.json("Count", updateContactMap.size());
        List<Map<String, String>> updateContactList = new ArrayList<>(updateContactMap.size());
        for (Map.Entry<String, String> entry : updateContactMap.entrySet()) {
            Map<String, String> param = new HashMap<>();
            param.put("EncryChatRoomId", StringUtils.isBlank(entry.getValue()) ? "" : entry.getValue());
            param.put("UserName", entry.getKey());
            updateContactList.add(param);
        }
        builder.json("List", updateContactList);
        baseRequest(builder);
        Response response = builder.execute(client);
        JSONObject content = readFromResp(response);
        Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
        if (null != ret && ret == 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("[{}]同步到群组信息   content:{}", instanceId, content.toJSONString());
            } else {
                logger.info("[{}]同步到群组信息", instanceId);
            }
            JSONArray contactList = content.getJSONArray("ContactList");
            List<WxUser> userList = new ArrayList<>();
            for (int i = 0; i < contactList.size(); i++) {
                WxUser wxUser = WxUtil.parse(contactList.getJSONObject(i));
                if (StringUtils.isBlank(wxUser.getEncryChatRoomId()) && StringUtils.isBlank(updateContactMap.get(wxUser.getUserName()))) {
                    continue;
                }
                userList.add(wxUser);
                if (wxUser.isGroup()) {
                    WxUser wxGroup = contactService.queryUserByUserName(wxUser.getUserName());
                    wxUser.getMemberList().forEach(member -> member.setFollow(contactService.contain(member.getUserName())));
                    if (wxGroup == null) {
                        contactService.addWxUser(wxUser);
                        wxGroup = wxUser;
                    } else {
                        wxGroup.setMemberCount(wxUser.getMemberCount());
                        wxGroup.setMemberList(wxUser.getMemberList());
                        wxGroup.setEncryChatRoomId(wxUser.getEncryChatRoomId());
                    }
                    if (groupMemberDetail) {
                        //群组成员信息加入等待更新列表
                        for (WxUser user : wxGroup.getMemberList()) {
                            lazyUpdateGroupMemberQueue.offer(new String[]{user.getUserName(), wxUser.getEncryChatRoomId()});
                        }
                    }
                } else {
                    if (StringUtils.isNotBlank(wxUser.getEncryChatRoomId())) {
                        wxUser.setFollow(contactService.contain(wxUser.getUserName()));
                        contactService.updateGroupUserInfo(wxUser);
                    } else {
                        if (StringUtils.isBlank(updateContactMap.get(wxUser.getUserName()))) {
                            contactService.addWxUser(wxUser);
                        }
                    }
                }
            }
            if (null != listConsumer) {
                listConsumer.accept(userList);
            }
            return true;
        } else {
            logger.warn("[{}]群组信息获取异常:{}", instanceId, JSON.toJSONString(content));
            return false;
        }
    }


    /**
     * long poll sync
     */
    private void syncCheck() {
        if (closed) {
            return;
        }
        //https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck?r=1492576604964&skey=%40crypt_cfbf95a5_ddaa708f9a9ac2e2d7a95a0a433b3c67&sid=GQ7GDgvoL6Y8FrQY&uin=1376796829&deviceid=e644133084693151&synckey=1_657703788%7C2_657703818%7C3_657703594%7C1000_1492563241&_=1492576604148
        String url = "https://webpush.{host}/cgi-bin/mmwebwx-bin/synccheck";
        RequestBuilder builder = initRequestBuilder(url);
        builder.query("r", System.currentTimeMillis());
        builder.query("skey", user.getSkey());
        builder.query("uin", user.getUin());
        builder.query("sid", user.getSid());
        builder.query("deviceid", WxUtil.randomDeviceId());
        builder.query("synckey", formatSyncKey(user.getSyncCheckKey()));
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
                        if (selector != 0) {
                            logger.info("有信息需要同步 selector:{}", selector);
                            sync();
                            return;
                        }
                        break;
                    case 1101:
                        login = false;
                        logger.info("客户端退出了");
                        WxUtil.deleteImgTmpDir(BaseServer.this.user.getUin());
                        BaseServer.this.contactService.clearContact();
                        if (!expireListener.expire(BaseServer.this, BaseServer.this.user.getUin())) {
                            queryNewUUID();
                        } else {
                            SyncMonitor.evict(BaseServer.this);
                        }
                        return;
                    case 1102:
                        login = false;
                        logger.error("会话异常");
                        WxUtil.deleteImgTmpDir(BaseServer.this.user.getUin());
                        BaseServer.this.contactService.clearContact();
                        SyncMonitor.evict(BaseServer.this);
                        return;
                    default:
                        logger.warn("没有正常获取到同步信息 : {}", content);
                }
                delayTask(BaseServer.this::syncCheck, 3);
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
                    logger.info("消息同步成功");
                    JSONObject syncKey = syncRsp.getJSONObject("SyncKey");
                    JSONObject syncCheckKey = syncRsp.getJSONObject("SyncCheckKey");
                    if (StringUtils.equals(syncKey.toJSONString(), user.getSyncKey().toJSONString())) {
                        logger.warn("同步key没有更新");
                        syncNow = false;
                    } else {
                        user.setSyncCheckKey(syncCheckKey);
                        user.setSyncKey(syncKey);
                    }
                    String skey = syncRsp.getString("SKey");
                    if (StringUtils.isNotBlank(skey)) {
                        logger.info("更新用户SKey");
                        user.setSkey(skey);
                    }
                    if (null != statusListener) {
                        JSONArray addMsgList = syncRsp.getJSONArray("AddMsgList");
                        checkGroupNotInContactList(addMsgList);
                        statusListener.onAddMsgList(addMsgList, BaseServer.this);
                        statusListener.onDelContactList(syncRsp.getJSONArray("DelContactList"), BaseServer.this);
                        statusListener.onModChatRoomMemberList(syncRsp.getJSONArray("ModChatRoomMemberList"), BaseServer.this);
                        statusListener.onModContactList(syncRsp.getJSONArray("ModContactList"), BaseServer.this);
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
        };
        builder.execute(client, callback);
    }

    private void checkGroupNotInContactList(JSONArray msgList) {
        for (int i = 0; i < msgList.size(); i++) {
            JSONObject message = msgList.getJSONObject(i);
            String statusNotifyUserName = message.getString("StatusNotifyUserName");
            String[] userNames = StringUtils.split(statusNotifyUserName, ",");
            for (String userName : userNames) {
                if (StringUtils.startsWith(userName, "@@")) {
                    this.addGroupLazyUpdateTask(userName);
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
                        if (loginProcessListener.onUinSuccess(user.getUin())) {
                            user.setSid(wxSidCookie.get().value());
                            logger.info("登录成功");
                            BaseServer.this.init();
                        }
                    } else {
                        logger.warn("微信登录异常,没有读取到wxsid");
                        delayTask(() -> reCall(call, this), 3);
                    }
                } else if ("1203".equals(ret)) {
                    String message = WxUtil.getValueFromXml(content, "message");
                    expireListener.expire(BaseServer.this, "");
                    loginProcessListener.updateScanStatus(1203, StringUtils.isBlank(message) ? "为了你的帐号安全，新注册的微信号不能登录网页微信" : message);
                } else {
                    logger.error("WEB微信登录异常 ret:{} handler:{}", ret, content);
                    delayTask(() -> reCall(call, this), 3);
                }
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
                logger.info("WX登录StatusNotify send success");
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
            loginProcessListener.onQrUrlSuccess(qrCodeUrl);
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
                    loginProcessListener.onQrUrlSuccess(qrCodeUrl);
                    expireListener.setLastLoginRequest(System.currentTimeMillis());
                    BaseServer.this.waitForLogin();
                    biConsumer.accept(false, qrCodeUrl);
                } else {
                    logger.warn("没有正常获取到UUID");
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
                int statusCode = NumberUtils.toInt(status, -1);
                switch (statusCode) {
                    case 200:
                        String url = StringUtils.substringBetween(content, "window.redirect_uri=\"", "\"");
                        HttpUrl httpUrl = HttpUrl.parse(url);
                        user.setLoginHost(httpUrl.host());
                        BaseServer.this.login(url);
                        break;
                    case 201:
                        logger.info("请点击手机客户端确认登录");
                        BaseServer.this.user.setUserAvatar(StringUtils.substringBetween(content, "window.userAvatar = '", "';"));
                        delayTask(BaseServer.this::waitForLogin, 2);
                        loginProcessListener.updateScanStatus(statusCode, "请点击手机客户端确认登录");
                        break;
                    case 408:
                        logger.info("请用手机客户端扫码登录web微信");
                        waitForLogin();
                        loginProcessListener.updateScanStatus(statusCode, "请用手机客户端扫码登录web微信");
                        break;
                    case 400:
                        logger.info("二维码失效");
                        BaseServer.this.user.setUuid(null);
                        loginProcessListener.updateScanStatus(statusCode, "二维码失效");
                        if (expireListener.expire() && expireListener.expire(BaseServer.this, BaseServer.this.user.getUin())) {
                            SyncMonitor.evict(BaseServer.this);
                            return;
                        }
                        BaseServer.this.queryNewUUID();
                        break;
                    default:
                        logger.info("扫码登录发生未知异常 服务器响应:{}", content);
                        if (expireListener.expire(BaseServer.this, BaseServer.this.user.getUin())) {
                            SyncMonitor.evict(BaseServer.this);
                        }
                }
            }
        });
    }

    public void downloadImg(WxGroup group, WxUser wxUser, Consumer<byte[]> consumer) {
        String tmpPath = this.user.getUin() + "/" + wxUser.getUserName() + ".jpg";
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


    public void uploadFile(String user, FilePart filePart, MessageSendListener messageSendListener, BiConsumer<WxUser, String> successUploadCallback) {
        WxUser wxUser = getWxUser(user, filePart.getFileName(), messageSendListener);
        if (wxUser == null) return;
        RequestBuilder builder = initRequestBuilder(Conf.API.webwxuploadmedia);
        builder.query("f", "json");
        long id = idCounter.incrementAndGet();
        builder.post("id", "WU_FILE_" + id);
        builder.post("name", "file_" + id);
        builder.post("type", "image/png");
        synchronized (this.dateFormat) {
            builder.post("lastModifiedDate", this.dateFormat.format(new Date()));
        }
        builder.post("size", filePart.getData().length);
        builder.post("mediatype", filePart.getMediaType());

        Map<String, Object> requestBody = baseRequest();
        requestBody.put("UploadType", 2);
        requestBody.put("ClientMediaId", System.currentTimeMillis());
        requestBody.put("TotalLen", filePart.getData().length);
        requestBody.put("StartPos", 0);
        requestBody.put("DataLen", filePart.getData().length);
        requestBody.put("MediaType", 4);
        requestBody.put("FromUserName", this.user.getUserName());
        requestBody.put("ToUserName", wxUser.getUserName());
        requestBody.put("FileMd5", DigestUtils.md5Hex(filePart.getData()));
        builder.post("uploadmediarequest", JSON.toJSONString(requestBody));
        builder.post("webwx_data_ticket", this.user.getWebwxDataTicket());
        builder.post("pass_ticket", this.user.getPassTicket());
        builder.file("filename", "file_" + id, filePart.getData(), filePart.getContentType());

        builder.execute(client, new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject content) {
                Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
                if (null != ret && 0 == ret) {
                    logger.info("文件上传成功");
                    String mediaId = content.getString("MediaId");
                    successUploadCallback.accept(wxUser, mediaId);
                } else {
                    logger.info("消息发送失败:{}", JSON.toJSONString(content));
                    messageSendListener.failure(user, filePart.getFileName(), ret, TypeUtils.castToString(JSONPath.eval(content, "BaseResponse.ErrMsg")));
                }
            }
        });
    }

    WxUser getWxUser(String user, String message, MessageSendListener messageSendListener) {
        if (!checkLogin()) {
            logger.info("[{}]还未完成登录,不能发送消息", getInstanceId());
            messageSendListener.serverNotReady(user, message);
            return null;
        }

        WxUser wxUser = contactService.queryUser(user);
        if (null == wxUser) {
            if (StringUtils.equals(user, loginUser().getUserName())) {
                wxUser = new WxUser();
                wxUser.setUserName(loginUser().getUserName());
                wxUser.setNickName(loginUser().getNickName());
                return wxUser;
            }
            logger.info("[{}]找不到目标用户,不能发送消息", getInstanceId());
            messageSendListener.userNotFound(user, message);
            return null;
        }
/*
        if (StringUtils.equals(wxUser.getUserName(), this.user.getUserName())) {
            logger.warn("[{}]WEB微信不能给自己发消息", getInstanceId());
            return null;
        }*/
        return wxUser;
    }

    /**
     * 重新执行请求
     *
     * @param call
     * @param callback
     */
    void reCall(Call call, Callback callback) {
        if (this.closed) {
            logger.warn("[{}]客户端已关闭", instanceId);
            return;
        }


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
    private String formatSyncKey(JSONObject keys) {
        JSONArray array = keys.getJSONArray("List");
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
                if (BaseServer.this.closed) {
                    return;
                }
                delayTask.execute();
            }
        }, (long) (delaySecond * 1000));
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        this.client.dispatcher().cancelAll();
        this.client.dispatcher().executorService().shutdownNow();
        if (StringUtils.isNotBlank(this.user.getUin())) {
            WxUtil.deleteImgTmpDir(this.user.getUin());
        }
        synchronized (instanceId) {
            instanceId.notify();
        }
    }

    public void setStatusListener(ServerStatusListener statusListener) {
        this.statusListener = null == statusListener ? DEFAULT_SERVER_STATUS_LISTENER : statusListener;
    }

    public void setLoginProcessListener(LoginProcessListener loginProcessListener) {
        this.loginProcessListener = loginProcessListener;
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


    private class ContactUpdateTask implements Runnable {

        @Override
        public void run() {
            while (!closed) {
                if (lazyUpdateGroupMemberQueue.isEmpty() && lazyUpdateGroupQueue.isEmpty()) {
                    synchronized (instanceId) {
                        try {
                            instanceId.wait();
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
                batchGetContact();
            }
            logger.info("通讯录同步线程退出");
        }
    }

    public abstract class BaseCallback implements Callback {

        @Override
        public void onFailure(Call call, IOException e) {
            prepareMDC();
            logger.error("{}", call.request().url().toString(), e);
            reCall(call, this);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            prepareMDC();
            try {
                String content = ResponseReadUtils.read(response);
                this.process(call, response, content);
            } finally {
                IOUtils.closeQuietly(response);
            }
        }

        abstract void process(Call call, Response response, String content);
    }

    public static JSONObject readFromResp(Response response) throws IOException {
        try {
            String content = ResponseReadUtils.read(response);
            return JSON.parseObject(content);
        } finally {
            IOUtils.closeQuietly(response);
        }
    }

    private void prepareMDC() {
        if (StringUtils.isBlank(user.getUin())) {
            MDC.put("tag", "[" + instanceId + "]");
        } else {
            MDC.put("tag", "[" + instanceId + ":" + user.getUin() + "]");
        }
    }


    public abstract class BaseJsonCallback implements Callback {

        @Override
        public void onFailure(Call call, IOException e) {
            prepareMDC();
            logger.error("{}", call.request().url().toString(), e);
            reCall(call, this);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            prepareMDC();
            try {
                this.process(call, response, readFromResp(response));
            } finally {
                IOUtils.closeQuietly(response);
            }
        }

        abstract void process(Call call, Response response, JSONObject content);
    }

    @Getter
    @Setter
    public class FilePart {
        private String fileName;
        private byte[] data;
        private String mediaType;
        private String contentType;
    }


    static {
        System.setProperty("jsse.enableSNIExtension", "false");
        //启动时清除临时文件
        WxUtil.deleteTmp();
    }
}
