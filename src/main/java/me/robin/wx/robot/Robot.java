package me.robin.wx.robot;

import me.robin.wx.robot.frame.Server;
import me.robin.wx.robot.frame.WxConst;
import me.robin.wx.robot.frame.listener.MessageSendListener;
import me.robin.wx.robot.frame.listener.impl.DefaultServerStatusListener;
import me.robin.wx.robot.frame.message.AppMsgHandler;
import me.robin.wx.robot.frame.message.MessageSaveHandler;
import me.robin.wx.robot.frame.message.RevokeMsgHandler;
import me.robin.wx.robot.frame.service.ContactService;
import me.robin.wx.robot.frame.service.MessageService;
import me.robin.wx.robot.frame.service.impl.ContactServiceImpl;
import me.robin.wx.robot.frame.service.impl.DefaultMessageServiceImpl;
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
    static {
        System.setProperty("jsse.enableSNIExtension", "false");
    }

    private static final Logger logger = LoggerFactory.getLogger(Robot.class);

    public static void main(String[] args) throws InterruptedException {
        ContactService contactService = new ContactServiceImpl();
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

        MessageService messageService = new DefaultMessageServiceImpl();

        DefaultServerStatusListener serverStatusListener = new DefaultServerStatusListener();
        RevokeMsgHandler revokeMsgHandler = new RevokeMsgHandler(messageSendListener,messageService,contactService);
        revokeMsgHandler.enable("demo");
        serverStatusListener.registerMessageHandler(WxConst.MessageType.REVOKE_MSG, revokeMsgHandler);
        serverStatusListener.registerMessageHandler(WxConst.MessageType.APP_MSG, new AppMsgHandler());

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
