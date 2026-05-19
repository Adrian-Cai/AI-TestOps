package com.example.aitestops.common.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonUtilTest {

    @Test
    void toJsonShouldSerializeValue() {
        String json = JsonUtil.toJson(new ObjectMapper(), Map.of("status", "ok"));

        assertThat(json).contains("\"status\":\"ok\"");
    }

    @Test
    void toJsonShouldWrapJacksonFailure() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(UnserializableValue.class, new JsonSerializer<UnserializableValue>() {
            @Override
            public void serialize(UnserializableValue value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                throw new IOException("boom");
            }
        });
        objectMapper.registerModule(module);

        assertThatThrownBy(() -> JsonUtil.toJson(objectMapper, new UnserializableValue()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JSON 序列化失败")
                .hasRootCauseInstanceOf(IOException.class);
    }

    private static class UnserializableValue {
    }
}
