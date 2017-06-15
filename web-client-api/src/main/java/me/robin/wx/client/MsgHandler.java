package me.robin.wx.client;


import me.robin.wx.client.model.WxMsg;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public interface MsgHandler {
    void handle(WxMsg message, WxApi api);
}
