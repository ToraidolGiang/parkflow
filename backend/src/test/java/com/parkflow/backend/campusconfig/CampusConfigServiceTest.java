package com.parkflow.backend.campusconfig;

import com.parkflow.backend.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampusConfigServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode seed() throws Exception {
        try(var input = new ClassPathResource("simulator/campus_config.json").getInputStream()) {
            return (ObjectNode)mapper.readTree(input);
        }
    }
    @Test void packagedSeedIsValidAndContainsBuildingsAndRoutes() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var config = seed();
        assertTrue(new CampusConfigService(jdbc,mapper).seedIfAbsent(config));
        verify(jdbc).update(eq("INSERT INTO campus_config(id,body) VALUES (1,?::jsonb)"), eq(mapper.writeValueAsString(config)));
        assertFalse(config.path("buildings").isEmpty());
        assertFalse(config.path("routes").isEmpty());
    }
    @Test void existingConfigIsNeverOverwritten() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT id FROM campus_config WHERE id = 1",Integer.class)).thenReturn(List.of(1));
        assertFalse(new CampusConfigService(jdbc,mapper).seedIfAbsent(seed()));
        verify(jdbc,never()).update(anyString(), any(Object[].class));
    }
    @Test void invalidReferencesFailBeforeWriting() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var config = seed();
        ((ObjectNode)config.path("routes").get(0)).put("lot","MISSING");
        assertThrows(ApiException.class, () -> new CampusConfigService(jdbc,mapper).seedIfAbsent(config));
        verifyNoInteractions(jdbc);
    }
    @Test void readsStoredConfigAndAddsCountsWithoutChangingStoredJson() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        String json = mapper.writeValueAsString(seed());
        when(jdbc.queryForList("SELECT body::text FROM campus_config WHERE id=1",String.class)).thenReturn(List.of(json));
        var service = new CampusConfigService(jdbc,mapper);
        assertEquals(mapper.readTree(json), service.current());
        assertEquals(0, service.currentWithCounts().path("lots").get(0).path("count").asInt());
        assertFalse(service.current().path("lots").get(0).has("count"));
    }
}
