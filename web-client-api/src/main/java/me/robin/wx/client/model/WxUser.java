package me.robin.wx.client.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by xuanlubin on 2017/4/18.
 */
@Getter
@Setter
public class WxUser {
    @JSONField(name = "UserName")
    private String userName;
    @JSONField(name = "NickName")
    private String nickName;
    @JSONField(name = "HeadImgUrl")
    private String headImgUrl;
    @JSONField(name = "ContactFlag")
    private int contactFlag;
    @JSONField(name = "RemarkName")
    private String remarkName;
    @JSONField(name = "Sex")
    private int sex;
    @JSONField(name = "Signature")
    private String signature;
    @JSONField(name = "VerifyFlag")
    private int verifyFlag;
    @JSONField(name = "StarFriend")
    private int starFriend;
    @JSONField(name = "AttrStatus")
    private int attrStatus;
    @JSONField(name = "Province")
    private String province;
    @JSONField(name = "City")
    private String city;
    @JSONField(name = "Alias")
    private String alias;
    @JSONField(name = "SnsFlag")
    private int snsFlag;
    @JSONField(name = "DisplayName")
    private String displayName;

    //群组特有字段
    @JSONField(name = "EncryChatRoomId")
    private String encryChatRoomId;
    @JSONField(name = "MemberCount")
    private int memberCount;
    @JSONField(name = "MemberList")
    private List<WxUser> memberList;

    //微信用户所在群组列表
    @JSONField(deserialize = false, serialize = false)
    private Set<String> groupNames = new HashSet<>();

    private boolean follow = true;

    @JSONField(deserialize = false, serialize = false)
    public boolean isGroup() {
        return StringUtils.startsWith(this.userName, "@@");
    }

    @JSONField(deserialize = false, serialize = false)
    public boolean isGZH() {
        switch (this.verifyFlag) {
            case 8:
            case 24:
            case 29:
                return true;
            default:
                return false;
        }
    }

    @JSONField(deserialize = false, serialize = false)
    public boolean isSpecial() {
        return StringUtils.contains("newsapp,fmessage,filehelper,weibo,qqmail,fmessage,tmessage,qmessage,qqsync,floatbottle,lbsapp,shakeapp,medianote,qqfriend,readerapp,blogapp,facebookapp,masssendapp,meishiapp,feedsapp,voip,blogappweixin,weixin,brandsessionholder,weixinreminder,wxid_novlwrv3lqwv11,gh_22b87fa7cb3c,officialaccounts,notification_messages,wxid_novlwrv3lqwv11,gh_22b87fa7cb3c,wxitil,userexperience_alarm,notification_messages", this.userName);
    }

    public String getAccountType() {
        if (isGroup()) {
            return "群组";
        } else if (isGZH()) {
            return "公众号";
        } else if (isSpecial()) {
            return "微信公共账号";
        } else {
            return "好友";
        }
    }
}
