package com.promptportal.media;

import com.promptportal.config.AppProperties;
import com.promptportal.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class ImageValidationService {

    private final AppProperties props;

    public ImageValidationService(AppProperties props) {
        this.props = props;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("Empty file.", "EMPTY_FILE");
        }
        long size = file.getSize();
        if (size <= 0) {
            throw ApiException.validation("Empty file.", "EMPTY_FILE");
        }
        if (size > props.getStorage().getMaxUploadBytes()) {
            throw ApiException.fileTooLarge("File exceeds the maximum allowed size of 2 MB.");
        }

        String original = file.getOriginalFilename();
        if (!ImageSignatureDetector.isSafeOriginalFilename(original)) {
            throw ApiException.validation("Invalid filename.");
        }
        String ext = ImageSignatureDetector.extensionFromFilename(original);
        if (!ImageSignatureDetector.ALLOWED_EXT.contains(ext)) {
            throw ApiException.unsupportedMedia(
                    "Only PNG, JPEG, WebP, and GIF images up to 2 MB are allowed.");
        }
        String extMime = ImageSignatureDetector.mimeFamilyFromExt(ext);

        String clientMime = ImageSignatureDetector.normalizeClientMime(file.getContentType());
        if (clientMime != null && !ImageSignatureDetector.ALLOWED_MIME.contains(clientMime)) {
            throw ApiException.unsupportedMedia(
                    "Only PNG, JPEG, WebP, and GIF images up to 2 MB are allowed.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("Could not read upload.");
        }
        if (bytes.length > props.getStorage().getMaxUploadBytes()) {
            throw ApiException.fileTooLarge("File exceeds the maximum allowed size of 2 MB.");
        }

        String detected = ImageSignatureDetector.detect(bytes);
        if (detected == null) {
            throw ApiException.unsupportedMedia(
                    "Only PNG, JPEG, WebP, and GIF images up to 2 MB are allowed.");
        }
        if (!detected.equals(extMime)) {
            throw ApiException.unsupportedMedia("File extension does not match image content.");
        }
        if (clientMime != null && !clientMime.equals(detected)) {
            throw ApiException.unsupportedMedia("Content-Type does not match image content.");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new ApiException("INVALID_IMAGE_CONTENT",
                    "File is not a valid image.",
                    org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            throw new ApiException("INVALID_IMAGE_CONTENT",
                    "File is not a valid image.",
                    org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        return new ValidatedImage(
                bytes,
                detected,
                ImageSignatureDetector.extensionForMime(detected),
                image.getWidth(),
                image.getHeight(),
                sha256(bytes),
                original
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public record ValidatedImage(
            byte[] bytes,
            String mimeType,
            String extension,
            int width,
            int height,
            String checksum,
            String originalFileName
    ) {
    }
}
