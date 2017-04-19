package me.robin.wx.robot.frame.listener;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface MessageSendListener {
    void userNotFound(String user, String message);
    void serverNotReady(String user,String message);
    void success(String user, String message, String messageId, String localId);
    void failure(String user,String message);
}
