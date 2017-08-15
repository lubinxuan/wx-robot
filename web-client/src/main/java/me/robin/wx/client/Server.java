package me.robin.wx.client;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.alibaba.fastjson.util.TypeUtils;
import me.robin.wx.client.listener.MessageSendListener;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxMsg;
import me.robin.wx.client.model.WxUser;
import me.robin.wx.client.service.ContactService;
import me.robin.wx.client.util.RequestBuilder;
import me.robin.wx.client.util.WxUtil;
import okhttp3.Call;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
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
        FilePart part = new FilePart();
        part.setFileName(fileName);
        part.setContentType("image/png");
        part.setData(imgBytes);
        part.setMediaType("pic");
        super.uploadFile(user, part, messageSendListener, (wxUser, mediaId) -> {
            RequestBuilder imgReq = initRequestBuilder(Conf.API.webwxsendmsgimg);
            imgReq.query("fun", "async");
            imgReq.query("f", "json");
            imgReq.query("lang", "zh_CN");
            imgReq.query("pass_ticket", loginUser().getPassTicket());
            sendMsg(wxUser, null, mediaId, 3, imgReq, messageSendListener);
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

    private void sendMsg(WxUser wxUser, String message, String mediaId, int type, RequestBuilder builder, MessageSendListener messageSendListener) {
        baseRequest(builder);
        String localId = System.currentTimeMillis() + WxUtil.random(4);
        builder.json("Scene", 0);
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

        builder.json("Msg", msg);

        builder.execute(client, new BaseJsonCallback() {
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
                    messageSendListener.failure(wxUser.getUserName(), message, ret, TypeUtils.castToString(JSONPath.eval(syncRsp, "BaseResponse.ErrMsg")));
                }
            }
        });
    }

    /**
     * 修改群聊名称
     *
     * @param chatRoom
     * @param name
     */
    public void modChatRoomName(String chatRoom, String name) throws IOException {
        if (!checkLogin()) {
            logger.info("[{}]还未完成登录,不能发送消息", getInstanceId());
        } else {
            WxUser wxUser = contactService.queryUser(chatRoom);
            if (wxUser instanceof WxGroup) {
                //https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxupdatechatroom?fun=modtopic&lang=zh_CN
                RequestBuilder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxupdatechatroom");
                builder.query("fun", "modtopic");
                builder.query("lang", "zh_CN");
                baseRequest(builder);
                builder.json("NewTopic", name);
                builder.json("ChatRoomName", wxUser.getUserName());
                Response response = builder.execute(client);
                JSONObject content = readFromResp(response);
                Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
                if (null != ret && 0 == ret) {
                    logger.info("[{}]群聊名称修改修改成功", getInstanceId());
                } else {
                    logger.info("[{}]群聊名称修改失败:{}", getInstanceId(), content.toJSONString());
                }
            } else {
                logger.warn("[{}]要求修改的不是群", getInstanceId());
            }
        }
    }

    /**
     * 添加好友
     *
     * @param userName
     * @param verifyContent
     */
    public boolean addContact(String userName, String verifyContent) throws IOException {
        if (!checkLogin()) {
            logger.info("[{}]还未完成登录,无法添加好友", getInstanceId());
            return false;
        } else {
            RequestBuilder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxverifyuser");
            builder.query("r", System.currentTimeMillis());
            builder.query("pass_ticket", user.getPassTicket());
            baseRequest(builder);
            builder.json("Opcode", 2);
            builder.json("VerifyUserListSize", 1);
            builder.json("VerifyContent", verifyContent);
            builder.json("SceneListCount", 1);
            builder.json("skey", user.getSkey());
            Map<String, String> verifyUser = new HashMap<>();
            verifyUser.put("Value", userName);
            verifyUser.put("VerifyUserTicket", "");
            builder.json("VerifyUserList", Collections.singleton(verifyUser));
            builder.json("SceneList", new int[]{33});
            Response response = builder.execute(client);
            JSONObject content = readFromResp(response);
            Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
            if (null != ret && 0 == ret) {
                logger.info("[{}]好友请求成功", getInstanceId());
                return true;
            } else {
                logger.info("[{}]好友请求失败:{}", getInstanceId(), content.toJSONString());
                return false;
            }
        }
    }

    public void modifyRemark(String userName, String remark) throws IOException {
        if (!checkLogin()) {
            logger.info("[{}]还未完成登录,无法进行备注修改", getInstanceId());
        } else {
            RequestBuilder builder = initRequestBuilder("/cgi-bin/mmwebwx-bin/webwxoplog");
            builder.query("pass_ticket", user.getPassTicket());
            baseRequest(builder);
            builder.json("CmdId", 2);
            builder.json("RemarkName", remark);
            builder.json("UserName", userName);
            Response response = builder.execute(client);
            JSONObject content = readFromResp(response);
            Integer ret = TypeUtils.castToInt(JSONPath.eval(content, "BaseResponse.Ret"));
            if (null != ret && 0 == ret) {
                logger.info("[{}]备注修改成功", getInstanceId());
            } else {
                logger.info("[{}]备注修改失败:{}", getInstanceId(), content.toJSONString());
            }
        }
    }
}
