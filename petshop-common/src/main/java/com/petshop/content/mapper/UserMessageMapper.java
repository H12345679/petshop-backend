package com.petshop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.content.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {

    /**
     * 将该用户已有关联记录但为未读状态的消息批量更新为已读
     */
    void updateAllUnreadToRead(@Param("userId") Long userId);

    /**
     * 获取未阅读过的广播消息ID列表
     */
    List<Long> selectUnreadBroadcastMessageIds(@Param("userId") Long userId);
}
