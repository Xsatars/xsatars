package com.aitable.config;

import com.aitable.game.ai.AiBrain;
import com.aitable.game.ai.AiBrainRouter;
import com.aitable.game.ai.AiReflector;
import com.aitable.game.ai.DeepSeekClient;
import com.aitable.game.ai.HostNarrator;
import com.aitable.game.ai.LlmAiBrain;
import com.aitable.game.ai.LlmHostNarrator;
import com.aitable.game.ai.LlmReflector;
import com.aitable.game.ai.SimpleAiBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * AI 脑子的装配。
 *
 * <p>装哪一套由配置 {@code ai-table.ai.brain} 决定：</p>
 * <ul>
 *   <li>{@code rule} —— 规则 AI（{@link SimpleAiBrain}），不联网、免费、立刻能用</li>
 *   <li>{@code llm}  —— 大模型（{@link LlmAiBrain}），需要环境变量 {@code DEEPSEEK_API_KEY}</li>
 * </ul>
 *
 * <p>注意两件事：<b>① Key 只从环境变量读，绝不写进配置文件</b>（否则会跟着代码进 Git 仓库）；
 * <b>② 大模型那套自带规则 AI 作为兜底</b>——网络抖一下也不能让一桌人干等。</p>
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Value("${ai-table.ai.brain:rule}")
    private String brainType;

    @Value("${ai-table.ai.llm.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai-table.ai.llm.api-key:}")
    private String apiKey;

    @Value("${ai-table.ai.llm.model:deepseek-chat}")
    private String model;

    @Value("${ai-table.ai.llm.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${ai-table.ai.llm.temperature:0.8}")
    private double temperature;

    /** 是否开启局后复盘（可选功能：关掉就不调大模型，结算面板也不显示复盘） */
    @Value("${ai-table.ai.reflection-enabled:true}")
    private boolean reflectionEnabled;

    /**
     * AI 脑子的分发器：规则 AI 和大模型 AI <b>两个都装上</b>，由房间自己选。
     *
     * <p>跟以前不一样的地方：以前只装一套（配置写死），换一种要改配置重启。
     * 现在装一个分发器，{@code Room.aiBrain} 决定这局用哪个 ——
     * 玩家想省流量就用规则 AI，想要有人味的发言就切大模型。</p>
     *
     * <p>注意 {@link #brainType} 现在只作为<b>默认值</b>（新建房间时用它）；
     * 大模型没配 Key 时 router 会自动退回规则 AI，不会让一桌人干等。</p>
     */
    @Bean
    public AiBrainRouter aiBrainRouter() {
        SimpleAiBrain ruleBrain = new SimpleAiBrain();

        AiBrain llmBrain = null;
        if (StringUtils.hasText(apiKey)) {
            DeepSeekClient client = new DeepSeekClient(baseUrl, apiKey, model, timeoutSeconds, temperature);
            llmBrain = new LlmAiBrain(client, ruleBrain);
            log.info("AI 脑子：规则 AI + 大模型 {}（房间可自选，调用失败自动回退）", model);
        } else {
            log.info("AI 脑子：只有规则 AI（没读到 API Key，房间选了大模型也会退回规则）");
        }

        return new AiBrainRouter(ruleBrain, llmBrain);
    }

    /** 新建房间时默认用哪种脑子（{@code rule} / {@code llm}） */
    public String defaultBrainType() {
        return "llm".equalsIgnoreCase(brainType) ? "llm" : "rule";
    }

    /**
     * 主持人（上帝）的解说。
     *
     * <p>没接大模型时返回一个"永远不说话"的实现 —— 上层会自动用事件自带的兜底文案，
     * 所以主持人这个角色<b>永远有个默认表现</b>，不会因为没配 Key 就消失。</p>
     */
    @Bean
    public HostNarrator hostNarrator() {
        if (!"llm".equalsIgnoreCase(brainType) || !StringUtils.hasText(apiKey)) {
            log.info("主持人：静态解说（没接大模型，用事件自带的文案）");
            return context -> null;
        }
        log.info("主持人：大模型解说（讲不出来会自动退回静态文案）");
        DeepSeekClient client = new DeepSeekClient(baseUrl, apiKey, model, timeoutSeconds, temperature);
        return new LlmHostNarrator(client);
    }

    /**
     * AI 的局后复盘（<b>可选功能</b>）。
     *
     * <p>三种情况会关掉它，都是返回一个"永远不复盘"的实现：</p>
     * <ul>
     *   <li>配置里显式关了（{@code ai-table.ai.reflection-enabled=false}）</li>
     *   <li>没接大模型</li>
     * </ul>
     * <p>关掉之后战绩里就不写复盘那一列，跨局记忆里也就没有"上局的教训"，
     * 其余一切照常。</p>
     */
    @Bean
    public AiReflector aiReflector() {
        if (!reflectionEnabled) {
            log.info("AI 复盘：已关闭（ai-table.ai.reflection-enabled=false）");
            return context -> null;
        }
        if (!"llm".equalsIgnoreCase(brainType) || !StringUtils.hasText(apiKey)) {
            log.info("AI 复盘：关闭（没接大模型）");
            return context -> null;
        }
        log.info("AI 复盘：开启（每局结束让每个 AI 反省一句）");
        DeepSeekClient client = new DeepSeekClient(baseUrl, apiKey, model, timeoutSeconds, temperature);
        return new LlmReflector(client);
    }
}
