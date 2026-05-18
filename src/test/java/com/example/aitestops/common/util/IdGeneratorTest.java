package com.example.aitestops.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    private static final Pattern ID_PATTERN = Pattern.compile("^(DOC|CHUNK|PARSE)_\\d{8}_[0-9a-f]{32}$");

    @Test
    void generatedIdsShouldUseUuidSuffixAndStayWithinDatabaseLength() {
        Set<String> ids = new HashSet<>();

        IntStream.range(0, 5_000).forEach(index -> {
            assertGeneratedId(ids, IdGenerator.documentId());
            assertGeneratedId(ids, IdGenerator.chunkId());
            assertGeneratedId(ids, IdGenerator.parseResultId());
        });

        assertThat(ids).hasSize(15_000);
    }

    private void assertGeneratedId(Set<String> ids, String id) {
        assertThat(id.length()).isLessThanOrEqualTo(64);
        assertThat(id).matches(ID_PATTERN);
        assertThat(ids.add(id)).isTrue();
    }
}
