package com.projlife.fileservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件服务（独立进程，00 册 C-02）：PDF 转换、水印合成、照片处理。
 * 复用 module-file 的水印与存储核心（仅装配 service 层，不装配 Controller，避免与主应用重复暴露）。
 */
@SpringBootApplication(scanBasePackages = {"com.projlife.fileservice", "com.projlife.file.service"})
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
