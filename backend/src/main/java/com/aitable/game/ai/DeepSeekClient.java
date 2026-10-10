package com.aitable.game.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 调 DeepSeek 的 HTTP 客户端。
 *
 * <p>为什么不用 Spring AI 之类的大框架：这里要做的事情非常小 ——
 * "发一段话，拿一段话回来"，直接调 HTTP 依赖更少、出问题也更好定位。
 * 至于 Tool Calling，我们这套架构本来就有等价物：
 * AI 只能返回一个 {@link com.aitable.game.core.ActionRequest}，合不合法由引擎说了算。</p>
 */
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;
    private final double temperature;

    public DeepSeekClient(String baseUrl, String apiKey, String model,
                          int timeoutSeconds, double temperature) {
        this.model = model;
        this.temperature = temperature;

        // 一定要设超时：模型偶尔会慢，不设的话一桌人就得干等
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 让模型回一段文本。
     *
     * @param systemPrompt 系统提示（角色设定、规则、输出格式）
     * @param userPrompt   这一轮的局面
     * @return 模型回复的正文；调用失败返回 null（调用方负责兜底）
     */
    public String chat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, true);
    }

    /**
     * 让模型回一段<b>自然语言</b>（不要求 JSON）。
     *
     * <p>给主持人解说用 —— 那种内容本来就该是散文，硬套 JSON 只会让它写得别扭。</p>
     */
    public String chatText(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, false);
    }

    /**
     * 一次<b>带工具</b>的对话，返回原始响应体（解析交给调用方）。
     *
     * <p>模型可能两种反应：直接给答案，或者先说要调哪几个工具。
     * 两种都是正常的，所以这里把原始 JSON 原样交出去。</p>
     *
     * @return 原始响应；调用失败返回 null
     */
    public String chatRaw(String systemPrompt, String userPrompt, List<AiTool> tools) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", temperature);
        body.put("max_tokens", 400);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> definitions = new java.util.ArrayList<>();
            for (AiTool tool : tools) {
                definitions.add(tool.toApiFormat());
            }
            body.put("tools", definitions);
            // 让模型自己决定查不查 —— 有时候它已经知道该干嘛，硬要它查反而啰嗦
            body.put("tool_choice", "auto");
        }

        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("带工具的调用失败（将回退）：{}", e.getMessage());
            return null;
        }
    }

    private String doChat(String systemPrompt, String userPrompt, boolean jsonMode) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", temperature);
        body.put("max_tokens", 300);
        if (jsonMode) {
            // 要 JSON 就明说，省得模型给你加一堆解释文字
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? null : content.asText();
        } catch (Exception e) {
            // 网络抖动、额度用完、返回格式变了……都不该让对局卡死，
            // 这里只记日志，交给上层用规则 AI 兜底
            log.warn("调用 DeepSeek 失败（将回退到规则 AI）：{}", e.getMessage());
            return null;
        }
    }
}
