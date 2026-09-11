package com.minex.backend.service;

import com.minex.backend.config.AppProps;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.springframework.stereotype.Service;

/** Thin wrapper over the MinIO S3-compatible object store (PRD §3). */
@Service
public class StorageService {
    private final MinioClient minio;
    private final AppProps props;

    public StorageService(MinioClient minio, AppProps props) {
        this.minio = minio;
        this.props = props;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            String bucket = props.getMinio().getBucket();
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            // MinIO may be down during unit tests / early boot; fail lazily on first real use.
            // Logged at warning via stdout to avoid adding a logging facade dependency here.
            System.out.println("[WARN] MinIO bucket check failed (will retry on use): " + ex.getMessage());
        }
    }

    public void put(String key, byte[] bytes, String contentType) {
        try {
            ensureBucket(); // self-heal if the bucket check at startup raced MinIO
            String bucket = props.getMinio().getBucket();
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                minio.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(in, bytes.length, -1)
                        .contentType(contentType)
                        .build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Object storage write failed for " + key, ex);
        }
    }

    public byte[] get(String key) {
        try {
            String bucket = props.getMinio().getBucket();
            try (InputStream in = minio.getObject(
                         GetObjectArgs.builder().bucket(bucket).object(key).build());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Object storage read failed for " + key, ex);
        }
    }
}
