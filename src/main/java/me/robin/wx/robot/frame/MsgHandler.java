package me.robin.wx.robot.frame;

import me.robin.wx.robot.frame.model.WxMsg;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public interface MsgHandler {
    void handle(WxMsg message, Server server);
}
