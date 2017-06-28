package me.robin.wx.client;

/**
 * Created by Lubin.Xuan on 2017-06-28.
 */
public abstract class ExpireListener {
    private long lastLoginRequest;
    /**
     * 超过多久未登录失效Client
     */
    private long expire;

    public abstract boolean expire(BaseServer server);

    public long getExpire() {
        return expire;
    }

    long getLastLoginRequest() {
        return lastLoginRequest;
    }

    void setLastLoginRequest(long lastLoginRequest) {
        this.lastLoginRequest = lastLoginRequest;
    }
}
