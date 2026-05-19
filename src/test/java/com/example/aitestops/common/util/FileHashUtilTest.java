package com.example.aitestops.common.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileHashUtilTest {

    @Test
    void sha256ShouldReturnKnownDigestAndConsumeInputStream() throws Exception {
        String digest = FileHashUtil.sha256(new ByteArrayInputStream("ai-testops".getBytes(StandardCharsets.UTF_8)));

        assertThat(digest).isEqualTo("8e0ac8652307a7bc906a14e2e610f00f6754b14cd0f1233ec3b0fcc14788ffb4");
    }

    @Test
    void sha256ShouldReturnEmptyStreamDigest() throws Exception {
        String digest = FileHashUtil.sha256(new ByteArrayInputStream(new byte[0]));

        assertThat(digest).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
