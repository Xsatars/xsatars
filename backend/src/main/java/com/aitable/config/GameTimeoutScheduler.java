package com.aitable.config;

import com.aitable.game.core.GameManager;
import com.aitable.game.core.GameRuntime;
import com.aitable.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 对局超时扫描任务。
 *
 * <p>为什么需要它：如果某个玩家一直不操作（挂机、关掉页面），对局就永远停在
 * "等他刀人 / 等他发言 / 等他投票"那一步。这个任务每隔几秒扫一遍正在进行的对局，
 * 发现某一步等太久了，就替他做默认处理（跳过 / 空刀 / 弃权），让整局能继续走下去。</p>
 *
 * <p>超时时长和扫描间隔都在 {@code application.yml} 的 {@code ai-table.game} 下配置。</p>
 */
@Component
public class GameTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameTimeoutScheduler.class);

    private final GameManager gameManager;
    private final GameService gameService;

    public GameTimeoutScheduler(GameManager gameManager, GameService gameService) {
        this.gameManager = gameManager;
        this.gameService = gameService;
    }

    @Scheduled(fixedDelayString = "${ai-table.game.timeout-check-interval-ms:2000}")
    public void scanTimeouts() {
        for (GameRuntime runtime : gameManager.all()) {
            try {
                gameService.handleTimeoutIfNeeded(runtime.getGameId());
            } catch (Exception e) {
                // 一局出错不能影响其它对局的扫描
                log.error("处理对局超时失败: gameId={}", runtime.getGameId(), e);
            }
        }
    }
}
