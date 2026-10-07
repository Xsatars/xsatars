package com.aitable.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 的定时任务支持。
 *
 * <p>目前只有一个用途：定期检查对局有没有卡在某个步骤上（玩家挂机），
 * 卡住就替他做默认处理，见 {@link GameTimeoutScheduler}。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
