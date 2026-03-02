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
    // OpenWeather API：南京的城市ID是1806260，units=metric表示摄氏度，lang=zh_cn表示中文
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?id=1806260&units=metric&lang=zh_cn&appid=";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        System.out.println("=== 微信推送程序启动 ===");

        // 读取环境变量
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");
        String weatherKey = System.getenv("OPENWEATHER_API_KEY");

        // 微信核心配置判空
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

            // 2. 获取南京天气，OpenWeather海外环境极稳
            String weatherInfo = "⚠️  暂时无法获取天气信息\n";
            if (weatherKey != null && !weatherKey.trim().isEmpty()) {
                try {
                    weatherInfo = getNanjingWeather(weatherKey);
                    System.out.println("✅ 天气获取成功：" + weatherInfo.replace("\n", " "));
                } catch (Exception e) {
                    System.err.println("⚠️  天气获取失败：" + e.getMessage());
                    e.printStackTrace();
                }
            }

            // 3. 拼接推送文案
            String pushContent = "☀️ 早安！\n" +
                    "【南京今日天气】\n" +
                    weatherInfo + "\n" +
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
     * 获取南京天气，OpenWeather专属解析逻辑
     */
    private static String getNanjingWeather(String weatherKey) throws Exception {
        String url = WEATHER_URL + weatherKey;
        System.out.println("🌤️  正在请求OpenWeather API...");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            get.setHeader("User-Agent", "Mozilla/5.0");

            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    String errorBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                    throw new RuntimeException("API状态码异常：" + statusCode + "，返回：" + errorBody);
                }

                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("🌤️  API返回预览：" + body.substring(0, Math.min(300, body.length())) + "...");

                // 解析OpenWeather返回的JSON
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                JsonObject main = json.getAsJsonObject("main");
                JsonObject weather = json.getAsJsonArray("weather").get(0).getAsJsonObject();
                JsonObject wind = json.getAsJsonObject("wind");
                JsonObject sys = json.getAsJsonObject("sys");

                // 提取天气信息
                String weatherDesc = weather.get("description").getAsString(); // 天气描述（中文）
                String temp = main.get("temp").getAsString(); // 当前温度
                String feelsLike = main.get("feels_like").getAsString(); // 体感温度
                String humidity = main.get("humidity").getAsString(); // 湿度
                String windSpeed = wind.get("speed").getAsString(); // 风速
                String pressure = main.get("pressure").getAsString(); // 气压

                // 拼接成易读的文案
                return "天气：" + weatherDesc + "\n" +
                        "温度：" + temp + "℃（体感" + feelsLike + "℃）\n" +
                        "湿度：" + humidity + "%\n" +
                        "风速：" + windSpeed + "m/s\n" +
                        "气压：" + pressure + "hPa";
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
                    throw new RuntimeException("获取微信token失败：" + body);
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
