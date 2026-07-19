package com.promptportal.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageSignatureDetectorTest {

    @Test
    void detectsPng() {
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0, 0, 0, 0, 0
        };
        assertEquals("image/png", ImageSignatureDetector.detect(png));
    }

    @Test
    void detectsJpeg() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertEquals("image/jpeg", ImageSignatureDetector.detect(jpeg));
    }

    @Test
    void rejectsHtmlAsPng() {
        byte[] html = "<html>".getBytes();
        byte[] padded = new byte[16];
        System.arraycopy(html, 0, padded, 0, html.length);
        assertNull(ImageSignatureDetector.detect(padded));
    }

    @Test
    void rejectsPathTraversalFilename() {
        assertFalse(ImageSignatureDetector.isSafeOriginalFilename("../x.png"));
        assertFalse(ImageSignatureDetector.isSafeOriginalFilename("a/b.png"));
        assertTrue(ImageSignatureDetector.isSafeOriginalFilename("photo.png"));
    }
}
