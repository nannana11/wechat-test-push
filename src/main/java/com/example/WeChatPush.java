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
import com.google.gson.JsonParser;

public class WeChatPush {
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";
    private static final String SEND_MSG_URL = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=";
    // 【新】免费天气接口：wttr.in，不需要API Key，直接用
    private static final String WEATHER_URL = "https://wttr.in/Nanjing?format=j1";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        System.out.println("=== 微信推送程序启动 ===");

        // 读取环境变量
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");

        // 微信核心配置判空
        if (appId == null || appId.trim().isEmpty() || appSecret == null || appSecret.trim().isEmpty() || openId == null || openId.trim().isEmpty()) {
            System.err.println("错误：微信核心配置缺失，程序终止");
            System.exit(1);
        }

        try {
            // 1. 获取微信access_token
            String accessToken = getAccessToken(appId, appSecret);
            System.out.println("✅ 获取access_token成功");

            // 2. 获取南京天气（零配置，不需要API Key）
            String weatherInfo = "⚠️  暂时无法获取天气信息\n";
            try {
                weatherInfo = getNanjingWeather();
                System.out.println("✅ 天气信息获取成功");
            } catch (Exception e) {
                System.err.println("⚠️  天气获取失败：" + e.getMessage());
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
     * 【新】获取南京天气，用wttr.in免费接口，零配置
     */
    private static String getNanjingWeather() throws Exception {
        System.out.println("🌤️  正在请求免费天气接口...");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(WEATHER_URL);
            // 模拟浏览器请求，避免被拦截
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            try (CloseableHttpResponse response = client.execute(get)) {
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("🌤️  天气接口返回：" + body.substring(0, Math.min(200, body.length())) + "...");

                // 解析天气数据
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonObject current = json.getAsJsonArray("current_condition").get(0).getAsJsonObject();
                JsonObject weather = json.getAsJsonArray("weather").get(0).getAsJsonObject();

                // 提取信息
                String tempC = current.get("temp_C").getAsString(); // 温度
                String weatherDesc = current.getAsJsonArray("weatherDesc").get(0).getAsJsonObject().get("value").getAsString(); // 天气状况
                String humidity = current.get("humidity").getAsString(); // 湿度
                String windspeed = current.get("windspeedKmph").getAsString(); // 风速
                String maxtemp = weather.getAsJsonArray("maxtempC").get(0).getAsString(); // 最高温
                String mintemp = weather.getAsJsonArray("mintempC").get(0).getAsString(); // 最低温

                return "天气：" + weatherDesc + "\n" +
                       "温度：" + tempC + "℃（最低" + mintemp + "℃ / 最高" + maxtemp + "℃）\n" +
                       "湿度：" + humidity + "%\n" +
                       "风速：" + windspeed + "km/h";
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
