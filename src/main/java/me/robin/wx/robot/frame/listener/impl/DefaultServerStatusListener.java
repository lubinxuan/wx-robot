package me.robin.wx.robot.frame.listener.impl;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.robot.frame.WxApi;
import me.robin.wx.robot.frame.MsgHandler;
import me.robin.wx.robot.frame.listener.ServerStatusListener;
import me.robin.wx.robot.frame.message.MsgChainHandler;
import me.robin.wx.robot.frame.model.WxMsg;
import me.robin.wx.robot.frame.util.WxUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public class DefaultServerStatusListener implements ServerStatusListener {

    private static final Logger logger = LoggerFactory.getLogger(DefaultServerStatusListener.class);

    private Map<Integer, MsgChainHandler> handlerMap = new ConcurrentHashMap<>();

    @Override
    public void registerMessageHandler(int msgType, MsgHandler msgHandler) {
        handlerMap.computeIfAbsent(msgType, s -> new MsgChainHandler()).addHandler(msgHandler);
    }

    @Override
    public void onUUIDSuccess(String url) {
        logger.debug("登录二维码:{}", url);
    }

    @Override
    public void onAddMsgList(JSONArray addMsgList, WxApi api) {
        for (int i = 0; i < addMsgList.size(); i++) {
            WxMsg message = addMsgList.getObject(i, WxMsg.class);
            String MsgId = message.getMsgID();
            String FromUserName = message.getFromUserName();
            String ToUserName = message.getToUserName();
            message.setContent(WxUtil.revertXml(message.getContent()));
            String Content = message.getContent();
            int msgType = message.getMsgType();
            logger.debug("收到新消息:{} {} {} {} {}", MsgId, FromUserName, ToUserName, Content, msgType);
            MsgHandler msgHandler = this.handlerMap.get(msgType);
            if (null != msgHandler) {
                msgHandler.handle(message, api);
            } else {
                logger.debug("没有定义消息处理器 msgType:{}", msgType);
            }
        }
    }

    @Override
    public void onModContactList(JSONArray modContactList, WxApi api) {

    }

    @Override
    public void onDelContactList(JSONArray delContactList, WxApi api) {

    }

    @Override
    public void onModChatRoomMemberList(JSONArray modChatRoomMemberList, WxApi api) {

    }
}
