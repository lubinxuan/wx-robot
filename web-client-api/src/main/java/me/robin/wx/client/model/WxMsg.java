package me.robin.wx.client.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by xuanlubin on 2017/4/20.
 */
@Data
public class WxMsg {
    @JSONField(name = "MsgId")
    private String msgID;
    @JSONField(name = "FromUserName")
    private String fromUserName;
    @JSONField(name = "Content")
    private String content;
    @JSONField(name = "ToUserName")
    private String toUserName;
    @JSONField(name = "CreateTime")
    private long createTime;
    @JSONField(name = "FileName")
    private String fileName;
    @JSONField(name = "FileSize")
    private String fileSize;
    @JSONField(name = "MediaId")
    private String mediaId;
    @JSONField(name = "MsgType")
    private int msgType;
    @JSONField(name = "Url")
    private String url;
    @JSONField(name = "SubMsgType")
    private int subMsgType;
    @JSONField(name = "AppMsgType")
    private int appMsgType;
    @JSONField(name = "RecommendInfo")
    private RecommendInfo recommendInfo;



    public boolean isGroupMsg() {
        return StringUtils.startsWith(fromUserName, "@@");
    }

    public String getSendUserName() {
        return StringUtils.substringBefore(content, ":");
    }

    public String getSendContent() {
        return StringUtils.substringAfter(content, ":");
    }
}
