package me.robin.wx.robot.frame.service.impl;

import com.alibaba.fastjson.JSONObject;
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

    private Queue<JSONObject> historyMessageList = new LinkedBlockingQueue<>();

    private Map<String, JSONObject> messageIdUserMap = new ConcurrentHashMap<>();

    public DefaultMessageServiceImpl() {
        new Timer("HistoryMessageClear-Task").schedule(new TimerTask() {
            @Override
            public void run() {
                long expire = System.currentTimeMillis() / 1000 - 3 * 60;
                while (true) {
                    JSONObject message = historyMessageList.peek();
                    if (null == message) {
                        return;
                    }
                    long createTime = message.getLongValue("CreateTime");
                    if (createTime < expire) {
                        historyMessageList.poll();
                        String fromUser = message.getString("FromUserName");
                        String msgId = message.getString("MsgId");
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
    public void saveMessage(JSONObject message) {
        String fromUser = message.getString("FromUserName");
        String msgId = message.getString("MsgId");
        String id = fromUser + "$$" + msgId;
        messageIdUserMap.computeIfAbsent(id, s -> {
            historyMessageList.offer(message);
            return message;
        });
    }

    @Override
    public JSONObject findMessageByUserAndMid(String fromUser, String msgId) {
        return messageIdUserMap.get(fromUser + "$$" + msgId);
    }
}
