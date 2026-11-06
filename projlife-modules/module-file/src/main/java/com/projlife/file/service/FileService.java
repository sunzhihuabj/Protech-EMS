package com.projlife.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.file.entity.SysAttachment;
import com.projlife.file.mapper.SysAttachmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件服务（S0-9）。
 * BR-07 铁律：download 只输出水印合成件；PDF→W2 水印 PDF；图片→W3 合成图且原始上传件即时删除；
 * 非白名单 MIME 拒绝（校验真实魔数，防 .exe 改名 .jpg，UC-SYS-12）；路径参数禁止穿越（UC-SYS-11）。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** 允许的扩展名 → 真实 MIME 白名单 */
    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "jpg", "jpeg", "png", "docx", "xlsx", "pptx");

    private final SysAttachmentMapper attachmentMapper;
    private final WatermarkService watermarkService;

    @Value("${projlife.storage.root:./data/storage}")
    private String storageRoot;

    public FileService(SysAttachmentMapper attachmentMapper, WatermarkService watermarkService) {
        this.attachmentMapper = attachmentMapper;
        this.watermarkService = watermarkService;
    }

    public SysAttachment upload(MultipartFile file, String bizType, String bizId) throws IOException {
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String ext = extOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("不支持的文件类型：" + ext);
        }
        byte[] content = file.getBytes();
        String realMime = probeMime(content);
        if (!realMimeMatchesExt(ext, realMime)) {
            throw BizException.badRequest("文件真实类型与扩展名不符，已拒绝");
        }

        String relPath = Paths.get(LocalDate.now().toString().replace("-", "/"),
                UUID.randomUUID().toString().replace("-", "") + "." + ext).toString();
        Path target = Paths.get(storageRoot, "original", relPath);
        Files.createDirectories(target.getParent());
        Files.write(target, content);

        SysAttachment attachment = new SysAttachment();
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachment.setFileName(original);
        attachment.setFilePath(relPath);
        attachment.setFileSize((long) content.length);
        attachment.setFileExt(ext);
        attachment.setMimeType(realMime);
        attachment.setSha256(sha256(content));
        attachment.setVersion(1);
        attachment.setIsWatermarked(0);
        attachment.setUploaderId(UserContextHolder.currentUserId());
        attachmentMapper.insert(attachment);
        log.info("文件上传 id={} name={} size={}", attachment.getId(), original, content.length);
        return attachment;
    }

    /**
     * 下载：只输出水印件。PDF 返回 W2 水印 PDF；图片返回 W3 合成图并删除原始件。
     */
    public DownloadResult download(Long id, String realName, String userCode) throws IOException {
        SysAttachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "文件不存在");
        }
        Path original = Paths.get(storageRoot, "original", attachment.getFilePath());
        if (!Files.exists(original)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "文件已缺失，请联系管理员");
        }
        if ("pdf".equals(attachment.getFileExt())) {
            try (InputStream in = Files.newInputStream(original)) {
                byte[] watermarked = watermarkService.watermarkPdf(in, realName, userCode);
                Path w2 = Paths.get(storageRoot, "watermarked", attachment.getFilePath());
                Files.createDirectories(w2.getParent());
                Files.write(w2, watermarked);
                attachment.setIsWatermarked(1);
                attachment.setWatermarkType("W2");
                attachmentMapper.updateById(attachment);
                return new DownloadResult(attachment.getFileName(), "application/pdf", watermarked);
            }
        }
        if ("jpg".equals(attachment.getFileExt()) || "jpeg".equals(attachment.getFileExt())
                || "png".equals(attachment.getFileExt())) {
            try (InputStream in = Files.newInputStream(original)) {
                byte[] watermarked = watermarkService.watermarkImage(in, realName, userCode);
                // W3：合成图落盘，原图删除（03 册 §4.1，验收 UC-SYS-09）
                Path w3 = Paths.get(storageRoot, "watermarked", attachment.getFilePath()).normalize();
                Path w3Base = Paths.get(storageRoot, "watermarked").normalize();
                if (!w3.startsWith(w3Base)) {
                    throw BizException.of(ErrorCode.PARAM_INVALID, "非法路径");
                }
                Files.createDirectories(w3.getParent());
                Files.write(w3, watermarked);
                Files.deleteIfExists(original);
                attachment.setIsWatermarked(1);
                attachment.setWatermarkType("W3");
                attachmentMapper.updateById(attachment);
                return new DownloadResult(stripExt(attachment.getFileName()) + ".png", "image/png", watermarked);
            }
        }
        // Office 等文档：S0 阶段返回拒绝，OnlyOffice 集成后转 PDF 水印下发（03 册 §4.2）
        throw BizException.of(ErrorCode.BLOCKED, "该类型文件仅支持在线预览，下载请先转换为 PDF")
                .with("blockCode", "BR-07");
    }

    public List<SysAttachment> listByBiz(String bizType, String bizId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<SysAttachment>()
                .eq(SysAttachment::getBizType, bizType)
                .eq(SysAttachment::getBizId, bizId)
                .orderByDesc(SysAttachment::getId));
    }

    private boolean realMimeMatchesExt(String ext, String mime) {
        switch (ext) {
            case "pdf":
                return "application/pdf".equals(mime);
            case "jpg":
            case "jpeg":
                return "image/jpeg".equals(mime);
            case "png":
                return "image/png".equals(mime);
            default:
                // OOXML 容器魔数为 zip：docx/xlsx/pptx 统一接受 application/zip
                return "application/zip".equals(mime);
        }
    }

    private String probeMime(byte[] content) {
        if (content.length < 8) {
            return "unknown";
        }
        if ((content[0] & 0xFF) == 0x25 && content[1] == 0x50 && content[2] == 0x44 && content[3] == 0x46) {
            return "application/pdf";
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if ((content[0] & 0xFF) == 0x89 && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47) {
            return "image/png";
        }
        if (content[0] == 0x50 && content[1] == 0x4B) {
            return "application/zip";
        }
        return "unknown";
    }

    private String extOf(String name) {
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExt(String name) {
        int idx = name.lastIndexOf('.');
        return idx < 0 ? name : name.substring(0, idx);
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /** 下载结果载体 */
    public static class DownloadResult {

        public final String fileName;
        public final String contentType;
        public final byte[] content;

        public DownloadResult(String fileName, String contentType, byte[] content) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.content = content;
        }
    }
}
