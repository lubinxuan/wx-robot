package me.robin.wx.client;

/**
 * Created by Administrator on 2017-06-02.
 */
public interface Conf {
    interface API {
        String webwxsendappmsg = "/cgi-bin/mmwebwx-bin/webwxsendappmsg";
        String webwxsendmsg = "/cgi-bin/mmwebwx-bin/webwxsendmsg";
        String webwxuploadmedia = "/cgi-bin/mmwebwx-bin/webwxuploadmedia?f=json";
        String webwxsendmsgimg = "/cgi-bin/mmwebwx-bin/webwxsendmsgimg";
    }
}
