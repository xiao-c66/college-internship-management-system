package com.college.internship.util;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;

/**
 * PDF 中文字体加载工具类
 * 优先从 classpath /fonts/NotoSansSC-Regular.ttf 加载，系统字体作为备用回退
 */
@Slf4j
public class PdfFontUtil {

    private static BaseFont chineseBaseFont;

    public static synchronized BaseFont getChineseBaseFont() {
        if (chineseBaseFont != null) {
            return chineseBaseFont;
        }

        // 1. 优先从 classpath: /fonts/NotoSansSC-Regular.ttf 加载
        try (InputStream is = PdfFontUtil.class.getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
            if (is != null) {
                byte[] fontBytes = is.readAllBytes();
                chineseBaseFont = BaseFont.createFont("NotoSansSC-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
                log.info("Successfully loaded PDF Chinese font from classpath: /fonts/NotoSansSC-Regular.ttf");
                return chineseBaseFont;
            } else {
                log.warn("Classpath resource /fonts/NotoSansSC-Regular.ttf not found");
            }
        } catch (Exception e) {
            log.warn("Failed to load font from classpath /fonts/NotoSansSC-Regular.ttf: {}", e.getMessage());
        }

        // 2. 备用回退：系统字体
        String[] candidatePaths = {
            "C:/Windows/Fonts/msyh.ttc,0",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsun.ttc,0",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0"
        };
        for (String path : candidatePaths) {
            try {
                String checkFile = path.contains(",") ? path.substring(0, path.indexOf(",")) : path;
                if (new File(checkFile).exists()) {
                    chineseBaseFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    log.info("Successfully loaded fallback PDF Chinese font from system: {}", path);
                    return chineseBaseFont;
                }
            } catch (Exception e) {
                log.warn("Failed to load fallback font from {}: {}", path, e.getMessage());
            }
        }

        // 3. 兜底内置 STSong-Light
        try {
            chineseBaseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            log.info("Loaded default STSong-Light font");
            return chineseBaseFont;
        } catch (Exception e) {
            log.warn("STSong-Light not available, fallback to Helvetica: {}", e.getMessage());
            try {
                chineseBaseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to initialize any PDF font", ex);
            }
        }
        return chineseBaseFont;
    }

    public static Font getTitleFont() {
        return new Font(getChineseBaseFont(), 16, Font.BOLD);
    }

    public static Font getHeaderFont() {
        return new Font(getChineseBaseFont(), 12, Font.BOLD);
    }

    public static Font getBodyFont() {
        return new Font(getChineseBaseFont(), 10, Font.NORMAL);
    }

    public static Font getSmallFont() {
        return new Font(getChineseBaseFont(), 8, Font.NORMAL);
    }
}
