package me.robin.wx.client.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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

    void deleteContact(String userName);

    void updateContact(JSONObject contact);

    /**
     * 判断是否已经在通讯录里面
     *
     * @param userName 加密用户名
     * @return 状态
     */
    boolean contain(String userName);

    void clearContact();

    List<WxGroup> listAllGroup();

    void updateGroupUserInfo(WxUser wxUser);

    void updateGroupUserInfo(WxGroup group, WxUser wxUser);

    boolean groupInitialized(String groupName);

}
