package com.aitable.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 前端用的是 history 路由（地址栏里没有 {@code #}）。
 *
 * <p>这带来一个小问题：别人直接打开 {@code /room/123456}，或者在游戏页按 F5 刷新时，
 * 请求会先打到后端——但后端的接口表里并没有这个地址。</p>
 *
 * <p>所以这里把这些"其实是前端路由"的地址统一转发到 index.html，
 * 剩下的交给前端自己处理。</p>
 */
@Controller
public class SpaForwardController {

    /** 前端的页面路由（加新的页面时记得也加到这里） */
    @RequestMapping({"/login", "/register", "/forgot-password", "/profile",
            "/room/**", "/game/**", "/admin"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
