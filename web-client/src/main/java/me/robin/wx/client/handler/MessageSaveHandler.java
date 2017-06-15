package me.robin.wx.client.handler;

import me.robin.wx.client.MsgHandler;
import me.robin.wx.client.WxApi;
import me.robin.wx.client.model.WxMsg;
import me.robin.wx.client.service.MessageService;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public class MessageSaveHandler implements MsgHandler {
    private MessageService messageService;

    public MessageSaveHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void handle(WxMsg message, WxApi api) {
        messageService.saveMessage(message);
    }
}
