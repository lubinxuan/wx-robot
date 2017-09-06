package me.robin.wx.client.listener;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.client.MsgHandler;
import me.robin.wx.client.WxApi;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface ServerStatusListener {
    void registerMessageHandler(int msgType, MsgHandler msgHandler);

    void onAddMsgList(JSONArray addMsgList, WxApi api);

    void onModContactList(JSONArray modContactList, WxApi api);

    void onDelContactList(JSONArray delContactList, WxApi api);

    void onModChatRoomMemberList(JSONArray modChatRoomMemberList, WxApi api);
}
