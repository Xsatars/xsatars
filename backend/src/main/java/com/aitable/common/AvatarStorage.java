package com.aitable.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 头像文件存储。
 *
 * <p>头像直接落在服务器磁盘上（默认 {@code ./uploads/avatar/}），数据库里只存一个相对地址，
 * 例如 {@code /uploads/avatar/12_1730000000000.png}。
 * 这样图片不占数据库空间，也不用手动改表结构。</p>
 */
@Component
public class AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorage.class);

    /** 头像最大 2MB */
    private static final long MAX_SIZE = 2 * 1024 * 1024L;

    /** 允许的图片类型 */
    private static final String[] ALLOWED_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp"};

    /** 存放目录，来自 application.yml；默认放在项目下的 uploads */
    @Value("${ai-table.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 保存头像。
     *
     * @return 可以直接给浏览器用的地址，例如 /uploads/avatar/12_xxx.png
     */
    public String store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("图片太大了，请上传 2MB 以内的图片");
        }

        String ext = resolveExtension(file.getOriginalFilename());
        if (ext == null) {
            throw new BusinessException("只支持 png / jpg / gif / webp 格式的图片");
        }

        // 文件名带上时间戳，避免浏览器缓存住旧头像
        String filename = userId + "_" + System.currentTimeMillis() + ext;
        Path dir = Paths.get(uploadDir, "avatar").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
            log.info("头像已保存: {}", target);
            return "/uploads/avatar/" + filename;
        } catch (IOException e) {
            log.error("保存头像失败", e);
            throw new BusinessException("头像保存失败，请稍后再试");
        }
    }

    /** 删掉旧头像文件（换头像 / 清空头像时调用），失败不影响主流程 */
    public void deleteQuietly(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl) || !avatarUrl.startsWith("/uploads/avatar/")) {
            return;
        }
        try {
            Path file = Paths.get(uploadDir, "avatar", avatarUrl.substring("/uploads/avatar/".length()))
                    .toAbsolutePath().normalize();
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除旧头像失败（不影响使用）: {}", e.getMessage());
        }
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        String lower = originalFilename.toLowerCase();
        for (String ext : ALLOWED_EXT) {
            if (lower.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }
}
