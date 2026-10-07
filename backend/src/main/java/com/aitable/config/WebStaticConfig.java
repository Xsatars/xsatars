package com.aitable.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源映射：前端页面 + 用户上传的文件（头像）。
 *
 * <p>这样部署时<b>只需要跑一个后端进程</b>，别人用一个网址就能玩，
 * 不用单独启动前端服务，也不用把前端源码给别人。</p>
 *
 * <p>前端页面打包后放在 {@code backend/src/main/resources/static}（会被打进 jar）。
 * 如果想指向别的地方（比如刚 build 出来的 dist 目录），
 * 用 {@code --ai-table.web.static-dir=D:/某个/dist} 指定即可。</p>
 */
@Configuration
public class WebStaticConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebStaticConfig.class);

    /** 外部静态目录；留空则用 jar 里的 classpath:/static/ */
    @Value("${ai-table.web.static-dir:}")
    private String staticDir;

    /** 用户上传文件的存放目录（头像放在它下面的 avatar 子目录） */
    @Value("${ai-table.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 用户上传的文件：浏览器访问 /uploads/avatar/xxx.png 时从这里取
        String uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize()
                .toString().replace("\\", "/");
        if (!uploadLocation.endsWith("/")) {
            uploadLocation = uploadLocation + "/";
        }
        log.info("上传文件目录: {}", uploadLocation);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadLocation);

        if (StringUtils.hasText(staticDir)) {
            String location = staticDir.endsWith("/") ? staticDir : staticDir + "/";
            log.info("前端页面目录（外部）: {}", location);
            registry.addResourceHandler("/**")
                    .addResourceLocations("file:" + location);
        } else {
            log.info("前端页面目录：classpath:/static/（已打包在 jar 里）");
        }
    }
}
