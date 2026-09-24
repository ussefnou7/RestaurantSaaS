package com.smart.restaurant_saas.media.storage;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ExternalServiceException;
import com.smart.restaurant_saas.media.MediaErrorCode;
import com.smart.restaurant_saas.media.MediaProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * V1 storage: a directory tree whose relative paths are exactly the storage keys.
 *
 * <p>Keeping the key and the relative path identical is what makes the eventual move to an
 * S3-compatible bucket a tree copy rather than a migration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileSystemStorageService implements StorageService {

    private final MediaProperties properties;

    private Path root;

    @PostConstruct
    void createRoot() {
        root = Path.of(properties.getStorageRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Media storage root is not creatable: " + root
                            + ". It must be an absolute path outside the deployed war.", ex);
        }
        log.info("Media storage root: {}", root);
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Write beside the target and move, so a crash mid-write cannot leave a truncated
            // object readable under a key the database already believes in.
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.deleteIfExists(temp);
                throw ex;
            }
        } catch (IOException ex) {
            throw storageFailure("write", key, ex);
        }
    }

    @Override
    public Resource get(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new ExternalServiceException(MediaErrorCode.MEDIA_STORAGE_FAILURE,
                    "Storage key has no object: " + key,
                    ErrorParams.of("storageKey", key));
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String key) {
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
            // The uuid directory exists only to hold one file's variants, so once the last one is
            // gone it is litter. deleteIfEmpty, never recursive -- a recursive delete here is one
            // key-building bug away from removing a tenant.
            deleteIfEmpty(target.getParent());
        } catch (IOException ex) {
            throw storageFailure("delete", key, ex);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public List<String> list(String prefix) {
        Path start = resolve(prefix);
        if (!Files.isDirectory(start)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(start)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException | UncheckedIOException ex) {
            throw storageFailure("list", prefix, ex);
        }
    }

    /**
     * Resolves a key under the root and proves it stayed there.
     *
     * <p>Keys are built by {@link StorageKeys} and never contain user input, so traversal is not
     * reachable today. The guard is here because the moment a key is ever derived from a request
     * parameter, the absence of this check is a read of any file the JVM user can open — and that
     * change will not come with a reminder.
     */
    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ExternalServiceException(MediaErrorCode.MEDIA_STORAGE_FAILURE,
                    "Storage key escapes the storage root: " + key,
                    ErrorParams.of("storageKey", key));
        }
        return resolved;
    }

    private void deleteIfEmpty(Path directory) throws IOException {
        if (directory == null || directory.equals(root) || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(directory);
            }
        }
    }

    private ExternalServiceException storageFailure(String operation, String key, Exception cause) {
        return new ExternalServiceException(MediaErrorCode.MEDIA_STORAGE_FAILURE,
                "Media storage %s failed for key %s".formatted(operation, key),
                ErrorParams.of("operation", operation, "storageKey", key), cause);
    }
}
