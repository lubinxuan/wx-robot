package me.robin.wx.client.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * Created by xuanlubin on 2017/4/18.
 */
@Data
public class WxGroup extends WxUser{
    @JSONField(name = "MemberCount")
    private int memberCount;
    @JSONField(name = "MemberList")
    private List<WxUser> memberList;
}
