package me.shumei.open.oks.xiaomibbs;

import java.io.IOException;
import java.util.HashMap;

import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
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
            String signPageUrl = "http://bbs.xiaomi.cn/plugin.php?id=mi_sign:sign";
            //提交签到信息的URL
            String signSubmitUrl = "http://bbs.xiaomi.cn/plugin.php?id=mi_sign:sign&operation=qiandao&infloat=0&inajax=0";
            
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
            
            //要发的表情，共9个
            //开心，难过，郁闷，无聊，怒，擦汗，奋斗，慵懒，衰
            String[] qdxqArr = {"kx","ng","ym","wl","nu","ch","fd","yl","shuai"};
            
            //要说的话，共7个
            //{"我爱小米社区，社区是我家！","因为米粉，所以小米！","米粉给力！小米加油！","米粉们，我来啦！","好好签到，天天向上！","哎...今天够累的，签到来啦！","好好学习，天天向上！","我是小米发烧友，我爱小米！"};
            String[] fastreplyArr = {"1","2","3","4","5","6","7","8"};
            
            int randNum;
            randNum = (int)(Math.random() * 8);
            String qdxq = qdxqArr[randNum];
            randNum = (int)(Math.random() * 7);
            String fastreply = fastreplyArr[randNum];
            
            //访问电脑版签到页面
            res = Jsoup.connect(signPageUrl).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
            cookies.putAll(res.cookies());
            
            //分析签到页面的信息，判断今天是否已经签到
            if(res.parse().toString().contains("今天已经签到过了或者签到时间还未开始"))
            {
                resultFlag = "true";
                //捕捉一下异常，防止获取数据时发生错误
                try {
                    Elements paragraphs = res.parse().select("#ct .mn > p");
                    resultStr = "今天已签过到\n";
                    resultStr += paragraphs.eq(0).text() + "\n";
                    resultStr += paragraphs.eq(1).text() + "\n";
                    resultStr += paragraphs.eq(2).text() + "\n";
                    resultStr += paragraphs.eq(3).text() + "\n";
                    resultStr += paragraphs.eq(4).text() + "\n";
                } catch (Exception e) {
                    resultStr = "今天已签到";
                }
            }
            else
            {
                //向电脑版网页提交签到信息
                String formhash = res.parse().getElementsByAttributeValue("name", "formhash").first().val();
                res = Jsoup.connect(signSubmitUrl).data("qdmode","2").data("formhash",formhash).data("qdxq",qdxq).data("fastreply",fastreply).data("todaysay","").cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
                
                this.resultFlag = "true";
                try {
                    this.resultStr = res.parse().select("#ct .c").text().replace("[点此返回]", "");
                } catch (Exception e) {
                    this.resultStr = "签到成功";
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
