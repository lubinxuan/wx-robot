package me.robin.wx.robot.frame;

import me.robin.wx.robot.frame.listener.MessageSendListener;
import me.robin.wx.robot.frame.model.LoginUser;

/**
 * Created by xuanlubin on 2017/4/24.
 */
public interface WxApi {

    LoginUser loginUser();

    void sendTextMessage(String user, String message, MessageSendListener messageSendListener);

    void sendTextMessage(String user, String message, int type, MessageSendListener messageSendListener);
}
