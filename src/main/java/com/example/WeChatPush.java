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
    // 国内稳定免费天气接口，免Key、免注册，直接用
    private static final String WEATHER_URL = "https://api.oioweb.cn/api/weather/GetWeather?cityName=南京";
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
            // 1. 获取微信access_token（必须成功才能发消息）
            String accessToken = getAccessToken(appId, appSecret);
            System.out.println("✅ 获取access_token成功");

            // 2. 获取南京天气，全量异常兜底，失败不影响微信发送
            String weatherInfo = "⚠️  暂时无法获取天气信息\n";
            try {
                weatherInfo = getNanjingWeather();
                System.out.println("✅ 天气信息获取成功：" + weatherInfo.replace("\n", " "));
            } catch (Exception e) {
                System.err.println("⚠️  天气获取失败，原因：" + e.getMessage());
            }

            // 3. 拼接最终推送文案
            String pushContent = "☀️ 早安！\n" +
                                 "【南京今日天气】\n" +
                                 weatherInfo + "\n" +
                                 "新的一天也要开心呀！";
            
            // 4. 发送微信消息
            String result = sendMsg(accessToken, openId, pushContent);
            System.out.println("✅ 微信接口响应：" + result);
            System.out.println("=== 推送执行完成 ===");

        } catch (Exception e) {
            System.err.println("❌ 程序核心执行失败：");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 获取南京实时天气，国内稳定接口，免Key
     */
    private static String getNanjingWeather() throws Exception {
        System.out.println("🌤️  正在请求天气接口...");
        // 用最基础的客户端创建方式，无兼容问题，自动关闭资源
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(WEATHER_URL);
            // 模拟浏览器请求，避免被接口拦截
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            get.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = client.execute(get)) {
                // 先判断接口响应状态
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RuntimeException("接口响应异常，状态码：" + statusCode);
                }

                // 读取接口返回内容
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("🌤️  天气接口返回：" + body.substring(0, Math.min(300, body.length())) + "...");

                // 解析JSON数据
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                // 校验接口调用是否成功
                if (!json.has("code") || json.get("code").getAsInt() != 200) {
                    throw new RuntimeException("接口返回错误，错误信息：" + (json.has("msg") ? json.get("msg").getAsString() : "未知"));
                }

                // 提取天气核心信息
                JsonObject data = json.getAsJsonObject("data");
                JsonObject realtime = data.getAsJsonObject("realtime");
                JsonObject today = data.getAsJsonObject("today");

                String weather = realtime.get("weather").getAsString();
                String temp = realtime.get("temperature").getAsString();
                String lowTemp = today.get("low").getAsString();
                String highTemp = today.get("high").getAsString();
                String humidity = realtime.get("humidity").getAsString();
                String wind = realtime.get("windDirect").getAsString() + realtime.get("windPower").getAsString();

                // 拼接成易读的文案
                return "天气：" + weather + "\n" +
                       "实时温度：" + temp + "℃\n" +
                       "今日温度：" + lowTemp + " ~ " + highTemp + "\n" +
                       "湿度：" + humidity + "%\n" +
                       "风向：" + wind;
            }
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
