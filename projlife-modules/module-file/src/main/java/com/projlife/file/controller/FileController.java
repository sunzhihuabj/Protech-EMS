package com.projlife.file.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.context.UserContextHolder;
import com.projlife.file.entity.SysAttachment;
import com.projlife.file.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件接口（file:download 控下载；上传登录即可，业务挂接点校验业务权限）。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ApiResponse<SysAttachment> upload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "COMMON") String bizType,
                                             @RequestParam(required = false) String bizId) throws IOException {
        return ApiResponse.ok(fileService.upload(file, bizType, bizId));
    }

    @GetMapping
    public ApiResponse<List<SysAttachment>> list(@RequestParam String bizType, @RequestParam String bizId) {
        return ApiResponse.ok(fileService.listByBiz(bizType, bizId));
    }

    /** BR-07：仅输出水印合成件；无任何路径返回原文件 */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws IOException {
        String realName = UserContextHolder.get() == null ? "未知用户" : UserContextHolder.get().getRealName();
        String userCode = UserContextHolder.get() == null ? "unknown" : UserContextHolder.get().getUserCode();
        FileService.DownloadResult result = fileService.download(id, realName, userCode);
        String encoded = URLEncoder.encode(result.fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType))
                .header("Content-Disposition", "attachment;filename*=utf-8''" + encoded)
                .body(result.content);
    }
}
