package me.robin.wx.robot.frame.service;

import me.robin.wx.robot.frame.model.WxMsg;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface MessageService {
    void saveMessage(WxMsg message);

    WxMsg findMessageByUserAndMid(String userName, String mid);
}
