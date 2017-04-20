package me.robin.wx.robot.frame.model;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * Created by xuanlubin on 2017/4/20.
 */
public class RecommendInfo {
    @JSONField(name = "Alias")
    private String alias;
    @JSONField(name = "City")
    private String city;
    @JSONField(name = "NickName")
    private String nickName;
    @JSONField(name = "Province")
    private String province;
    @JSONField(name = "UserName")
    private String userName;
    @JSONField(name = "VerifyFlag")
    private int verifyFlag;
    @JSONField(name = "Signature")
    private String signature;
    @JSONField(name = "Content")
    private String content;

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getVerifyFlag() {
        return verifyFlag;
    }

    public void setVerifyFlag(int verifyFlag) {
        this.verifyFlag = verifyFlag;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
