package com.projlife.file.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 水印合成（03 册 §4.1 四种形态中的两种文件级水印）：
 * W2 文档水印：PDF 全页斜向平铺「姓名 工号 时间」；
 * W3 照片水印：合成后返回新图，原图由调用方删除（验收：原图不存在）。
 */
@Service
public class WatermarkService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] watermarkPdf(InputStream source, String name, String userCode) throws IOException {
        try (PDDocument document = PDDocument.load(source)) {
            String text = name + " " + userCode + " " + LocalDateTime.now().format(TS);
            for (PDPage page : document.getPages()) {
                tileText(document, page, text);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void tileText(PDDocument document, PDPage page, String text) throws IOException {
        PDRectangle size = page.getMediaBox();
        double rad = Math.toRadians(30f);
        for (float y = 40; y < size.getHeight(); y += 100) {
            for (float x = 30; x < size.getWidth(); x += 320) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 11);
                    stream.setNonStrokingColor(215, 215, 215);
                    stream.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(rad, x, y));
                    stream.showText(text);
                    stream.endText();
                }
            }
        }
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
