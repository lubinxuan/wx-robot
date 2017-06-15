package me.robin.wx.client.model;

import com.alibaba.fastjson.annotation.JSONField;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by xuanlubin on 2017/4/20.
 */
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

    public String getMsgID() {
        return msgID;
    }

    public void setMsgID(String msgID) {
        this.msgID = msgID;
    }

    public String getFromUserName() {
        return fromUserName;
    }

    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToUserName() {
        return toUserName;
    }

    public void setToUserName(String toUserName) {
        this.toUserName = toUserName;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public int getMsgType() {
        return msgType;
    }

    public void setMsgType(int msgType) {
        this.msgType = msgType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getSubMsgType() {
        return subMsgType;
    }

    public void setSubMsgType(int subMsgType) {
        this.subMsgType = subMsgType;
    }

    public int getAppMsgType() {
        return appMsgType;
    }

    public void setAppMsgType(int appMsgType) {
        this.appMsgType = appMsgType;
    }

    public RecommendInfo getRecommendInfo() {
        return recommendInfo;
    }

    public void setRecommendInfo(RecommendInfo recommendInfo) {
        this.recommendInfo = recommendInfo;
    }

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
