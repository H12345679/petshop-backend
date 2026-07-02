package com.petshop.recommend;

import com.petshop.recommend.service.impl.CollaborativeFilteringServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RecommendServiceTest {

    @InjectMocks
    private CollaborativeFilteringServiceImpl cfService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testRunBatch_EmptyData() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.emptyList());
        
        cfService.runCollaborativeFilteringBatch();
        
        verify(jdbcTemplate).queryForList("SELECT user_id, product_id, score FROM user_item_score");
    }
}
