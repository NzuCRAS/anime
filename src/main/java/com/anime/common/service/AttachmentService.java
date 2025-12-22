package com.anime.common.service;

import com.anime.common.entity.attachment.Attachment;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.mapper.attachment.AttachmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AttachmentCommonService - 支持 storagePath 前缀、文件名为 UUID、
 * metadata 当前不写入（保持为 null），如未来需要再扩展 metadata 写入函数
 */
@Slf4j
@Service
public class AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final HttpClient httpClient;

    @Value("${storage.bucket}")
    private String bucket;

    @Value("${storage.cdn-domain:}")
    private String cdnDomain;

    // presign validity (minutes)
    private static final long PRESIGN_MINUTES = 15L;
    // server fetch max size (example 20MB)
    private static final long MAX_SERVER_FETCH_BYTES = 20L * 1024L * 1024L;

    // 简单的 content-type -> 扩展名 映射（常用）
    private static final Map<String, String> MIME_TO_EXT;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("image/jpeg", ".jpg");
        m.put("image/png", ".png");
        m.put("image/gif", ".gif");
        m.put("image/webp", ".webp");
        m.put("image/svg+xml", ".svg");
        m.put("video/mp4", ".mp4");
        m.put("video/webm", ".webm");
        m.put("audio/mpeg", ".mp3");
        m.put("audio/ogg", ".ogg");
        m.put("application/pdf", ".pdf");
        MIME_TO_EXT = Collections.unmodifiableMap(m);
    }

    public AttachmentService(AttachmentMapper attachmentMapper,
                             S3Client s3Client,
                             S3Presigner s3Presigner) {
        this.attachmentMapper = attachmentMapper;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // --------------------------
    // Helper: sanitize storagePath & generate storage key
    // --------------------------

    /**
     * 清洗后端传递的文件存储路径
     * @param storagePath
     * @return
     */
    private String sanitizeStoragePath(String storagePath) {
        if (storagePath == null) return "";
        String p = storagePath.replace('\\', '/').replaceAll("/+", "/").replaceAll("^/+", "").replaceAll("/+$", "");
        if (p.isBlank()) return "";
        String safe = Stream.of(p.split("/"))
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("/"));
        return safe;
    }

    /**
     * 从后端传输的文件拓展名得到文件类型
     * @param contentType
     * @return
     */
    private String deriveExtensionFromContentType(String contentType) {
        if (contentType == null) return "";
        String t = contentType.split(";")[0].trim().toLowerCase();
        return MIME_TO_EXT.getOrDefault(t, "");
    }

    /**
     * 创建文件存储路径
     * @param storagePath
     * @param contentType
     * @return
     */
    private String generateStorageKey(String storagePath, String contentType) {
        String safePath = sanitizeStoragePath(storagePath);
        String uuid = UUID.randomUUID().toString();
        String shortHash = uuid.replace("-", "").substring(0, 2);
        String ext = deriveExtensionFromContentType(contentType);
        if (safePath.isBlank()) {
            return String.format("attachments/%s/%s%s", shortHash, uuid, ext);
        } else {
            return String.format("attachments/%s/%s/%s%s", safePath, shortHash, uuid, ext);
        }
    }

    // --------------------------
    // 前端直传相关（storagePath + contentType）
    // 实现顺序：先生成 presign（若失败，不写 DB），presign 成功后插入 DB 并返回 DTO
    // metadata 不再写入数据库（保持 null）
    // --------------------------

    /**
     * 在文件系统中创建文件夹并创建一个用于前端PUT的url
     * @param storagePath
     * @param contentType
     * @param uploadedBy
     * @param originalFilename
     * @return
     */
    @Transactional
    public PresignResponseDTO preCreateAndPresign(String storagePath, String contentType, Long uploadedBy, String originalFilename, Integer width, Integer height) {
        // 1) 计算 storageKey（但不直接写 DB）
        String storageKey = generateStorageKey(storagePath, contentType);

        // 2) 先构造 PutObjectRequest 并 presign（若 presign 失败，不会在 DB 留下记录）
        PutObjectRequest por = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .putObjectRequest(por)
                .signatureDuration(Duration.ofMinutes(PRESIGN_MINUTES))
                .build();

        PresignedPutObjectRequest presigned;
        try {
            presigned = s3Presigner.presignPutObject(presignRequest);
        } catch (Exception ex) {
            log.error("presignPutObject failed for key {} contentType={}", storageKey, contentType, ex);
            throw new RuntimeException("failed to presign upload URL: " + ex.getMessage(), ex);
        }

        // 3) presign 成功后再插入 DB（保证 DB 与 presign 一致性）
        Attachment a = new Attachment();
        a.setProvider("s3");
        a.setBucket(bucket);
        a.setStorageKey(storageKey);
        a.setUrl(null);
        a.setChecksum(null);
        a.setMimeType(contentType);
        a.setSizeBytes(null);
        a.setWidth(width);
        a.setHeight(height);
        a.setUploadedBy(uploadedBy);
        a.setStatus("uploading");
        a.setMetadata(null);

        a.setCreatedAt(LocalDateTime.now());

        try {
            attachmentMapper.insert(a);
        } catch (Exception ex) {
            log.error("attachment insert failed for key {} : {}", storageKey, ex.getMessage(), ex);
            throw ex;
        }

        Map<String, String> headers = presigned.httpRequest().headers().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(",", e.getValue())));
        String publicUrl = (cdnDomain != null && !cdnDomain.isBlank())
                ? (cdnDomain.endsWith("/") ? cdnDomain + storageKey : cdnDomain + "/" + storageKey)
                : null;

        return new PresignResponseDTO(a.getId(), presigned.url().toString(), headers, publicUrl);
    }

    @Transactional
    public PresignResponseDTO preCreateAndPresign(String storagePath, String contentType, Long uploadedBy) {
        return preCreateAndPresign(storagePath, contentType, uploadedBy, null, null, null);
    }

    // --------------------------
    // completeUpload / createFromUrl / uploadFromStream / delete / presigned-get
    // --------------------------

    /**
     * 完成文件传输后,使用完成传输的文件的attachmentId,进行数据库中的最终确认,把status字段设置为available
     * @param attachmentId
     * @return
     */
    @Transactional
    public Attachment completeUpload(Long attachmentId) {
        if (attachmentId == null) throw new IllegalArgumentException("attachmentId required");

        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) throw new IllegalArgumentException("attachment not found: " + attachmentId);

        HeadObjectRequest headReq = HeadObjectRequest.builder()
                .bucket(a.getBucket())
                .key(a.getStorageKey())
                .build();
        HeadObjectResponse resp = s3Client.headObject(headReq);

        a.setChecksum(resp.eTag());
        a.setMimeType(resp.contentType());
        a.setSizeBytes(resp.contentLength());
        a.setStatus("available");
        if (cdnDomain != null && !cdnDomain.isBlank()) {
            a.setUrl(cdnDomain.endsWith("/") ? cdnDomain + a.getStorageKey() : cdnDomain + "/" + a.getStorageKey());
        }
        attachmentMapper.updateById(a);
        return a;
    }

    public Attachment createFromUrl(String storagePath, String url, Long createdBy) throws Exception {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("invalid url");
        }

        HttpRequest headReq = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<Void> headResp = httpClient.send(headReq, HttpResponse.BodyHandlers.discarding());
        long contentLength = headResp.headers().firstValueAsLong("content-length").orElse(-1L);
        if (contentLength > MAX_SERVER_FETCH_BYTES) {
            throw new IllegalArgumentException("file too large");
        }

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<InputStream> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofInputStream());
        if (getResp.statusCode() >= 400) {
            throw new IllegalStateException("failed fetching url: " + getResp.statusCode());
        }

        String contentType = getResp.headers().firstValue("content-type").orElse("application/octet-stream");
        InputStream is = getResp.body();

        String storageKey = generateStorageKey(storagePath, contentType);

        PutObjectRequest por = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .build();

        long len = contentLength > 0 ? contentLength : is.available(); // fallback
        s3Client.putObject(por, RequestBody.fromInputStream(is, len));

        Attachment a = new Attachment();
        a.setProvider("s3");
        a.setBucket(bucket);
        a.setStorageKey(storageKey);
        a.setUrl((cdnDomain != null && !cdnDomain.isBlank()) ? (cdnDomain.endsWith("/") ? cdnDomain + storageKey : cdnDomain + "/" + storageKey) : null);
        a.setChecksum(null);
        a.setMimeType(contentType);
        a.setSizeBytes(len);
        a.setUploadedBy(createdBy);
        a.setStatus("available");
        a.setCreatedAt(LocalDateTime.now());
        a.setMetadata(null);

        attachmentMapper.insert(a);
        return a;
    }

    public Attachment createFromUrl(String url, Long createdBy) throws Exception {
        return createFromUrl(null, url, createdBy);
    }

    public Attachment uploadFromStream(String storagePath, InputStream data, String contentType, Long createdBy, long contentLength, String originalFilename) {
        String storageKey = generateStorageKey(storagePath, contentType);

        PutObjectRequest por = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .build();

        s3Client.putObject(por, RequestBody.fromInputStream(data, contentLength));

        Attachment a = new Attachment();
        a.setProvider("s3");
        a.setBucket(bucket);
        a.setStorageKey(storageKey);
        a.setUrl((cdnDomain != null && !cdnDomain.isBlank()) ? (cdnDomain.endsWith("/") ? cdnDomain + storageKey : cdnDomain + "/" + storageKey) : null);
        a.setMimeType(contentType);
        a.setSizeBytes(contentLength);
        a.setUploadedBy(createdBy);
        a.setStatus("available");
        a.setCreatedAt(LocalDateTime.now());
        a.setMetadata(null);

        attachmentMapper.insert(a);
        return a;
    }

    public Attachment uploadFromStream(InputStream data, String contentType, Long createdBy, long contentLength) {
        return uploadFromStream(null, data, contentType, createdBy, contentLength, null);
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, boolean soft) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) return;
        if (soft) {
            a.setStatus("deleted");
            attachmentMapper.updateById(a);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(a.getBucket()).key(a.getStorageKey()).build());
            attachmentMapper.deleteById(attachmentId);
        } catch (S3Exception e) {
            log.warn("s3 delete failed for {}: {}", attachmentId, e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * 创建一个能够GET的url,指定attachmentId和该url可用时间
     * @param attachmentId
     * @param expirySeconds
     * @return
     */
    public String generatePresignedGetUrl(Long attachmentId, long expirySeconds) {
        if (attachmentId == null) return null;
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) throw new IllegalArgumentException("attachment not found: " + attachmentId);

        if (cdnDomain != null && !cdnDomain.isBlank()) {
            return cdnDomain.endsWith("/") ? cdnDomain + a.getStorageKey() : cdnDomain + "/" + a.getStorageKey();
        }

        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(a.getBucket())
                .key(a.getStorageKey())
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(60, expirySeconds)))
                .getObjectRequest(getReq)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    public Attachment getAttachmentById(Long attachmentId) {
        if (attachmentId == null) return null;
        return attachmentMapper.selectById(attachmentId);
    }
}