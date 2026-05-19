package com.example.aitestops.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameUtilTest {

    @Test
    void cleanOriginalFilenameShouldRemoveClientSidePath() {
        assertThat(FileNameUtil.cleanOriginalFilename("C:/Users/demo/Desktop/需求文档.PDF"))
                .isEqualTo("需求文档.PDF");
        assertThat(FileNameUtil.cleanOriginalFilename("../unsafe/../../case.xlsx"))
                .isEqualTo("case.xlsx");
    }

    @Test
    void cleanOriginalFilenameShouldHandleNullAsEmptyName() {
        assertThat(FileNameUtil.cleanOriginalFilename(null)).isEmpty();
    }

    @Test
    void extensionShouldReturnLowercaseSuffixOnlyWhenPresent() {
        assertThat(FileNameUtil.extension("需求文档.PDF")).isEqualTo("pdf");
        assertThat(FileNameUtil.extension("archive.tar.gz")).isEqualTo("gz");
        assertThat(FileNameUtil.extension("README")).isEmpty();
        assertThat(FileNameUtil.extension("file.")).isEmpty();
        assertThat(FileNameUtil.extension(" ")).isEmpty();
    }
}
