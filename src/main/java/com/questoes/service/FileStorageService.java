package com.questoes.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            log.error("Erro ao criar diretório de uploads", e);
        }
    }

    public String salvarImagem(MultipartFile file) throws IOException {
        String extension = getExtensao(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + extension;
        Path targetPath = Paths.get(uploadDir).resolve(fileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + fileName;
    }

    public Path getUploadPath() {
        return Paths.get(uploadDir);
    }

    private String getExtensao(String filename) {
        if (filename == null) return ".png";
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx).toLowerCase() : ".png";
    }
}
