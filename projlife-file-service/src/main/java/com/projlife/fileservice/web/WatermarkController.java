package com.projlife.fileservice.web;

import com.projlife.file.service.WatermarkService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件服务 HTTP 契约（主应用与大文件处理解耦的旁路接口）。
 * 仅内网可达；水印参数来自主应用当前登录上下文（主应用负责鉴权后转发）。
 */
@RestController
@RequestMapping("/fs")
public class WatermarkController {

    private final WatermarkService watermarkService;

    public WatermarkController(WatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    @PostMapping(value = "/pdf/watermark", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfWatermark(@RequestParam("file") MultipartFile file,
                                               @RequestParam String realName,
                                               @RequestParam String userCode) throws IOException {
        byte[] result = watermarkService.watermarkPdf(file.getInputStream(), realName, userCode);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(result);
    }

    @PostMapping(value = "/image/watermark", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> imageWatermark(@RequestParam("file") MultipartFile file,
                                                 @RequestParam String realName,
                                                 @RequestParam String userCode) throws IOException {
        byte[] result = watermarkService.watermarkImage(file.getInputStream(), realName, userCode);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(result);
    }
}
