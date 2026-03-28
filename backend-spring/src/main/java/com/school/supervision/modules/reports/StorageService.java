package com.school.supervision.modules.reports;

public interface StorageService {
    String upload(String key, byte[] content, String contentType);
}
