package me.robin.wx.client.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * Created by xuanlubin on 2017/4/20.
 */
@Data
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

}
