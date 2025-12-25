package com.wmsay.gpt4_lll.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.model.baidu.TokenInfo;
import com.wmsay.gpt4_lll.model.server.ApiResponse;
import com.wmsay.gpt4_lll.model.server.TokenResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AuthUtils {
    private static final String FILE_NAME = "GPT4_lll_Auth.json";
    private static final Path FILE_PATH = PluginPathUtils.pluginTempFile(FILE_NAME);

    private static final String ACCESS_TOKEN_KEY="accessToken";
    private static final String ACCESS_TOKEN_EXPIRE_KEY="expireTime";

    private static final String BAIDU_FREE_ACCESS_TOKEN_KEY="baiduFreeAccessToken";
    private static final String BAIDU_FREE_ACCESS_TOKEN_EXPIRE_KEY="baiduFreeExpireTime";

    public static String getBaiduAccessToken(){
        String accessToken = null;
        try {
            LinkedHashMap<String, String> accessTokenInfo= loadData();
            if (!accessTokenInfo.isEmpty()){
                String expStr= accessTokenInfo.get(ACCESS_TOKEN_EXPIRE_KEY);
                if (expStr!=null){
                    Long expireDateMilSec=Long.parseLong(expStr);
                    if (expireDateMilSec> Instant.now().toEpochMilli()){
                        accessToken=accessTokenInfo.get(ACCESS_TOKEN_KEY);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 如果本地没能成功获取accessToken，则从网络获取
        if (accessToken==null||accessToken.isEmpty()) {
            MyPluginSettings settings = MyPluginSettings.getInstance();
            String apiKey = settings.getBaiduAPIKey();
            String secretKey = settings.getBaiduSecretKey();
            accessToken = getBaiduAccessTokenFromApi(apiKey, secretKey, null);
        }
        return accessToken;
    }

    static String getBaiduAccessTokenFromApi(String clientId, String clientSecret, String grantType){
        AtomicReference<String> accessToken = new AtomicReference<>("");
        AtomicInteger expiresIn = new AtomicInteger();

        if (grantType==null||grantType.isEmpty()){
            grantType = "client_credentials";
        }

        HttpClient client = HttpClient.newBuilder()
                .build()
                ;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://aip.baidubce.com/oauth/2.0/token?client_id="+clientId+"&client_secret="+clientSecret+"&grant_type="+grantType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAcceptAsync(stringHttpResponse -> {
            TokenInfo tokenInfo= JSON.parseObject(stringHttpResponse.body(), TokenInfo.class);
            accessToken.set(tokenInfo.getAccess_token());
            expiresIn.set(tokenInfo.getExpires_in());
        }).join();

        LinkedHashMap<String,String> data = new LinkedHashMap<>();
        data.put(ACCESS_TOKEN_KEY,accessToken.get());
        // 获取当前时间的时间戳（毫秒）
        long now = System.currentTimeMillis();
        // 计算过期时间的时间戳（毫秒）
        long expirationTimeMillis = now + ((expiresIn.get() - 60) * 1000L); // 将秒转换为毫秒
        data.put(ACCESS_TOKEN_EXPIRE_KEY, String.valueOf(expirationTimeMillis));
        try {
            saveData(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return accessToken.get();
    }


    public static String getFreeBaiduAccessToken(){
        String accessToken = null;
        try {
            LinkedHashMap<String, String> accessTokenInfo= loadData();
            if (!accessTokenInfo.isEmpty()){
                String expStr= accessTokenInfo.get(BAIDU_FREE_ACCESS_TOKEN_EXPIRE_KEY);
                if (expStr!=null){
                    Long expireDateMilSec=Long.parseLong(expStr);
                    if (expireDateMilSec> Instant.now().toEpochMilli()){
                        accessToken=accessTokenInfo.get(BAIDU_FREE_ACCESS_TOKEN_KEY);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 如果本地没能成功获取accessToken，则从网络获取
        if (accessToken==null||accessToken.isEmpty()) {
            accessToken = getFreeBaiduAccessTokenFromServer();
        }
        return accessToken;
    }

    static String getFreeBaiduAccessTokenFromServer(){
        AtomicReference<String> accessToken = new AtomicReference<>("");
        AtomicReference<Date> expiresDate = new AtomicReference<>();
        HttpClient client = HttpClient.newBuilder()
                .build()
                ;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://blog.wmsay.com/gpt/free/getToken"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAcceptAsync(stringHttpResponse -> {
            ApiResponse<TokenResult> tokenInfo= JSON.parseObject(stringHttpResponse.body(), new TypeReference<ApiResponse<TokenResult>>() {});
            if (tokenInfo.isSuccess()){
                tokenInfo.getData().getTokenList().forEach(apiToken -> {
                    if ("BaiduFree".equals(apiToken.getApiModel()) ){
                        accessToken.set( apiToken.getToken());
                        expiresDate.set(apiToken.getExpireDate());
                    }
                });
            }
        }).join();
        LinkedHashMap<String,String> data = new LinkedHashMap<>();
        data.put(BAIDU_FREE_ACCESS_TOKEN_KEY,accessToken.get());
        // 获取当前时间的时间戳（毫秒）
        data.put(BAIDU_FREE_ACCESS_TOKEN_EXPIRE_KEY, String.valueOf(expiresDate.get().getTime()));
        try {
            saveData(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return accessToken.get();
    }





    public static LinkedHashMap<String, String> loadData() throws IOException {
        if (Files.notExists(FILE_PATH)) {
            // If the file does not exist, return an empty map
            return new LinkedHashMap<>();
        }

        byte[] bytes = Files.readAllBytes(FILE_PATH);

        // If the file is empty, return an empty map
        if (bytes.length == 0) {
            return new LinkedHashMap<>();
        }

        // Convert the JSON string to a map
        return JSON.parseObject(new String(bytes), new TypeReference<LinkedHashMap<String, String>>() {});
    }

    public static void saveData(LinkedHashMap<String, String> data) throws IOException {
        System.out.println(FILE_PATH);
        // Make sure the directory exists
        Files.createDirectories(FILE_PATH.getParent());

        // Convert the map to a JSON string
        String jsonString = JSON.toJSONString(data);

        // Write the JSON string to file
        Files.write(FILE_PATH, jsonString.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

}
