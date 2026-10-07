/**
 * 游戏引擎相关代码（当前阶段为空，只占位）。
 *
 * <p>后续规划（对应架构文档的 V0.1 阶段）：</p>
 * <ul>
 *   <li>{@code game/core}：与具体游戏无关的引擎抽象，例如 GameEngine、GameState、
 *       ActionRequest、PlayerView（按座位过滤后的只读视图）</li>
 *   <li>{@code game/werewolf}：狼人杀规则，角色、夜晚阶段、白天投票、胜负判定</li>
 * </ul>
 *
 * <p>核心原则：<b>AI 负责决策，Game Engine 负责规则</b>。AI 只能提交"行动请求"，
 * 是否合法、改动什么状态由引擎决定。</p>
 */
package com.aitable.game;
