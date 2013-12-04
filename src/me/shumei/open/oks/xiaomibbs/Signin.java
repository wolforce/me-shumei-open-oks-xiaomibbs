package me.shumei.open.oks.xiaomibbs;

import java.io.IOException;
import java.util.HashMap;

import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
    String resultFlag = "false";
    String resultStr = "未知错误！";
    
    /**
     * <p><b>程序的签到入口</b></p>
     * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
     * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
     * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
     * @param cfg “配置”栏内输入的数据
     * @param user 用户名
     * @param pwd 解密后的明文密码
     * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
     */
    public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
        //把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
        CaptchaUtil.context = ctx;
        //标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
        CaptchaUtil.isAutoSign = isAutoSign;
        
        try{
            //存放Cookies的HashMap
            HashMap<String, String> cookies = new HashMap<String, String>();
            //Jsoup的Response
            Response res;
            
            //登录通行证的URL
            String loginPassportUrl = "https://account.xiaomi.com/pass/serviceLoginAuth";
            //登录BBS的URL
            String loginBBSUrl = "http://bbs.xiaomi.cn/member.php?mod=logging&action=login&mobile=yes";
            //签到页面的URL
            String signPageUrl = "http://bbs.xiaomi.cn/qiandao/";
            //获取签到口号的URL
            String logoUrl = "http://oss.xiaomi.com/cmsApi/logo/GetLogo";
            //提交签到信息的URL
            String signSubmitUrl = "http://bbs.xiaomi.cn/qiandao/index/share";
            
            //登录小米通行证账号
            res = Jsoup.connect(loginPassportUrl).data("user", user).data("pwd", pwd).data("sid", "passport").userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
            cookies.putAll(res.cookies());
            
            //如果页面中有“忘记密码”字符串，说明登录失败
            if(res.body().contains("忘记密码"))
            {
                this.resultFlag = "false";
                this.resultStr = "登录失败，请检查账号密码是否正确";
                return new String[]{this.resultFlag,this.resultStr};
            }
            
            //带着通行证的Cookies访问论坛地址，实现登录论坛
            res = Jsoup.connect(loginBBSUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
            cookies.putAll(res.cookies());
            
            //访问电脑版签到页面
            res = Jsoup.connect(signPageUrl).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
            cookies.putAll(res.cookies());
            
            //分析签到页面的信息，判断今天是否已经签到
            if(res.parse().toString().contains("您今天还没有签到"))
            {
                //获取签到口号
                //var logo_url = "";var logo_text = "投资未来的人是忠于现实的人。";var logo_text_count=33124;
                res = Jsoup.connect(logoUrl).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
                cookies.putAll(res.cookies());
                String logoStr = res.body().replaceAll(".+var logo_text.*=.*\"(.+)\".*", "$1");
                
                //向电脑版网页提交签到信息
                res = Jsoup.connect(signSubmitUrl).data("text", logoStr).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
                JSONObject jsonObj = new JSONObject(res.body());
                String code = jsonObj.getString("code");
                if (code.equals("200")) {
                    JSONObject userObj = jsonObj.getJSONObject("user");
                    this.resultFlag = "true";
                    StringBuilder sb = new StringBuilder();
                    sb.append(userObj.getString("name") + ",签到成功\n");
                    sb.append("连续签到：" + userObj.getString("days") + "\n");
                    sb.append("签到等级：" + userObj.getString("level") + "\n");
                    sb.append("获得奖励：" + userObj.getString("lastreward") + "\n");
                    sb.append("积分奖励：" + userObj.getString("reward") + "\n");
                    sb.append("签到排名：" + userObj.getString("sort") + "\n");
                    sb.append("总天数：" + userObj.getString("tdays") + "\n");
                    
                    //中奖
                    if (jsonObj.getString("data").length() > 0) {
                        sb.append("签到中奖！中奖信息：\n" );
                        sb.append(jsonObj.getString("data"));
                    }
                    
                    this.resultStr = sb.toString();
                } else {
                    this.resultFlag = "false";
                    this.resultStr = "登录成功，在提交签到信息时发生未知错误";
                }
            }
            else
            {
                resultFlag = "true";
                //捕捉一下异常，防止获取数据时发生错误
                try {
                    Document doc = res.parse();
                    StringBuilder sb = new StringBuilder();
                    sb.append("今天已签过到\n");
                    sb.append("连续签到：" + doc.getElementById("lxdays").val() + "\n");
                    sb.append("签到等级：" + doc.getElementById("lxlevel").val() + "\n");
                    sb.append("积分奖励：" + doc.getElementById("lxreward").val() + "\n");
                    sb.append("签到排名：" + doc.getElementById("qiandaobtnnum").val() + "\n");
                    sb.append("总天数：" + doc.getElementById("lxtdays").val() + "\n");
                    resultStr = sb.toString();
                } catch (Exception e) {
                    resultStr = "今天已签到";
                }
            }
            
        } catch (IOException e) {
            this.resultFlag = "false";
            this.resultStr = "连接超时";
            e.printStackTrace();
        } catch (Exception e) {
            this.resultFlag = "false";
            this.resultStr = "未知错误！";
            e.printStackTrace();
        }
        
        return new String[]{resultFlag, resultStr};
    }
    
    
}
