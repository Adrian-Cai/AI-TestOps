package com.example.aitestops.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文件 Hash 工具，统一使用 SHA-256 进行去重标识。
 */
public final class FileHashUtil {

    private static final int BUFFER_SIZE = 8192;

    private FileHashUtil() {
    }

    public static String sha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (digestInputStream.read(buffer) != -1) {
                    // DigestInputStream 会在读取时自动更新摘要。
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }
}
