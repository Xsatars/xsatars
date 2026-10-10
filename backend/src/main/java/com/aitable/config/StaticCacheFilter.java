package com.aitable.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 静态资源的缓存策略。
 *
 * <p><b>为什么必须有这个</b>：前端打包后 JS/CSS 的文件名带内容哈希
 * （{@code index-CBHEIvrt.js}），但 <b>index.html 的文件名是固定的</b>。
 * 如果浏览器缓存了 index.html，它引用的就永远是<b>旧的那份 JS</b> ——
 * 表现就是"明明重新构建部署了，用户刷新后看到的还是老界面"。
 * （这个坑真实踩过，还一度以为是代码没生效，排查了很久。）</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li><b>带哈希的 /assets/*</b> → 可以长期缓存（内容变了文件名就变，不会拿错）</li>
 *   <li><b>入口页面</b>（{@code /}、{@code /index.html}、前端路由）→ <b>绝不缓存</b></li>
 *   <li><b>上传的头像</b> → 缓存一小时（不常变，也不影响功能）</li>
 * </ul>
 */
@Component
@Order(1)
public class StaticCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isHashedAsset(path)) {
            // Vite 产物的文件名里带内容哈希：内容一变文件名就变，所以放心长期缓存
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        } else if (isEntry(path)) {
            // 入口页面绝对不能缓存，否则它会一直引用旧的那份 JS。
            // 三种头都写上是为了兼容各种浏览器/代理的脾气。
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        } else if (path.startsWith("/uploads/")) {
            response.setHeader("Cache-Control", "public, max-age=3600");
        }

        chain.doFilter(request, response);
    }

    /** Vite 产物：{@code /assets/xxx-<hash>.js|css|字体|图片} */
    private boolean isHashedAsset(String path) {
        return path.startsWith("/assets/")
                && path.matches(".*-[A-Za-z0-9_-]{6,}\\.(js|css|woff2?|ttf|png|jpg|svg)$");
    }

    /**
     * 是不是"前端入口"。
     *
     * <p>接口（{@code /api/}）、WebSocket（{@code /ws}）、上传文件都不算入口 ——
     * 它们各有自己的缓存语义，不要被这里改掉。</p>
     */
    private boolean isEntry(String path) {
        if (path.startsWith("/api/") || path.startsWith("/ws") || path.startsWith("/uploads/")) {
            return false;
        }
        if (path.equals("/") || path.equals("/index.html")) {
            return true;
        }
        // history 路由（/room/123、/game/45 这种）：路径里没有"."的都不是静态文件，
        // 一律当入口返回 index.html，所以也不能缓存
        String lastSegment = path.substring(path.lastIndexOf('/'));
        return !lastSegment.contains(".");
    }
}
