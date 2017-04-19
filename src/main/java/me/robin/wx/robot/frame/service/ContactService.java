package me.robin.wx.robot.frame.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import me.robin.wx.robot.frame.model.WxUser;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public interface ContactService {
    void updateContact(JSONArray contactJson);

    WxUser queryUserByAlias(String alias);

    WxUser queryUserByRemark(String remark);

    WxUser queryUserByNickName(String nickName);

    WxUser queryUserByUserName(String userName);

    WxUser queryUser(String queryString);

}
