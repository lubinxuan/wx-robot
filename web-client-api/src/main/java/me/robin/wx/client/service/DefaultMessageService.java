package me.robin.wx.client.service;

import me.robin.wx.client.model.WxMsg;
import me.robin.wx.client.service.MessageService;

import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public class DefaultMessageService implements MessageService {

    protected Queue<WxMsg> historyMessageList = new LinkedBlockingQueue<>();

    protected Map<String, WxMsg> messageIdUserMap = new ConcurrentHashMap<>();

    public DefaultMessageService() {
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
