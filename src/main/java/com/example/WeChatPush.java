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
import com.google.gson.JsonArray;

public class WeChatPush {
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";
    private static final String SEND_MSG_URL = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=";
    // 和风天气免费API：南京的城市ID是101190101，也可以用"nanjing"
    private static final String WEATHER_URL = "https://devapi.qweather.com/v7/weather/now?location=101190101&key=";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        System.out.println("=== 微信推送程序启动 ===");
        // 从环境变量获取配置
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");
        String weatherKey = System.getenv("WEATHER_API_KEY");

        // Java 8兼容的判空逻辑
        if (appId == null || appId.trim().isEmpty()) {
            System.err.println("错误：WECHAT_APPID 未配置");
            System.exit(1);
        }
        if (appSecret == null || appSecret.trim().isEmpty()) {
            System.err.println("错误：WECHAT_APPSECRET 未配置");
            System.exit(1);
        }
        if (openId == null || openId.trim().isEmpty()) {
            System.err.println("错误：WECHAT_OPENID 未配置");
            System.exit(1);
        }
        if (weatherKey == null || weatherKey.trim().isEmpty()) {
            System.err.println("错误：WEATHER_API_KEY 未配置");
            System.exit(1);
        }

        try {
            // 1. 获取access_token
            String accessToken = getAccessToken(appId, appSecret);
            System.out.println("✅ 获取access_token成功");

            // 2. 获取南京天气
            String weatherInfo = getNanjingWeather(weatherKey);
            System.out.println("✅ 获取天气成功：" + weatherInfo);

            // 3. 拼接推送文案
            String pushContent = "☀️ 早安！\n" +
                                 "【南京今日天气】\n" +
                                 weatherInfo + "\n" +
                                 "新的一天也要开心呀！";
            
            // 4. 发送消息
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
     * 【新增方法】获取南京实时天气
     * 原理：用HTTP GET请求调用和风天气API，解析返回的JSON
     */
    private static String getNanjingWeather(String weatherKey) throws Exception {
        String url = WEATHER_URL + weatherKey;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                
                // 检查天气API是否调用成功
                if (!json.get("code").getAsString().equals("200")) {
                    throw new RuntimeException("获取天气失败：" + body);
                }

                // 解析和风天气返回的JSON，提取需要的信息
                JsonObject now = json.getAsJsonObject("now");
                String temp = now.get("temp").getAsString(); // 温度
                String text = now.get("text").getAsString(); // 天气状况（晴、多云等）
                String windDir = now.get("windDir").getAsString(); // 风向
                String humidity = now.get("humidity").getAsString(); // 湿度

                // 拼接成易读的天气文案
                return "天气：" + text + "\n" +
                       "温度：" + temp + "℃\n" +
                       "风向：" + windDir + "\n" +
                       "湿度：" + humidity + "%";
            }
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
