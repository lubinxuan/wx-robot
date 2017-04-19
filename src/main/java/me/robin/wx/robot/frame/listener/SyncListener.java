package me.robin.wx.robot.frame.listener;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.robot.frame.Server;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface SyncListener {
    void onAddMsgList(JSONArray addMsgList,Server server);

    void onModContactList(JSONArray modContactList,Server server);

    void onDelContactList(JSONArray delContactList,Server server);

    void onModChatRoomMemberList(JSONArray modChatRoomMemberList,Server server);
}
