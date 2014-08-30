package me.shumei.open.oks.xiaomibbs;

import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            
            //登录通行证的页面URL
            String loginPageUrl = "https://account.xiaomi.com/pass/serviceLogin";
            //登录通行证的URL
            String loginPassportUrl = "https://account.xiaomi.com/pass/serviceLoginAuth2";
            //登录BBS的URL
            String loginBBSUrl = "http://bbs.xiaomi.cn/member.php?mod=logging&action=login&mobile=yes";
            //签到页面的URL
            String signPageUrl = "http://bbs.xiaomi.cn/qiandao/";
            //获取签到口号的URL
            String logoUrl = "http://oss.xiaomi.com/cmsApi/logo/GetLogo";
            //提交签到信息的URL
            String signSubmitUrl = "http://bbs.xiaomi.cn/qiandao/index/share";
            
            //访问登录页面，获取必要的登录信息
            res = Jsoup.connect(loginPageUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
            cookies.putAll(res.cookies());
            
            String oriStr = res.body();
            String callback = "https://account.xiaomi.com";
            String sid = getStrWithRegx("sid *: *\"(.+)\"", oriStr);
            String qs = getStrWithRegx("qs *: *\"(.+)\"", oriStr);
            String hidden = "";
            String _sign = getStrWithRegx("_sign\" *: *\"(.+)\"", oriStr);
            String serviceParam = "{\"checkSafePhone\":false}";
            
            HashMap<String, String> postDatas = new HashMap<String, String>();
            postDatas.put("user", user);
            postDatas.put("_json", "true");
            postDatas.put("pwd", pwd);
            postDatas.put("callback", callback);
            postDatas.put("sid", sid);
            postDatas.put("qs", qs);
            postDatas.put("hidden", hidden);
            postDatas.put("_sign", _sign);
            postDatas.put("serviceParam", serviceParam);
            
            //登录小米通行证账号
            //&&&START&&&{"desc":"签名值不合法","location":null,"captchaUrl":null,"code":21310}
            //&&&START&&&{"sid":"passport","desc":"登录验证失败","location":null,"captchaUrl":null,"callback":"https://account.xiaomi.com","code":70016,"qs":"%3Fsid%3Dpassport","_sign":"44rewwer45+ewre45reXg="}
            //&&&START&&&{"passToken":"Aq3FBlXT8808jz9YNmskY6J+fZBo=","securityStatus":0,"ssecurity":"3DjO/S4Qi9dP9123456nQ==","desc":"成功","nonce":51456452015364,"location":"https://account.xiaomi.com/sts?sid=qwertdx12345646","userId":13465120,"captchaUrl":null,"psecurity":"96Py/54q6we5r12sda==","code":0,"qs":"%3Fsid%3Dpassport","notificationUrl":""}
            res = Jsoup.connect(loginPassportUrl).data(postDatas).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
            cookies.putAll(res.cookies());
            System.out.println(res.body());
            
            JSONObject jsonObj = new JSONObject(res.body().replace("&&&START&&&", ""));
            int code_no = jsonObj.optInt("code");
            String desc = jsonObj.optString("desc");
            if (code_no != 0) return new String[]{"false", desc};
            
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
                jsonObj = new JSONObject(res.body());
                String code = jsonObj.optString("code");
                if (code.equals("200")) {
                    JSONObject userObj = jsonObj.getJSONObject("user");
                    this.resultFlag = "true";
                    StringBuilder sb = new StringBuilder();
                    sb.append(userObj.optString("name") + ",签到成功\n");
                    sb.append("连续签到：" + userObj.optString("days") + "\n");
                    sb.append("签到等级：" + userObj.optString("level") + "\n");
                    sb.append("获得奖励：" + userObj.optString("lastreward") + "\n");
                    sb.append("积分奖励：" + userObj.optString("reward") + "\n");
                    sb.append("签到排名：" + userObj.optString("sort") + "\n");
                    sb.append("总天数：" + userObj.optString("tdays") + "\n");
                    
                    //中奖
                    if (jsonObj.optString("data").length() > 0) {
                        sb.append("签到中奖！中奖信息：\n" );
                        sb.append(jsonObj.optString("data"));
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
    
    
    /**
     * 根据传入的正则规则和字符串查找第一个符合条件的值
     * @param pattStr 正则表达式规则
     * @param oriStr 原始文本
     * @return 返回第一个符合条件的值，如果没有符合条件的值，就会回一个长度为0的字符串
     */
    private String getStrWithRegx(String pattStr, String oriStr) {
        Pattern pattern = Pattern.compile(pattStr);
        Matcher matcher = pattern.matcher(oriStr);
        String str = "";
        if (matcher.find()) str = matcher.group(1);
        return str;
    }
    
    
}
