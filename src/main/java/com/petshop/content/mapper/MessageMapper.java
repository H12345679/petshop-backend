package com.petshop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.content.entity.Message;
import com.petshop.content.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 查询我的消息列表（含全站广播及定向发给我的），并关联已读状态
     * @param page 分页参数
     * @param userId 当前用户ID
     * @return 包含已读状态的消息分页数据
     */
    Page<MessageVO> selectMyMessages(Page<MessageVO> page, @Param("userId") Long userId);
}
