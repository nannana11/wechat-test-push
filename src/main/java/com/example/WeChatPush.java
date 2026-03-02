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
    // 【最终稳定天气接口】直接返回纯文本天气，不用任何解析，海外100%可访问
    private static final String WEATHER_URL = "https://wttr.in/Nanjing?lang=zh-cn&format=%l：%c+%t+%w+湿度：%h\n";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        System.out.println("=== 微信推送程序启动 ===");

        // 读取微信核心配置
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");

        // 微信配置判空，缺少直接终止
        if (appId == null || appId.trim().isEmpty()
                || appSecret == null || appSecret.trim().isEmpty()
                || openId == null || openId.trim().isEmpty()) {
            System.err.println("错误：微信核心配置缺失，程序终止");
            System.exit(1);
        }

        try {
            // 1. 获取微信access_token
            String accessToken = getAccessToken(appId, appSecret);
            System.out.println("✅ 获取access_token成功");

            // 2. 获取南京天气，极简逻辑，零解析报错
            String weatherInfo = "⚠️  暂时无法获取天气信息\n";
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet get = new HttpGet(WEATHER_URL);
                get.setHeader("User-Agent", "curl/7.68.0"); // 模拟curl请求，100%兼容
                try (CloseableHttpResponse response = client.execute(get)) {
                    // 直接拿到纯文本天气，不用任何JSON解析
                    weatherInfo = EntityUtils.toString(response.getEntity(), "UTF-8").trim();
                    System.out.println("✅ 天气获取成功：" + weatherInfo);
                }
            } catch (Exception e) {
                System.err.println("⚠️  天气获取失败：" + e.getMessage());
            }

            // 3. 拼接最终推送文案
            String pushContent = "☀️ 早安！\n" +
                    "【南京今日天气】\n" +
                    weatherInfo + "\n\n" +
                    "新的一天也要开心呀！";

            // 4. 发送微信消息
            String result = sendMsg(accessToken, openId, pushContent);
            System.out.println("✅ 微信接口响应：" + result);
            System.out.println("=== 推送执行完成 ===");

        } catch (Exception e) {
            System.err.println("❌ 程序执行失败：");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 获取微信access_token
     */
    private static String getAccessToken(String appId, String appSecret) throws Exception {
        String url = TOKEN_URL + "&appid=" + appId + "&secret=" + appSecret;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                if (json.has("errcode") && json.get("errcode").getAsInt() != 0) {
                    throw new RuntimeException("获取微信token失败：" + body);
                }
                return json.get("access_token").getAsString();
            }
        }
    }

    /**
     * 发送微信客服消息
     */
    private static String sendMsg(String accessToken, String openId, String content) throws Exception {
        String url = SEND_MSG_URL + accessToken;
        JsonObject msg = new JsonObject();
        msg.addProperty("touser", openId);
        msg.addProperty("msgtype", "text");
        JsonObject text = new JsonObject();
        text.addProperty("content", content);
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
