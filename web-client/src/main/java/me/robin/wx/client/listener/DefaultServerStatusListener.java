package me.robin.wx.client.listener;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.client.MsgChainHandler;
import me.robin.wx.client.MsgHandler;
import me.robin.wx.client.WxApi;
import me.robin.wx.client.model.WxMsg;
import me.robin.wx.client.model.WxUser;
import me.robin.wx.client.service.ContactService;
import me.robin.wx.client.service.DefaultContactService;
import me.robin.wx.client.util.WxUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public class DefaultServerStatusListener extends EmptyServerStatusListener implements ServerStatusListener {

    private static final Logger logger = LoggerFactory.getLogger(DefaultServerStatusListener.class);

    private ContactService contactService;

    private Map<Integer, MsgChainHandler> handlerMap = new ConcurrentHashMap<>();

    public DefaultServerStatusListener(ContactService contactService) {
        this.contactService = contactService;
    }

    public DefaultServerStatusListener() {
        this.contactService = new DefaultContactService();
    }


    @Override
    public void registerMessageHandler(int msgType, MsgHandler msgHandler) {
        handlerMap.computeIfAbsent(msgType, s -> new MsgChainHandler()).addHandler(msgHandler);
    }

    @Override
    public void onAddMsgList(JSONArray addMsgList, WxApi api) {
        for (int i = 0; i < addMsgList.size(); i++) {
            WxMsg message = addMsgList.getObject(i, WxMsg.class);
            String MsgId = message.getMsgID();
            String FromUserName = getNickName(message.getFromUserName());
            String ToUserName = getNickName(message.getToUserName());
            message.setContent(WxUtil.revertXml(message.getContent()));
            String Content = message.getContent();
            int msgType = message.getMsgType();

            WxUser from = contactService.queryUserByUserName(FromUserName);
            WxUser to = contactService.queryUserByUserName(ToUserName);

            logger.debug("收到新消息:{} {} {} {} {}", MsgId, FromUserName, ToUserName, Content, msgType);
            MsgHandler msgHandler = this.handlerMap.get(msgType);
            if (null != msgHandler) {
                msgHandler.handle(message, api);
            } else {
                logger.debug("没有定义消息处理器 msgType:{}", msgType);
            }
        }
    }

    private String getNickName(String userName) {
        WxUser user = contactService.queryUserByUserName(userName);
        return null == user ? userName : user.getNickName();
    }
}
