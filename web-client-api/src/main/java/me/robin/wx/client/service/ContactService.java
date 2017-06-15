package me.robin.wx.client.service;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.client.model.WxUser;

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

    void clearContact();

}
