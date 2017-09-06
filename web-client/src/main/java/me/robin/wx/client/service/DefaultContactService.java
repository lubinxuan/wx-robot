package me.robin.wx.client.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxUser;
import me.robin.wx.client.util.WxUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiFunction;

/**
 * Created by xuanlubin on 2017/4/19.
 */
@Slf4j
public class DefaultContactService implements ContactService {

    protected Map<String, WxUser> aliasMap = new ConcurrentHashMap<>();
    protected Map<String, WxUser> remarkMap = new ConcurrentHashMap<>();
    protected Map<String, WxUser> nickNameMap = new ConcurrentHashMap<>();
    protected Map<String, WxUser> userNameMap = new ConcurrentHashMap<>();
    protected Set<String> groupUserNames = new HashSet<>();


    @Override
    public void updateContact(JSONArray array) {
        for (int i = 0; i < array.size(); i++) {
            WxUser user = WxUtil.parse(array.getJSONObject(i));
            addWxUser(user);
        }
    }

    public void addWxUser(WxUser wxUser) {

        this.deleteContact(wxUser.getUserName());

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
    public void deleteContact(String userName) {
        WxUser wxUser = this.userNameMap.remove(userName);
        if (null != wxUser) {
            this.nickNameMap.remove(wxUser.getNickName());
            if (StringUtils.isNotBlank(wxUser.getAlias())) {
                aliasMap.remove(wxUser.getAlias());
            }
            if (StringUtils.isNotBlank(wxUser.getRemarkName())) {
                remarkMap.remove(wxUser.getRemarkName());
            }
        }
    }

    @Override
    public void updateContact(JSONObject contact) {

    }

    @Override
    public boolean contain(String userName) {
        return this.userNameMap.containsKey(userName);
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
    public WxUser queryGroupMember(WxGroup wxGroup, String memberUserName) {
        Optional<WxUser> member = wxGroup.getMemberList().stream().filter(u -> StringUtils.equals(u.getUserName(), memberUserName)).findAny();
        return member.orElse(null);
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
    public List<WxGroup> listAllGroup() {
        List<WxGroup> groupList = new ArrayList<>();
        this.userNameMap.values().stream().filter(u -> u instanceof WxGroup).forEach(u -> groupList.add((WxGroup) u));
        return groupList;
    }

    @Override
    public void updateGroupUserInfo(WxUser wxUser) {
        Optional<WxUser> group = this.userNameMap.values().stream().filter(u -> StringUtils.equals(u.getEncryChatRoomId(), wxUser.getEncryChatRoomId())).findAny();
        if (group.isPresent() && group.get() instanceof WxGroup) {
            this.updateGroupUserInfo((WxGroup) group.get(), wxUser);
        }
    }

    @Override
    public void updateGroupUserInfo(WxGroup group, WxUser wxUser) {
        for (int i = 0; i < group.getMemberList().size(); i++) {
            WxUser tmp = group.getMemberList().get(i);
            if (StringUtils.equals(tmp.getUserName(), wxUser.getUserName())) {
                group.getMemberList().set(i, wxUser);
                userNameMap.computeIfPresent(wxUser.getUserName(), (s, wxUser1) -> {
                    wxUser1.getGroupNames().add(group.getNickName());
                    return wxUser1;
                });
                return;
            }
        }
        group.getMemberList().add(wxUser);
    }

    @Override
    public boolean groupInitialized(String groupName) {
        return !groupUserNames.add(groupName);
    }

    @Override
    public void clearContact() {
        this.aliasMap.clear();
        this.remarkMap.clear();
        this.nickNameMap.clear();
        this.userNameMap.clear();
        this.groupUserNames.clear();
    }
}
