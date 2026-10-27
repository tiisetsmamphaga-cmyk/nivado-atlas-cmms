package com.grash.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.exception.CustomException;
import com.grash.model.File;
import com.grash.utils.Helper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/** Private Supabase bucket accessed only by the Atlas backend. */
@Service
public class SupabaseStorageService implements StorageService {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper json;
    private final CacheService cacheService;
    private final String base;
    private final String key;
    private final String bucket;

    public SupabaseStorageService(ObjectMapper json, CacheService cacheService,
            @Value("${SUPABASE_URL:}") String url,
            @Value("${SUPABASE_SECRET_KEY:}") String key,
            @Value("${SUPABASE_STORAGE_BUCKET:atlas-files}") String bucket) {
        this.json = json;
        this.cacheService = cacheService;
        this.base = url.replaceAll("/+$", "") + "/storage/v1";
        this.key = key;
        this.bucket = bucket;
    }

    private static String encode(String path) {
        return Arrays.stream(path.split("/", -1)).map(part ->
                URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private String objectPath(String prefix, String path) {
        if (path == null || path.isBlank() || path.startsWith("/") ||
                Arrays.asList(path.split("/")).contains("..")) {
            throw new CustomException("Invalid storage path", HttpStatus.BAD_REQUEST);
        }
        return prefix + "/" + encode(bucket) + "/" + encode(path);
    }

    private HttpResponse<byte[]> request(String method, String path, byte[] body, String contentType) {
        if (key.isBlank() || !base.startsWith("https://")) {
            throw new CustomException("Supabase storage is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(60)).header("apikey", key)
                    .header("Content-Type", contentType == null ? "application/octet-stream" : contentType);
            // Legacy service_role JWTs require Authorization; modern secret keys use apikey.
            if (!key.startsWith("sb_secret_")) request.header("Authorization", "Bearer " + key);
            if (method.equals("POST") && path.startsWith("/object/")) request.header("x-upsert", "true");
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                    HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException("Storage request interrupted", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (IOException e) {
            throw new CustomException("Storage is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private byte[] success(HttpResponse<byte[]> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Never include upstream bodies, signed URLs or credentials in error responses.
            throw new CustomException("Storage request failed (HTTP " + response.statusCode() + ")",
                    HttpStatus.BAD_GATEWAY);
        }
        return response.body();
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) throw new CustomException("Uploaded file is empty", HttpStatus.BAD_REQUEST);
        String path = Helper.generateUniqueFilePath(file.getOriginalFilename(), folder);
        try {
            uploadAt(file.getBytes(), path, file.getContentType());
            return path;
        } catch (IOException e) {
            throw new CustomException("Cannot read uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public String upload(byte[] data, String fileName, String folder) {
        String path = Helper.generateUniqueFilePath(fileName, folder);
        uploadAt(data, path, "application/octet-stream");
        return path;
    }

    @Override
    public void uploadAt(byte[] data, String filePath, String contentType) {
        if (data == null || data.length == 0) throw new CustomException("Uploaded file is empty", HttpStatus.BAD_REQUEST);
        success(request("POST", objectPath("/object", filePath), data, contentType));
    }

    @Override
    public byte[] download(String filePath) {
        return success(request("GET", objectPath("/object/authenticated", filePath), null, null));
    }

    @Override
    public byte[] download(File file) { return download(file.getPath()); }

    @Override
    public boolean exists(String filePath) {
        HttpResponse<byte[]> response = request("HEAD", objectPath("/object", filePath), null, null);
        if (response.statusCode() == 404) return false;
        success(response);
        return true;
    }

    @Override
    public String generateSignedUrl(File file, long expirationMinutes) {
        return cacheService.getCachedOrGenerateSignedUrl(file, expirationMinutes,
                () -> generateSignedUrl(file.getPath(), expirationMinutes));
    }

    @Override
    public String generateSignedUrl(String filePath, long expirationMinutes) {
        if (expirationMinutes < 1 || expirationMinutes > 10080) {
            throw new CustomException("Invalid signed URL lifetime", HttpStatus.BAD_REQUEST);
        }
        try {
            byte[] body = json.writeValueAsBytes(Map.of("expiresIn", Math.multiplyExact(expirationMinutes, 60)));
            JsonNode result = json.readTree(success(request("POST", objectPath("/object/sign", filePath), body,
                    "application/json")));
            String signed = result.path("signedURL").asText();
            if (!signed.startsWith("/object/sign/")) throw new IOException("Invalid signed URL response");
            return base + signed;
        } catch (IOException e) {
            throw new CustomException("Cannot generate file download URL", HttpStatus.BAD_GATEWAY);
        }
    }
}
