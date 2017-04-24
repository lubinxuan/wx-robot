package me.robin.wx.robot.frame.listener;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.robot.frame.WxApi;
import me.robin.wx.robot.frame.MsgHandler;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface ServerStatusListener {

    void registerMessageHandler(int msgType, MsgHandler msgHandler);

    void onUUIDSuccess(String url);

    void onAddMsgList(JSONArray addMsgList,WxApi api);

    void onModContactList(JSONArray modContactList,WxApi api);

    void onDelContactList(JSONArray delContactList,WxApi api);

    void onModChatRoomMemberList(JSONArray modChatRoomMemberList,WxApi api);
}
