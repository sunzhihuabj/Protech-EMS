package com.projlife.file.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 水印合成（03 册 §4.1 四种形态中的两种文件级水印）：
 * W2 文档水印：PDF 全页斜向平铺「姓名 工号 时间」；
 * W3 照片水印：合成后返回新图，原图由调用方删除（验收：原图不存在）。
 *
 * 中文水印需 CJK 字体：按 projlife.watermark.font-path → Windows 楷黑/雅黑 → Linux Noto 顺序加载；
 * 全部缺失时降级为 Helvetica + 纯 ASCII 文本（工号+时间），不阻断下载（BR-07 仍生效）。
 */
@Service
public class WatermarkService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 生产（Linux 容器）配置的 CJK 字体路径，多个用逗号分隔 */
    @Value("${projlife.watermark.font-path:}")
    private String configuredFontPath;

    public byte[] watermarkPdf(InputStream source, String name, String userCode) throws IOException {
        try (PDDocument document = PDDocument.load(source)) {
            PDFont font = loadCjkFont(document);
            String text = buildText(name, userCode, font != null);
            for (PDPage page : document.getPages()) {
                tileText(document, page, text, font);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void tileText(PDDocument document, PDPage page, String text, PDFont font) throws IOException {
        PDRectangle size = page.getMediaBox();
        double rad = Math.toRadians(30f);
        for (float y = 40; y < size.getHeight(); y += 100) {
            for (float x = 30; x < size.getWidth(); x += 320) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.beginText();
                    stream.setFont(font == null ? PDType1Font.HELVETICA : font, 11);
                    stream.setNonStrokingColor(215, 215, 215);
                    stream.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(rad, x, y));
                    stream.showText(text);
                    stream.endText();
                }
            }
        }
    }

    private String buildText(String name, String userCode, boolean cjkAvailable) {
        String displayName = cjkAvailable ? name : toAscii(name);
        return displayName + " " + userCode + " " + LocalDateTime.now().format(TS);
    }

    /** ASCII 降级：水印仍可追溯（工号+时间），不因字体缺失阻塞下载 */
    private String toAscii(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "");
    }

    private PDFont loadCjkFont(PDDocument document) {
        for (String candidate : candidates()) {
            File file = new File(candidate);
            if (file.exists()) {
                try {
                    return PDType0Font.load(document, file);
                } catch (Exception ex) {
                    // 单个字体加载失败（如 TTC 解析）继续尝试下一个
                }
            }
        }
        return null;
    }

    private String[] candidates() {
        String[] configured = configuredFontPath == null || configuredFontPath.isEmpty()
                ? new String[0] : configuredFontPath.split("\\s*,\\s*");
        String[] defaults = {
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/msyh.ttf",
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
        };
        String[] all = new String[configured.length + defaults.length];
        System.arraycopy(configured, 0, all, 0, configured.length);
        System.arraycopy(defaults, 0, all, configured.length, defaults.length);
        return all;
    }

    /** 照片水印：右下角落款 + 中央斜向重复；返回合成图 PNG 字节 */
    public byte[] watermarkImage(InputStream source, String name, String userCode) throws IOException {
        BufferedImage image = javax.imageio.ImageIO.read(source);
        if (image == null) {
            throw new IllegalArgumentException("不是有效的图片文件");
        }
        BufferedImage target = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 255, 255, 70));
        g.rotate(Math.toRadians(-25));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, Math.max(14, image.getWidth() / 40)));
        String text = name + " " + userCode + " " + LocalDateTime.now().format(TS);
        int width = image.getWidth() * 2;
        for (int y = 0; y < width; y += 120) {
            for (int x = -image.getHeight(); x < width; x += 340) {
                g.drawString(text, x, y);
            }
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(target, "png", out);
        return out.toByteArray();
    }
}
