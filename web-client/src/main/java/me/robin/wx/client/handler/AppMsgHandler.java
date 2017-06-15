package me.robin.wx.client.handler;

import me.robin.wx.client.MsgHandler;
import me.robin.wx.client.WxApi;
import me.robin.wx.client.listener.MessageSendListener;
import me.robin.wx.client.model.WxMsg;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public class AppMsgHandler implements MsgHandler {

    private MessageSendListener messageSendListener;

    public AppMsgHandler(MessageSendListener messageSendListener) {
        this.messageSendListener = messageSendListener;
    }

    @Override
    public void handle(WxMsg message, WxApi api) {
        api.sendAppMessage(message,this.messageSendListener);
    }
}
