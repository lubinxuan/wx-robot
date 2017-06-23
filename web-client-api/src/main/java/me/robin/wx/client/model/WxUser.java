package me.robin.wx.client.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * Created by xuanlubin on 2017/4/18.
 */
@Data
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
    @JSONField(name = "EncryChatRoomId")
    private String encryChatRoomId;
}
