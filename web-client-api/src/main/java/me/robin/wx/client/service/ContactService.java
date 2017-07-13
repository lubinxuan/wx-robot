package me.robin.wx.client.service;

import com.alibaba.fastjson.JSONArray;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxUser;

import java.util.List;

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

    void addWxUser(WxUser wxUser);

    void clearContact();

    List<WxGroup> listAllGroup();

    void updateGroupUserInfo(WxUser wxUser);

    void updateGroupUserInfo(WxGroup group,WxUser wxUser);

    boolean groupInitialized(String groupName);

}
