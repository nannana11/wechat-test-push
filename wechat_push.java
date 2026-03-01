package com.example;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class WeChatPush {
    public static void main(String[] args) {
        // 从环境变量获取配置（GitHub Secrets安全存储）
        String appId = System.getenv("WECHAT_APPID");
        String appSecret = System.getenv("WECHAT_APPSECRET");
        String openId = System.getenv("WECHAT_OPENID");
        
        try {
            // 1. 获取access_token
            String accessToken = getAccessToken(appId, appSecret);
            
            // 2. 准备推送消息
            String message = createMessage(openId, "今日内容：Java开发者的每日推送");
            
            // 3. 发送消息
            sendCustomMessage(accessToken, message);
            
            System.out.println("推送成功！消息内容: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 获取access_token
    private static String getAccessToken(String appId, String appSecret) throws Exception {
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + appId + "&secret=" + appSecret;
        
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        
        CloseableHttpResponse response = httpClient.execute(httpPost);
        String responseEntity = EntityUtils.toString(response.getEntity());
        
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(responseEntity, JsonObject.class);
        return jsonObject.get("access_token").getAsString();
    }
    
    // 创建消息内容
    private static String createMessage(String openId, String content) {
        Gson gson = new Gson();
        JsonObject message = new JsonObject();
        
        message.addProperty("touser", openId);
        JsonObject text = new JsonObject();
        text.addProperty("content", "【每日推送】" + content);
        message.add("msgtype", text);
        
        return gson.toJson(message);
    }
    
    // 发送消息
    private static void sendCustomMessage(String accessToken, String message) throws Exception {
        String url = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=" + accessToken;
        
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        
        httpPost.setEntity(new StringEntity(message, "UTF-8"));
        httpPost.setHeader("Content-Type", "application/json");
        
        CloseableHttpResponse response = httpClient.execute(httpPost);
        String responseEntity = EntityUtils.toString(response.getEntity());
        System.out.println("微信响应: " + responseEntity);
    }
}
