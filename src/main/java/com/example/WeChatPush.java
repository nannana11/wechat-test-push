package com.example;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class WeChatPush {
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";
    private static final String SEND_MSG_URL = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        System.out.println("=== 微信推送程序启动 ===");
        // 从环境变量获取配置
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");

        // 校验配置
        if (appId == null || appId.isBlank()) {
            System.err.println("错误：WECHAT_APPID 未配置");
            System.exit(1);
        }
        if (appSecret == null || appSecret.isBlank()) {
            System.err.println("错误：WECHAT_APPSECRET 未配置");
            System.exit(1);
        }
        if (openId == null || openId.isBlank()) {
            System.err.println("错误：WECHAT_OPENID 未配置");
            System.exit(1);
        }

        try {
            // 1. 获取access_token
            String accessToken = getAccessToken(appId, appSecret);
            System.out.println("✅ 获取access_token成功");

            // 2. 发送消息
            String result = sendMsg(accessToken, openId, "每日推送测试成功！");
            System.out.println("✅ 微信接口响应：" + result);
            System.out.println("=== 推送执行完成 ===");

        } catch (Exception e) {
            System.err.println("❌ 程序执行失败：");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getAccessToken(String appId, String appSecret) throws Exception {
        String url = TOKEN_URL + "&appid=" + appId + "&secret=" + appSecret;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                if (json.has("errcode") && json.get("errcode").getAsInt() != 0) {
                    throw new RuntimeException("获取token失败：" + body);
                }
                return json.get("access_token").getAsString();
            }
        }
    }

    private static String sendMsg(String accessToken, String openId, String content) throws Exception {
        String url = SEND_MSG_URL + accessToken;
        // 微信官方要求的标准格式
        JsonObject msg = new JsonObject();
        msg.addProperty("touser", openId);
        msg.addProperty("msgtype", "text");
        JsonObject text = new JsonObject();
        text.addProperty("content", "【每日推送】" + content);
        msg.add("text", text);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json;charset=UTF-8");
            post.setEntity(new StringEntity(GSON.toJson(msg), "UTF-8"));
            try (CloseableHttpResponse response = client.execute(post)) {
                return EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        }
    }
}
