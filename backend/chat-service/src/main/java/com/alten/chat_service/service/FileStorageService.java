package com.alten.chat_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Stores ticket attachments (screenshots, images) on local disk under
 * {app.upload.dir}/tickets/{ticketId}/{uuid}.{ext}, and serves them back
 * via /uploads/** (see WebConfig).
 *
 * Kept intentionally simple (local filesystem) — sufficient for a PFE demo.
 * Swapping to S3/MinIO later only requires changing this one class.
 */
@Service
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    /**
     * Saves the given file under a folder dedicated to the ticket and
     * returns the relative URL to access it (e.g. "/uploads/tickets/12/abc.png").
     *
     * @return null if file is null/empty (nothing to store)
     */
    public String store(MultipartFile file, Long ticketId) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Type de fichier non autorisé. Formats acceptés : PNG, JPEG, GIF, WEBP.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Fichier trop volumineux (5 Mo max).");
        }

        String original = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String filename = UUID.randomUUID() + extension;

        Path ticketDir = Paths.get(uploadDir, "tickets", String.valueOf(ticketId)).toAbsolutePath().normalize();
        Files.createDirectories(ticketDir);

        Path target = ticketDir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/tickets/" + ticketId + "/" + filename;
    }
}