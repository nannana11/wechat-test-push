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
    private static final Gson GSON = new Gson();

    // 【3个备选天气接口，自动兜底】
    private static final String[] WEATHER_API_LIST = {
            // 主接口：海外稳定访问，免Key
            "https://wttr.in/Nanjing?format=j1",
            // 备用接口1：国内免Key接口
            "https://api.oioweb.cn/api/weather/GetWeather?cityName=南京",
            // 备用接口2：海外备用免Key接口
            "https://weatherdbi.herokuapp.com/data/weather/nanjing"
    };

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

            // 2. 循环尝试获取天气，3个接口都失败才用兜底文案
            String weatherInfo = "⚠️  暂时无法获取天气信息\n";
            for (int i = 0; i < WEATHER_API_LIST.length; i++) {
                try {
                    System.out.println("🌤️  正在尝试第" + (i+1) + "个天气接口...");
                    weatherInfo = getWeatherByApi(WEATHER_API_LIST[i]);
                    System.out.println("✅ 第" + (i+1) + "个接口获取天气成功");
                    break; // 成功就跳出循环，不用再试后面的接口
                } catch (Exception e) {
                    System.err.println("⚠️  第" + (i+1) + "个接口获取失败：" + e.getMessage());
                }
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
     * 统一天气获取方法，自动适配不同接口的返回格式
     */
    private static String getWeatherByApi(String apiUrl) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(apiUrl);
            // 模拟浏览器请求，避免被接口拦截
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            get.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = client.execute(get)) {
                // 先判断接口响应状态
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    throw new RuntimeException("接口状态码异常：" + statusCode);
                }

                // 读取接口返回内容
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("🌤️  接口返回预览：" + body.substring(0, Math.min(200, body.length())) + "...");

                // 根据URL自动适配解析逻辑
                if (apiUrl.contains("wttr.in")) {
                    return parseWttrIn(body);
                } else if (apiUrl.contains("oioweb.cn")) {
                    return parseOioWeb(body);
                } else if (apiUrl.contains("weatherdbi")) {
                    return parseWeatherDbi(body);
                } else {
                    throw new RuntimeException("不支持的接口");
                }
            }
        }
    }

    /**
     * 解析主接口 wttr.in 的返回数据
     */
    private static String parseWttrIn(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        JsonObject current = json.getAsJsonArray("current_condition").get(0).getAsJsonObject();
        JsonObject today = json.getAsJsonArray("weather").get(0).getAsJsonObject();

        String weather = current.getAsJsonArray("weatherDesc").get(0).getAsJsonObject().get("value").getAsString();
        String temp = current.get("temp_C").getAsString();
        String lowTemp = today.getAsJsonArray("mintempC").get(0).getAsString();
        String highTemp = today.getAsJsonArray("maxtempC").get(0).getAsString();
        String humidity = current.get("humidity").getAsString();
        String wind = current.get("winddir16Point").getAsString() + current.get("windspeedKmph").getAsString() + "km/h";

        return "天气：" + weather + "\n" +
                "实时温度：" + temp + "℃\n" +
                "今日温度：" + lowTemp + " ~ " + highTemp + "℃\n" +
                "湿度：" + humidity + "%\n" +
                "风向：" + wind;
    }

    /**
     * 解析备用接口 oioweb.cn 的返回数据
     */
    private static String parseOioWeb(String body) {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (!json.has("code") || json.get("code").getAsInt() != 200) {
            throw new RuntimeException("接口返回错误：" + json.get("msg").getAsString());
        }

        JsonObject data = json.getAsJsonObject("data");
        JsonObject realtime = data.getAsJsonObject("realtime");
        JsonObject today = data.getAsJsonObject("today");

        String weather = realtime.get("weather").getAsString();
        String temp = realtime.get("temperature").getAsString();
        String lowTemp = today.get("low").getAsString();
        String highTemp = today.get("high").getAsString();
        String humidity = realtime.get("humidity").getAsString();
        String wind = realtime.get("windDirect").getAsString() + realtime.get("windPower").getAsString();

        return "天气：" + weather + "\n" +
                "实时温度：" + temp + "℃\n" +
                "今日温度：" + lowTemp + " ~ " + highTemp + "\n" +
                "湿度：" + humidity + "%\n" +
                "风向：" + wind;
    }

    /**
     * 解析备用接口 weatherdbi 的返回数据
     */
    private static String parseWeatherDbi(String body) {
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (!json.has("status") || !json.get("status").getAsString().equals("success")) {
            throw new RuntimeException("接口返回失败");
        }

        JsonObject current = json.getAsJsonObject("current_condition");
        JsonObject today = json.getAsJsonArray("next_days").get(0).getAsJsonObject();

        String weather = current.get("comment").getAsString();
        String temp = current.get("temp").getAsJsonObject().get("c").getAsString();
        String lowTemp = today.get("min_temp").getAsJsonObject().get("c").getAsString();
        String highTemp = today.get("max_temp").getAsJsonObject().get("c").getAsString();
        String humidity = current.get("humidity").getAsString();
        String wind = current.get("wind").getAsString();

        return "天气：" + weather + "\n" +
                "实时温度：" + temp + "℃\n" +
                "今日温度：" + lowTemp + " ~ " + highTemp + "℃\n" +
                "湿度：" + humidity + "%\n" +
                "风向：" + wind;
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
