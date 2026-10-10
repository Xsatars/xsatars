package com.aitable.game.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 一个 AI 可以<b>主动调用</b>的工具。
 *
 * <p>V0.3 之前是我们把材料（发言记录、投票记录、跨局战绩）一股脑塞进提示词，
 * AI 只能被动地读。这一版给它工具，让它<b>自己决定要查什么</b> ——
 * 想翻某人说过的话就去查，想看看票型就去调投票记录。</p>
 *
 * <p>好处有两个：一是它问得更聚焦（不是每步都塞一大段），
 * 二是这更接近"一个会用工具的 Agent"该有的样子。</p>
 */
public class AiTool {

    /** 工具名（要符合 OpenAI function calling 的命名要求：字母数字下划线） */
    private final String name;

    /** 给模型看的说明 —— 写得好不好直接决定它会不会用、用得对不对 */
    private final String description;

    /** 参数说明：参数名 → 说明（本项目的工具参数都很简单，用不着完整的 JSON Schema） */
    private final Map<String, String> parameters;

    /** 真正干活的函数：入参是模型给的那几个参数，返回一段给模型看的文本 */
    private final Function<Map<String, Object>, String> handler;

    public AiTool(String name, String description,
                  Map<String, String> parameters,
                  Function<Map<String, Object>, String> handler) {
        this.name = name;
        this.description = description;
        this.parameters = parameters == null ? new LinkedHashMap<>() : parameters;
        this.handler = handler;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * 执行这个工具。
     *
     * <p>工具本身<b>只读</b>：它只能查信息，不能改游戏状态。
     * 想改状态还是得走 {@link com.aitable.game.core.ActionRequest} → 引擎校验。</p>
     */
    public String execute(Map<String, Object> arguments) {
        try {
            String result = handler.apply(arguments == null ? Map.of() : arguments);
            return result == null || result.isBlank() ? "（没有查到内容）" : result;
        } catch (Exception e) {
            // 工具自己出错不该把 AI 卡死，把它当成"查不到"告诉模型
            return "（查询失败：" + e.getMessage() + "）";
        }
    }

    /**
     * 转成 DeepSeek（OpenAI 兼容）要求的 function 描述格式。
     */
    public Map<String, Object> toApiFormat() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            properties.put(entry.getKey(), Map.of(
                    "type", "string",
                    "description", entry.getValue()));
        }

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", Map.of(
                "type", "object",
                "properties", properties,
                // 我们的工具参数都是可选的，让模型想查就查、不查也行
                "required", java.util.List.of()));

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }
}
