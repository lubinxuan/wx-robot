package me.robin.wx.client.listener;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.client.MsgHandler;
import me.robin.wx.client.WxApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Lubin.Xuan on 2017-06-28.
 */
public class EmptyServerStatusListener implements ServerStatusListener {

    private static final Logger logger = LoggerFactory.getLogger(EmptyServerStatusListener.class);

    @Override
    public void registerMessageHandler(int msgType, MsgHandler msgHandler) {

    }

    @Override
    public void onUUIDSuccess(String url) {
        logger.debug("登录二维码:{}", url);
    }

    @Override
    public void onAddMsgList(JSONArray addMsgList, WxApi api) {

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

    @Override
    public void loginSuccess() {

    }
}
