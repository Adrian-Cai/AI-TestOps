package com.example.aitestops.diff.risk;

import com.example.aitestops.diff.enums.DiffFileRoleEnum;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiffFileClassifierTest {

    private final DiffFileClassifier classifier = new DiffFileClassifier();

    @ParameterizedTest
    @CsvSource({
            "src/main/java/com/example/aitestops/order/OrderController.java,CONTROLLER",
            "src/main/java/com/example/aitestops/order/service/OrderServiceImpl.java,SERVICE",
            "src/main/java/com/example/aitestops/order/mapper/OrderMapper.java,DAO",
            "docs/sql/order_schema.sql,SQL",
            "src/main/resources/application.yml,CONFIG",
            "src/main/java/com/example/aitestops/security/JwtAuthFilter.java,AUTH",
            "src/main/java/com/example/aitestops/message/OrderMqConsumer.java,MQ",
            "src/main/java/com/example/aitestops/job/DailyScheduler.java,JOB",
            "src/test/java/com/example/aitestops/order/OrderServiceTest.java,TEST",
            "README.md,OTHER"
    })
    void classifyShouldMapPathToFileRole(String path, DiffFileRoleEnum expectedRole) {
        assertThat(classifier.classify(path)).isEqualTo(expectedRole);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,OTHER",
            "'   ',OTHER",
            "frontend/src/App.test.ts,TEST",
            "frontend/src/api.spec.js,TEST"
    }, nullValues = "NULL")
    void classifyShouldHandleBlankAndFrontendTestFiles(String path, DiffFileRoleEnum expectedRole) {
        assertThat(classifier.classify(path)).isEqualTo(expectedRole);
    }

    @ParameterizedTest
    @CsvSource({
            "src/test/java/com/example/DemoTest.java,true",
            "src/main/java/com/example/DemoService.java,false"
    })
    void isTestFileShouldReflectClassifierResult(String path, boolean expected) {
        assertThat(classifier.isTestFile(path)).isEqualTo(expected);
    }
}
