package me.robin.wx.robot.frame.message;

import me.robin.wx.robot.frame.MsgHandler;
import me.robin.wx.robot.frame.Server;
import me.robin.wx.robot.frame.model.WxMsg;
import me.robin.wx.robot.frame.service.MessageService;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public class MessageSaveHandler implements MsgHandler {
    private MessageService messageService;

    public MessageSaveHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void handle(WxMsg message, Server server) {
        messageService.saveMessage(message);
    }
}
