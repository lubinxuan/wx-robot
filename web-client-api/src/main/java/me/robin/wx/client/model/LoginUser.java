package me.robin.wx.client.model;

import com.alibaba.fastjson.JSONObject;

/**
 * Created by xuanlubin on 2017/4/19.
 */
public class LoginUser {
    private String uuid;

    private String userAvatar;

    private String nickName;

    private String userName;

    private String skey;

    private String passTicket;

    private String uin;

    private String sid;

    private String webwxDataTicket;

    private String loginHost = "wx.qq.com";

    private JSONObject syncKey;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUserAvatar() {
        return userAvatar;
    }

    public void setUserAvatar(String userAvatar) {
        this.userAvatar = userAvatar;
    }

    public String getSkey() {
        return skey;
    }

    public void setSkey(String skey) {
        this.skey = skey;
    }

    public String getPassTicket() {
        return passTicket;
    }

    public void setPassTicket(String passTicket) {
        this.passTicket = passTicket;
    }

    public String getUin() {
        return uin;
    }

    public void setUin(String uin) {
        this.uin = uin;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getLoginHost() {
        return loginHost;
    }

    public void setLoginHost(String loginHost) {
        this.loginHost = loginHost;
    }

    public JSONObject getSyncKey() {
        return syncKey;
    }

    public void setSyncKey(JSONObject syncKey) {
        this.syncKey = syncKey;
    }

    public String getWebwxDataTicket() {
        return webwxDataTicket;
    }

    public void setWebwxDataTicket(String webwxDataTicket) {
        this.webwxDataTicket = webwxDataTicket;
    }
}
