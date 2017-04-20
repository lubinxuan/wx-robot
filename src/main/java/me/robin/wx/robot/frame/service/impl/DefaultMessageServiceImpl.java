package me.robin.wx.robot.frame.service.impl;

import me.robin.wx.robot.frame.model.WxMsg;
import me.robin.wx.robot.frame.service.MessageService;

import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public class DefaultMessageServiceImpl implements MessageService {

    private Queue<WxMsg> historyMessageList = new LinkedBlockingQueue<>();

    private Map<String, WxMsg> messageIdUserMap = new ConcurrentHashMap<>();

    public DefaultMessageServiceImpl() {
        new Timer("HistoryMessageClear-Task").schedule(new TimerTask() {
            @Override
            public void run() {
                long expire = System.currentTimeMillis() / 1000 - 3 * 60;
                while (true) {
                    WxMsg message = historyMessageList.peek();
                    if (null == message) {
                        return;
                    }
                    long createTime = message.getCreateTime();
                    if (createTime < expire) {
                        historyMessageList.poll();
                        String fromUser = message.getFromUserName();
                        String msgId = message.getMsgID();
                        String id = fromUser + "$$" + msgId;
                        messageIdUserMap.remove(id);
                    } else {
                        break;
                    }
                }
            }
        }, 20000, 20000);
    }

    @Override
    public void saveMessage(WxMsg message) {
        String fromUser = message.getFromUserName();
        String msgId = message.getMsgID();
        String id = fromUser + "$$" + msgId;
        messageIdUserMap.computeIfAbsent(id, s -> {
            historyMessageList.offer(message);
            return message;
        });
    }

    @Override
    public WxMsg findMessageByUserAndMid(String fromUser, String msgId) {
        return messageIdUserMap.get(fromUser + "$$" + msgId);
    }
}
