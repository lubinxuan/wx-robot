package me.robin.wx.client;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.alibaba.fastjson.util.TypeUtils;
import me.robin.wx.client.listener.MessageSendListener;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxMsg;
import me.robin.wx.client.model.WxUser;
import me.robin.wx.client.service.ContactService;
import me.robin.wx.client.util.WxUtil;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class Server extends BaseServer {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    public Server(String appId, ContactService contactService) {
        super(appId, contactService);
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
    public void sendAppMessage(WxMsg message, MessageSendListener messageSendListener) {
        String messageContent = message.getContent().replaceAll("<br/>","").replaceAll("(<appmsg[\\s\\S]*?</appmsg>)","$1");
        if(StringUtils.contains(message.getContent(),"<attachid />")){
            messageContent = messageContent.replaceAll("[\\s\\S]*?(<appmsg[\\s\\S]*?)<attachid />([\\s\\S]*?</appmsg>)[\\s\\S]*?", "$1<attachid>" + message.getMediaId() + "</attachid>$2");
        }else{
            messageContent = messageContent.replaceAll("[\\s\\S]*?(<appmsg[\\s\\S]*?<attachid>)[\\s\\S]*?(</attachid>[\\s\\S]*?</appmsg>)[\\s\\S]*?", "$1" + message.getMediaId() + "$2");
        }
        sendMsg(message.getToUserName(), messageContent, message.getAppMsgType(), Conf.API.webwxsendappmsg, messageSendListener);
    }

    private void sendMsg(String user, String message, int type, String api, MessageSendListener messageSendListener) {
        if (!checkLogin()) {
            logger.info("还未完成登录,不能发送消息");
            messageSendListener.serverNotReady(user, message);
            return;
        }

        WxUser wxUser = contactService.queryUser(user);
        if (null == wxUser) {
            logger.info("找不到目标用户,不能发送消息");
            messageSendListener.userNotFound(user, message);
            return;
        }

        if (StringUtils.equals(wxUser.getUserName(), this.user.getUserName())) {
            logger.warn("WEB微信不能给自己发消息");
            return;
        }

        Request.Builder builder = initRequestBuilder(api);
        Map<String, Object> requestBody = baseRequest();
        String localId = System.currentTimeMillis() + WxUtil.random(4);
        requestBody.put("Scene", 0);
        JSONObject msg = new JSONObject();
        msg.put("Type", type);
        msg.put("Content", message);
        msg.put("FromUserName", this.user.getUserName());
        msg.put("ToUserName", wxUser.getUserName());
        msg.put("LocalID", localId);
        msg.put("ClientMsgId", localId);

        requestBody.put("Msg", msg);

        WxUtil.jsonRequest(requestBody, builder::post);

        client.newCall(builder.build()).enqueue(new BaseJsonCallback() {
            @Override
            void process(Call call, Response response, JSONObject syncRsp) {
                Integer ret = TypeUtils.castToInt(JSONPath.eval(syncRsp, "BaseResponse.Ret"));
                if (null != ret && 0 == ret) {
                    logger.info("消息发送成功");
                    String msgId = syncRsp.getString("MsgID");
                    msg.put("MsgId", msgId);
                    messageSendListener.success(user, message, msgId, localId);
                } else {
                    logger.info("消息发送失败:{}", syncRsp.toJSONString());
                    messageSendListener.failure(user, message);
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
    public void modChatRoomName(String chatRoom, String name) {
        if (!checkLogin()) {
            logger.info("还未完成登录,不能发送消息");
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
                            logger.info("群聊名称修改修改成功");
                        } else {
                            logger.info("群聊名称修改失败:{}", content.toJSONString());
                        }
                    }
                });
            } else {
                logger.warn("要求修改的不是群");
            }
        }
    }
}
