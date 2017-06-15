package me.robin.wx.client;


import me.robin.wx.client.listener.MessageSendListener;
import me.robin.wx.client.model.LoginUser;
import me.robin.wx.client.model.WxMsg;

/**
 * Created by xuanlubin on 2017/4/24.
 */
public interface WxApi {

    LoginUser loginUser();

    void sendTextMessage(String user, String message, MessageSendListener messageSendListener);
    void sendAppMessage(WxMsg message, MessageSendListener messageSendListener);

    void sendTextMessage(String user, String message, int type, MessageSendListener messageSendListener);

    default void createGroup(){}
    default void modifyGroupName(){}
    default void deleteGroupUser(){}
}
