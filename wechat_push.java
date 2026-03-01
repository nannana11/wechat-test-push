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
    // 微信官方接口地址
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";
    private static final String SEND_MSG_URL = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        // 从环境变量获取配置（和GitHub Secrets对应）
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");

        // 校验配置是否存在
        if (appId == null || appSecret == null || openId == null) {
            System.err.println("错误：未获取到微信配置，请检查GitHub Secrets是否正确设置");
            System.exit(1);
        }

        try {
            // 1. 获取access_token
            String accessToken = getAccessToken(appId, appSecret);
            System.out.println("获取access_token成功");

            // 2. 发送推送消息
            String result = sendCustomMessage(accessToken, openId, "Java开发者的每日推送测试成功！");
            System.out.println("微信接口响应: " + result);
            System.out.println("推送执行完成！");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 获取access_token
     */
    private static String getAccessToken(String appId, String appSecret) throws Exception {
        String url = TOKEN_URL + "&appid=" + appId + "&secret=" + appSecret;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                JsonObject jsonObject = GSON.fromJson(responseBody, JsonObject.class);

                // 处理接口报错
                if (jsonObject.has("errcode") && jsonObject.get("errcode").getAsInt() != 0) {
                    throw new RuntimeException("获取access_token失败：" + responseBody);
                }

                return jsonObject.get("access_token").getAsString();
            }
        }
    }

    /**
     * 发送客服消息（修正了微信要求的消息格式）
     */
    private static String sendCustomMessage(String accessToken, String openId, String content) throws Exception {
        String url = SEND_MSG_URL + accessToken;

        // 微信官方要求的正确消息格式
        JsonObject message = new JsonObject();
        message.addProperty("touser", openId);
        message.addProperty("msgtype", "text");

        JsonObject text = new JsonObject();
        text.addProperty("content", "【每日推送】" + content);
        message.add("text", text);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            httpPost.setEntity(new StringEntity(GSON.toJson(message), "UTF-8"));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                return EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        }
    }
}
