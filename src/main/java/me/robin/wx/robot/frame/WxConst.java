package me.robin.wx.robot.frame;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public interface WxConst {
    String QR_CODE_API = "https://login.weixin.qq.com/jslogin";
    String LOGIN_CHECK_API = "https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login";
    String INIT_URL = "/cgi-bin/mmwebwx-bin/webwxinit";
    //https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxstatusnotify?lang=zh_CN&pass_ticket=Me48r4e05o5ztyB3CG3wGR9ZQgfDTISx1VnOceJnuvWmUz5IdisOjtPITI7hji04
    String STATUS_NOTIFY = "/cgi-bin/mmwebwx-bin/webwxstatusnotify";

    /**
     * Created by xuanlubin on 2017/4/20.
     */
    enum MessageTarget {
        GROUP, SINGLE
    }

    interface MessageType {
        int TEXT = 1;
        int IMG = 3;
        int VOICE = 34;
        int CONTACT = 42;
        int VIDEO = 43;
        int APP_MSG = 49;
        int SYS_MSG = 10000;
    }

    interface SpecialMsg{
        String BRIBERY_MONEY = "收到红包，请在手机上查看";
    }
}
