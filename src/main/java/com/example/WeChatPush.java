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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class WeChatPush {
    // ==================== 【完全保留你的常量】 ====================
    private static final String GIRLFRIEND_NAME = "刘雨嫣";
    private static final int NANJING_CITY_ID = 1806260;
    private static final LocalDate LOVE_START_DATE = LocalDate.of(2025, 11, 6);
    // ==============================================================

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";
    private static final String SEND_TEMPLATE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=";
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?id=" + NANJING_CITY_ID + "&units=metric&lang=zh_cn&appid=";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        System.out.println("=== 微信推送程序启动 ===");

        // 读取环境变量
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");
        String weatherKey = System.getenv("OPENWEATHER_API_KEY");
        String templateId = System.getenv("WECHAT_TEMPLATE_ID");

        // 微信核心配置判空
        if (appId == null || appId.trim().isEmpty()
                || appSecret == null || appSecret.trim().isEmpty()
                || openId == null || openId.trim().isEmpty()
                || templateId == null || templateId.trim().isEmpty()) {
            System.err.println("错误：微信核心配置或模板ID缺失，程序终止");
            System.exit(1);
        }

        try {
            // 【完全保留你的日期计算】
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", Locale.CHINA);
            String todayStr = today.format(dateFormatter);
            long loveDays = ChronoUnit.DAYS.between(LOVE_START_DATE, today);

            System.out.println("📅 今天日期：" + todayStr);
            System.out.println("⏳ 在一起：" + loveDays + "天");
            
            // 【完全保留】获取微信access_token
            String accessToken = getAccessToken(appId, appSecret);
            System.out.println("✅ 获取access_token成功");

            // 【完全保留你的天气获取逻辑】
            String greeting = "☀️☀️" + GIRLFRIEND_NAME + "小宝宝早安！☀️☀️";
            String weatherDesc = "未知";
            String temp = "未知";
            String humidity = "未知";
            String windSpeed = "未知";
            String pressure = "未知";
            String closing = "新的一天也要开心哦🥰\n加油加油💪";

            if (weatherKey != null && !weatherKey.trim().isEmpty()) {
                try {
                    JsonObject weatherData = getWeatherData(weatherKey);
                    JsonObject main = weatherData.getAsJsonObject("main");
                    JsonObject weather = weatherData.getAsJsonArray("weather").get(0).getAsJsonObject();
                    JsonObject wind = weatherData.getAsJsonObject("wind");

                    weatherDesc = weather.get("description").getAsString();
                    temp = main.get("temp").getAsString() + "℃（体感" + main.get("feels_like").getAsString() + "℃）";
                    humidity = main.get("humidity").getAsString() + "%";
                    windSpeed = wind.get("speed").getAsString() + "m/s";
                    pressure = main.get("pressure").getAsString() + "hPa";
                    
                    System.out.println("✅ 天气获取成功：" + weatherDesc + " " + temp);
                } catch (Exception e) {
                    System.err.println("⚠️  天气获取失败：" + e.getMessage());
                }
            }

            // 【保留模板发送形式，内容改回你原来的文案】
            String result = sendTemplateMessage(accessToken, openId, templateId,
                    greeting, todayStr, String.valueOf(loveDays),
                    weatherDesc, temp, humidity, windSpeed, pressure, closing);
            
            System.out.println("✅ 微信接口响应：" + result);
            System.out.println("=== 推送执行完成 ===");

        } catch (Exception e) {
            System.err.println("❌ 程序执行失败：");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 【完全保留】天气数据获取
     */
    private static JsonObject getWeatherData(String weatherKey) throws Exception {
        String url = WEATHER_URL + weatherKey;
        System.out.println("🌤️  正在请求OpenWeather API...");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            get.setHeader("User-Agent", "Mozilla/5.0");

            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    String errorBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                    throw new RuntimeException("API状态码异常：" + statusCode);
                }
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                return GSON.fromJson(body, JsonObject.class);
            }
        }
    }

    /**
     * 【完全保留】获取Token
     */
    private static String getAccessToken(String appId, String secret) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse res = client.execute(new HttpGet(TOKEN_URL + "&appid=" + appId + "&secret=" + secret));
        String body = EntityUtils.toString(res.getEntity());
        return GSON.fromJson(body, JsonObject.class).get("access_token").getAsString();
    }

    /**
     * 【保留模板发送，彩色文字，内容匹配简化版模板】
     */
    private static String sendTemplateMessage(String token, String openId, String templateId,
            String greeting, String date, String loveDays,
            String weather, String temp, String humidity, String wind, String pressure, String closing) throws Exception {
        String url = SEND_TEMPLATE_URL + token;
        
        JsonObject msg = new JsonObject();
        msg.addProperty("touser", openId);
        msg.addProperty("template_id", templateId);

        // 设置模板参数和颜色（保留彩色效果）
        JsonObject data = new JsonObject();
        data.add("greeting", createParam(greeting, "#FF69B4")); // 粉色
        data.add("date", createParam(date, "#FFD700")); // 金色
        data.add("love_days", createParam(loveDays, "#32CD32")); // 绿色
        data.add("weather", createParam(weather, "#FF6347")); // 红色
        data.add("temp", createParam(temp, "#FF69B4")); // 粉色
        data.add("humidity", createParam(humidity, "#1E90FF")); // 蓝色
        data.add("wind", createParam(wind, "#9370DB")); // 紫色
        data.add("pressure", createParam(pressure, "#808080")); // 灰色
        data.add("closing", createParam(closing, "#FF69B4")); // 粉色

        msg.add("data", data);

        // 发送请求
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json;charset=UTF-8");
            post.setEntity(new StringEntity(GSON.toJson(msg), "UTF-8"));
            try (CloseableHttpResponse response = client.execute(post)) {
                return EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        }
    }

    /**
     * 辅助方法：创建带颜色的模板参数
     */
    private static JsonObject createParam(String value, String color) {
        JsonObject param = new JsonObject();
        param.addProperty("value", value);
        param.addProperty("color", color);
        return param;
    }
}
