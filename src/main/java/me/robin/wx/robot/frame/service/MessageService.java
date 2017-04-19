package me.robin.wx.robot.frame.service;

import com.alibaba.fastjson.JSONObject;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface MessageService {
    void saveMessage(JSONObject message);

    JSONObject findMessageByUserAndMid(String userName, String mid);
}
