package com.school.supervision.modules.reports;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Primary
public class LocalStorageService implements StorageService {
    @Value("${storage.local.root-path:uploads}")
    private String rootPath;

    @Override
    public String upload(String key, byte[] content, String contentType) {
        try {
            Path file = Path.of(rootPath, key);
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            return file.toUri().toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to store file", e);
        }
    }
}
