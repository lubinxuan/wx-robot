package me.robin.wx.client;

import me.robin.wx.client.listener.MessageSendListener;
import me.robin.wx.client.listener.DefaultServerStatusListener;
import me.robin.wx.client.handler.AppMsgHandler;
import me.robin.wx.client.handler.MessageSaveHandler;
import me.robin.wx.client.handler.RevokeMsgHandler;
import me.robin.wx.client.service.ContactService;
import me.robin.wx.client.service.MessageService;
import me.robin.wx.client.service.DefaultContactService;
import me.robin.wx.client.service.DefaultMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class Robot {

    private static final Logger logger = LoggerFactory.getLogger(Robot.class);

    public static void main(String[] args) throws InterruptedException {
        ContactService contactService = new DefaultContactService();
        Server server = new Server(WxConst.APP_ID, contactService);
        MessageSendListener messageSendListener = new MessageSendListener() {
            @Override
            public void userNotFound(String user, String message) {

            }

            @Override
            public void serverNotReady(String user, String message) {
                server.waitLoginDone();
                server.sendTextMessage(user, message, this);
            }

            @Override
            public void success(String user, String message, String messageId, String localId) {
                logger.debug("发送完成:{} {}", messageId, localId);
            }

            @Override
            public void failure(String user, String message) {

            }
        };

        MessageService messageService = new DefaultMessageService();

        DefaultServerStatusListener serverStatusListener = new DefaultServerStatusListener(contactService);
        RevokeMsgHandler revokeMsgHandler = new RevokeMsgHandler(messageSendListener,messageService,contactService);
        revokeMsgHandler.enable("lb_test");
        serverStatusListener.registerMessageHandler(WxConst.MessageType.REVOKE_MSG, revokeMsgHandler);
        serverStatusListener.registerMessageHandler(WxConst.MessageType.APP_MSG, new AppMsgHandler(messageSendListener));

        MessageSaveHandler messageSaveHandler = new MessageSaveHandler(messageService);
        serverStatusListener.registerMessageHandler(WxConst.MessageType.TEXT, messageSaveHandler);
        serverStatusListener.registerMessageHandler(WxConst.MessageType.IMG, messageSaveHandler);
        serverStatusListener.registerMessageHandler(WxConst.MessageType.VIDEO, messageSaveHandler);
        serverStatusListener.registerMessageHandler(WxConst.MessageType.VOICE, messageSaveHandler);

        server.setStatusListener(serverStatusListener);
        server.run();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                server.sendTextMessage("AgFighter", "消息发送：" + new Date(), messageSendListener);
            }
        }, 0, 15 * 60 * 1000);
        TimeUnit.DAYS.sleep(5);
    }
}
