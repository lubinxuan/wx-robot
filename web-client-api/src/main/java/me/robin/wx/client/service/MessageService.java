package me.robin.wx.client.service;


import me.robin.wx.client.model.WxMsg;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface MessageService {
    void saveMessage(WxMsg message);

    WxMsg findMessageByUserAndMid(String userName, String mid);
}
