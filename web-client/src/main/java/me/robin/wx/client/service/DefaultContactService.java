package me.robin.wx.client.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxUser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public class DefaultContactService implements ContactService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultContactService.class);

    protected Map<String, WxUser> aliasMap = new ConcurrentHashMap<>();
    protected Map<String, WxUser> remarkMap = new ConcurrentHashMap<>();
    protected Map<String, WxUser> nickNameMap = new ConcurrentHashMap<>();
    protected Map<String, WxUser> userNameMap = new ConcurrentHashMap<>();

    @Override
    public void updateContact(JSONArray array) {
        for (int i = 0; i < array.size(); i++) {
            JSONObject account = array.getJSONObject(i);
            String userName = account.getString("UserName");
            if (StringUtils.startsWith(userName, "@@")) {
                WxGroup wxGroup = account.toJavaObject(WxGroup.class);
                addWxUser(wxGroup);
            } else if (StringUtils.startsWith(userName, "@")) {
                WxUser wxUser = account.toJavaObject(WxUser.class);
                addWxUser(wxUser);
            } else {
                logger.info("不支持的用户类型: {} {} {} {}", userName, account.getString("Alias"), account.getString("NickName"), account.getString("Signature"));
            }
        }
    }

    private void addWxUser(WxUser wxUser) {
        if (StringUtils.isNotBlank(wxUser.getAlias())) {
            aliasMap.put(wxUser.getAlias(), wxUser);
        }
        if (StringUtils.isNotBlank(wxUser.getRemarkName())) {
            remarkMap.put(wxUser.getRemarkName(), wxUser);
        }
        nickNameMap.put(wxUser.getNickName(), wxUser);
        userNameMap.put(wxUser.getUserName(), wxUser);
    }

    @Override
    public WxUser queryUserByAlias(String alias) {
        return aliasMap.get(alias);
    }

    @Override
    public WxUser queryUserByRemark(String remark) {
        return remarkMap.get(remark);
    }

    @Override
    public WxUser queryUserByNickName(String nickName) {
        return nickNameMap.get(nickName);
    }

    @Override
    public WxUser queryUserByUserName(String userName) {
        return userNameMap.get(userName);
    }

    @Override
    public WxUser queryUser(String queryString) {
        //根据备注名查找用户
        WxUser wxUser = queryUserByRemark(queryString);
        if (null == wxUser) {
            //根据昵称找用户
            wxUser = queryUserByNickName(queryString);
        }
        if (null == wxUser) {
            //根据登录名查找用户
            wxUser = queryUserByAlias(queryString);
        }
        if (null == wxUser) {
            //根据加密名称查找用户
            wxUser = queryUserByUserName(queryString);
        }
        return wxUser;
    }

    @Override
    public void clearContact() {
        this.aliasMap.clear();
        this.remarkMap.clear();
        this.nickNameMap.clear();
        this.userNameMap.clear();
    }
}