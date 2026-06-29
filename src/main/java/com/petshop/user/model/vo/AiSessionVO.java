package com.petshop.user.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** AI 历史会话列表项 */
@Data
public class AiSessionVO {
    private String sessionId;
    private String title;
    private LocalDateTime createTime;
}
