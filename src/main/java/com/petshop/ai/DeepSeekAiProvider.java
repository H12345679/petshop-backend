package com.petshop.ai;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 实现（OpenAI 兼容协议）。
 * <p>
 * 启用方式：在 application.yml 中设置 ai.provider=deepseek 并配置 api-key。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "deepseek")
public class DeepSeekAiProvider implements AiProvider {

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${ai.deepseek.timeout:30000}")
    private int timeout;

    private String apiUrl;
    private final RestTemplate restTemplate;

    public DeepSeekAiProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        this.apiUrl = baseUrl + "/chat/completions";
        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) this.restTemplate.getRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        log.info("DeepSeek AI Provider 已启用，接口: {}, 模型: {}, 超时: {}ms", apiUrl, model, timeout);
    }

    @Override
    public String chat(String question, String context) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            String sysPrompt = "你是一个专业的宠物健康顾问，擅长回答关于猫、狗、兔子、鹦鹉等常见宠物的饲养、健康、营养、行为等问题。请用中文回答，语气亲切专业。如果用户问的不是宠物相关的问题，请友好地引导用户回到宠物话题。";
            if (context != null && !context.trim().isEmpty()) {
                sysPrompt += "\n【商城在售商品库】：\n" + context +
                             "\n\n要求：如果用户询问购买建议，请严格从上述商品库中挑选1-3款推荐给他，必须给出推荐理由，并且必须使用Markdown链接格式附带商品链接，例如：[【商品名】](/product/商品ID)。如果商品库中没有合适的，请委婉说明。";
            }

            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", sysPrompt),
                    Map.of("role", "user", "content", question)
                ),
                "temperature", 0.7,
                "max_tokens", 1000
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<DeepSeekResponse> response = restTemplate.postForEntity(
                    apiUrl, request, DeepSeekResponse.class);

            if (response.getBody() != null
                    && response.getBody().choices != null
                    && !response.getBody().choices.isEmpty()) {
                return response.getBody().choices.get(0).message.content;
            }

            log.warn("DeepSeek 返回空结果");
            return "抱歉，AI 服务暂时无法回答，请稍后再试。";

        } catch (HttpClientErrorException e) {
            // 捕获 4xx 错误，打印 DeepSeek 返回的具体原因
            String respBody = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.error("DeepSeek API 认证/请求失败 [{}]: {}", e.getStatusCode(), respBody);
            return "AI 服务调用失败（" + e.getStatusCode() + "）：" + respBody;
        } catch (Exception e) {
            log.error("DeepSeek API 调用异常", e);
            return "抱歉，AI 服务暂时不可用：" + e.getMessage();
        }
    }

    // ---------- 响应映射 ----------

    @Data
    private static class DeepSeekResponse {
        private List<Choice> choices;
    }

    @Data
    private static class Choice {
        private Message message;
    }

    @Data
    private static class Message {
        private String role;
        private String content;
    }
}
