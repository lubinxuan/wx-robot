package me.robin.wx.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.alibaba.fastjson.util.TypeUtils;
import me.robin.wx.client.listener.MessageSendListener;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxMsg;
import me.robin.wx.client.model.WxUser;
import me.robin.wx.client.service.ContactService;
import me.robin.wx.client.util.WxUtil;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class Server extends BaseServer {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private final AtomicLong idCounter = new AtomicLong(0);

    private final DateFormat dateFormat = new SimpleDateFormat("EEE d MMM yyyy HH:mm:ss 'GMT'", Locale.CHINA);

    public Server(String instanceId, String appId, ContactService contactService) {
        super(instanceId, appId, contactService);
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * 发送文本消息
     *
     * @param user
     * @param message
     * @param messageSendListener
     */
    public void sendTextMessage(String user, String message, MessageSendListener messageSendListener) {
        sendTextMessage(user, message, 1, messageSendListener);
    }


    public void sendTextMessage(String user, String message, int type, MessageSendListener messageSendListener) {
        sendMsg(user, message, type, Conf.API.webwxsendmsg, messageSendListener);
    }

    @Override
    public void sendImgMessage(String user, String fileName, byte[] imgBytes, MessageSendListener messageSendListener) {
        WxUser wxUser = getWxUser(user, fileName, messageSendListener);
        if (wxUser == null) return;
        RequestBody fileBody = RequestBody.create(MediaType.parse("image/png"), imgBytes);
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        long id = idCounter.incrementAndGet();
        builder.addFormDataPart("id", "WU_FILE_" + id);
        builder.addFormDataPart("name", "file_" + id);
        builder.addFormDataPart("type", "image/png");
        synchronized (this.dateFormat) {
            builder.addFormDataPart("lastModifiedDate", this.dateFormat.format(new Date()));
        }
        builder.addFormDataPart("size", Integer.toString(imgBytes.length));
        builder.addFormDataPart("mediatype", "pic");

        Map<String, Object> requestBody = baseRequest();
        requestBody.put("UploadType", 2);
        requestBody.put("ClientMediaId", System.currentTimeMillis());
        requestBody.put("TotalLen", imgBytes.length);
        requestBody.put("StartPos", 0);
        requestBody.put("DataLen", imgBytes.length);
        requestBody.put("MediaType", 4);
        requestBody.put("FromUserName", this.user.getUserName());
        requestBody.put("ToUserName", wxUser.getUserName());
        requestBody.put("FileMd5", DigestUtils.md5Hex(imgBytes));
        builder.addFormDataPart("uploadmediarequest", JSON.toJSONString(requestBody));
        builder.addFormDataPart("webwx_data_ticket", this.user.getWebwxDataTicket());
        builder.addFormDataPart("pass_ticket", this.user.getPassTicket());
        builder.addFormDataPart("filename", "file_" + id, fileBody);

        Request.Builder requestBuilder = initRequestBuilder(Conf.API.webwxuploadmedia, "f", "json");
        Request request = requestBuilder.post(builder.build()).build();
        this.client.newCall(request).enqueue(new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject content) {
                Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
                if (null != ret && 0 == ret) {
                    logger.info("[{}][]文件上传成功", getInstanceId());
                    String mediaId = content.getString("MediaId");
                    Request.Builder builder1 = initRequestBuilder(Conf.API.webwxsendmsgimg, "fun", "async", "f", "json", "lang", "zh_CN", "pass_ticket", loginUser().getPassTicket());
                    sendMsg(wxUser, null, mediaId, 3, builder1, messageSendListener);
                } else {
                    logger.info("[{}]消息发送失败:{}", getInstanceId(), JSON.toJSONString(content));
                    messageSendListener.failure(user, fileName, TypeUtils.castToString(JSONPath.eval(content, "BaseResponse.ErrMsg")));
                }
            }
        });
    }

    @Override
    public void sendAppMessage(WxMsg message, MessageSendListener messageSendListener) {
        String messageContent = message.getContent().replaceAll("<br/>", "").replaceAll("(<appmsg[\\s\\S]*?</appmsg>)", "$1");
        if (StringUtils.contains(message.getContent(), "<attachid />")) {
            messageContent = messageContent.replaceAll("[\\s\\S]*?(<appmsg[\\s\\S]*?)<attachid />([\\s\\S]*?</appmsg>)[\\s\\S]*?", "$1<attachid>" + message.getMediaId() + "</attachid>$2");
        } else {
            messageContent = messageContent.replaceAll("[\\s\\S]*?(<appmsg[\\s\\S]*?<attachid>)[\\s\\S]*?(</attachid>[\\s\\S]*?</appmsg>)[\\s\\S]*?", "$1" + message.getMediaId() + "$2");
        }
        sendMsg(message.getToUserName(), messageContent, message.getAppMsgType(), Conf.API.webwxsendappmsg, messageSendListener);
    }

    private void sendMsg(String user, String message, int type, String api, MessageSendListener messageSendListener) {
        WxUser wxUser = getWxUser(user, message, messageSendListener);
        if (wxUser == null) return;

        sendMsg(wxUser, message, null, type, initRequestBuilder(api), messageSendListener);
    }

    private void sendMsg(WxUser wxUser, String message, String mediaId, int type, Request.Builder builder, MessageSendListener messageSendListener) {
        Map<String, Object> requestBody = baseRequest();
        String localId = System.currentTimeMillis() + WxUtil.random(4);
        requestBody.put("Scene", 0);
        JSONObject msg = new JSONObject();
        msg.put("Type", type);
        msg.put("Content", StringUtils.isBlank(message) ? "" : message);
        msg.put("FromUserName", this.user.getUserName());
        msg.put("ToUserName", wxUser.getUserName());
        msg.put("LocalID", localId);
        msg.put("ClientMsgId", localId);
        if (StringUtils.isNotBlank(mediaId)) {
            msg.put("MediaId", mediaId);
        }

        requestBody.put("Msg", msg);

        WxUtil.jsonRequest(requestBody, builder::post);

        client.newCall(builder.build()).enqueue(new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject syncRsp) {
                Integer ret = TypeUtils.castToInt(JSONPath.eval(syncRsp, "BaseResponse.Ret"));
                if (null != ret && 0 == ret) {
                    logger.info("[{}]消息发送成功", getInstanceId());
                    String msgId = syncRsp.getString("MsgID");
                    msg.put("MsgId", msgId);
                    messageSendListener.success(wxUser.getUserName(), message, msgId, localId);
                } else {
                    logger.info("[{}]消息发送失败:{}", getInstanceId(), syncRsp.toJSONString());
                    messageSendListener.failure(wxUser.getUserName(), message, TypeUtils.castToString(JSONPath.eval(syncRsp, "BaseResponse.ErrMsg")));
                }
            }
        });
    }

    private WxUser getWxUser(String user, String message, MessageSendListener messageSendListener) {
        if (!checkLogin()) {
            logger.info("[{}]还未完成登录,不能发送消息", getInstanceId());
            messageSendListener.serverNotReady(user, message);
            return null;
        }

        WxUser wxUser = contactService.queryUser(user);
        if (null == wxUser) {
            logger.info("[{}]找不到目标用户,不能发送消息", getInstanceId());
            messageSendListener.userNotFound(user, message);
            return null;
        }

        if (StringUtils.equals(wxUser.getUserName(), this.user.getUserName())) {
            logger.warn("[{}]WEB微信不能给自己发消息", getInstanceId());
            return null;
        }
        return wxUser;
    }

    /**
     * 修改群聊名称
     *
     * @param chatRoom
     * @param name
     */
    public void modChatRoomName(String chatRoom, String name) {
        if (!checkLogin()) {
            logger.info("[{}]还未完成登录,不能发送消息", getInstanceId());
        } else {
            WxUser wxUser = contactService.queryUser(chatRoom);
            if (wxUser instanceof WxGroup) {
                //https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxupdatechatroom?fun=modtopic&lang=zh_CN
                Request.Builder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxupdatechatroom", "fun", "modtopic", "lang", "zh_CN");
                Map<String, Object> requestBody = baseRequest();
                requestBody.put("NewTopic", name);
                requestBody.put("ChatRoomName", wxUser.getUserName());
                WxUtil.jsonRequest(requestBody, builder::post);
                client.newCall(builder.build()).enqueue(new BaseJsonCallback() {
                    @Override
                    void process(Call call, Response response, JSONObject content) {
                        Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
                        if (null != ret && 0 == ret) {
                            logger.info("[{}]群聊名称修改修改成功", getInstanceId());
                        } else {
                            logger.info("[{}]群聊名称修改失败:{}", getInstanceId(), content.toJSONString());
                        }
                    }
                });
            } else {
                logger.warn("[{}]要求修改的不是群", getInstanceId());
            }
        }
    }
}
