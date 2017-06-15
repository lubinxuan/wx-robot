package me.robin.wx.client.model;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.List;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class WxGroup extends WxUser{
    @JSONField(name = "MemberCount")
    private int memberCount;
    @JSONField(name = "MemberList")
    private List<WxUser> memberList;

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public List<WxUser> getMemberList() {
        return memberList;
    }

    public void setMemberList(List<WxUser> memberList) {
        this.memberList = memberList;
    }
}
