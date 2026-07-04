package com.petshop.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.content.dto.MessageSendDTO;
import com.petshop.content.entity.Message;
import com.petshop.content.entity.UserMessage;
import com.petshop.content.mapper.MessageMapper;
import com.petshop.content.mapper.UserMessageMapper;
import com.petshop.content.service.MessageService;
import com.petshop.content.vo.MessageVO;
import com.petshop.security.UserContext;
import com.petshop.security.OwnershipChecker;
import com.petshop.shop.entity.ShopCustomer;
import com.petshop.shop.mapper.ShopCustomerMapper;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ShopMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Autowired
    private UserMessageMapper userMessageMapper;
    @Autowired
    private OwnershipChecker ownershipChecker;
    @Autowired
    private ShopCustomerMapper shopCustomerMapper;
    @Autowired
    private ShopMapper shopMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(MessageSendDTO dto) {
        if ("MERCHANT".equals(UserContext.getRole())) {
            if (dto.getType() == 1) {
                throw new BusinessException(403, "商家无法发送系统通知");
            }
            List<Long> shopIds = ownershipChecker.myShopIds();
            if (shopIds == null || shopIds.isEmpty()) {
                throw new BusinessException(403, "您尚未绑定任何店铺，无法发送消息");
            }
            LambdaQueryWrapper<ShopCustomer> qw = new LambdaQueryWrapper<>();
            qw.in(ShopCustomer::getShopId, shopIds);
            qw.select(ShopCustomer::getUserId);
            List<Long> customerIds = shopCustomerMapper.selectObjs(qw).stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .distinct()
                    .collect(Collectors.toList());

            if (dto.getScope() == 1) {
                // 商家广播：转为定向发送给所有本店客户
                if (customerIds.isEmpty()) {
                    throw new BusinessException(400, "您的店铺暂无历史购买客户，无法广播");
                }
                dto.setScope(2);
                dto.setTargetUserIds(customerIds);
            } else if (dto.getScope() == 2) {
                // 商家定向发送：必须是本店客户
                List<Long> targets = dto.getTargetUserIds();
                if (targets == null || targets.isEmpty()) {
                    throw new BusinessException(400, "定向发送必须指定目标用户列表");
                }
                for (Long t : targets) {
                    if (!customerIds.contains(t)) {
                        throw new BusinessException(403, "只能向购买过您本店商品的用户发送消息");
                    }
                }
            }

            // 自动追加店铺署名与签名
            Shop shop = shopMapper.selectById(shopIds.get(0));
            String shopName = (shop != null && shop.getName() != null && !shop.getName().trim().isEmpty()) ? shop.getName() : "品牌商家";
            String title = dto.getTitle();
            if (title != null && !title.startsWith("【")) {
                dto.setTitle("【" + shopName + "】" + title);
            }
            String content = dto.getContent();
            if (content != null && !content.contains("—— 来自店铺：")) {
                dto.setContent(content + "\n\n—— 来自店铺：「" + shopName + "」");
            }
        }
        
        Message message = new Message();
        message.setTitle(dto.getTitle());
        message.setContent(dto.getContent());
        message.setType(dto.getType());
        message.setScope(dto.getScope());
        this.save(message);

        // 如果是定向发送，直接给这些用户插入未读记录
        if (dto.getScope() == 2) {
            List<Long> targetIds = dto.getTargetUserIds();
            if (targetIds == null || targetIds.isEmpty()) {
                throw new BusinessException(400, "定向发送必须指定目标用户列表");
            }
            // 简单循环插入，如果量大应使用批量插入 (insertBatchSomeColumn)
            for (Long targetUserId : targetIds) {
                UserMessage um = new UserMessage();
                um.setMessageId(message.getId());
                um.setUserId(targetUserId);
                um.setIsRead(0);
                userMessageMapper.insert(um);
            }
        }
    }

    @Override
    public PageResult<Message> pageManageMessages(long current, long size) {
        Page<Message> page = new Page<>(current, size);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Message::getId);
        Page<Message> resultPage = this.page(page, wrapper);
        return PageResult.of(resultPage);
    }

    @Override
    public PageResult<MessageVO> pageMyMessages(long current, long size) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        Page<MessageVO> page = new Page<>(current, size);
        Page<MessageVO> resultPage = this.baseMapper.selectMyMessages(page, userId);
        return PageResult.of(resultPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readMessage(Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        // 查询消息本身是否存在
        Message message = this.getById(id);
        if (message == null) {
            throw new BusinessException(404, "消息不存在");
        }

        // 检查 user_message
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getMessageId, id)
               .eq(UserMessage::getUserId, userId);
        UserMessage um = userMessageMapper.selectOne(wrapper);

        if (um != null) {
            if (um.getIsRead() == 0) {
                um.setIsRead(1);
                um.setReadTime(LocalDateTime.now());
                userMessageMapper.updateById(um);
            }
        } else {
            // 没有关联记录，说明是第一次读取广播消息（或非发给该用户的消息，这里简化为都能读但创建已读记录）
            // 如果为了严谨，可以校验 message.getScope() == 1
            if (message.getScope() == 1) {
                UserMessage newUm = new UserMessage();
                newUm.setMessageId(id);
                newUm.setUserId(userId);
                newUm.setIsRead(1);
                newUm.setReadTime(LocalDateTime.now());
                userMessageMapper.insert(newUm);
            } else {
                throw new BusinessException(403, "无权读取该消息");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAllMessages() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        // 高效实现与自动主键兼顾：
        // 1. 查询该用户还没有阅读过的广播消息的 ID 列表
        List<Long> unreadIds = userMessageMapper.selectUnreadBroadcastMessageIds(userId);
        if (unreadIds != null && !unreadIds.isEmpty()) {
            for (Long msgId : unreadIds) {
                UserMessage newUm = new UserMessage();
                newUm.setMessageId(msgId);
                newUm.setUserId(userId);
                newUm.setIsRead(1);
                newUm.setReadTime(LocalDateTime.now());
                userMessageMapper.insert(newUm); // 让 MyBatis-Plus 自动为 id 生成 Snowflake ID
            }
        }
        // 2. 对于已存在于 user_message 中但状态为未读的消息，批量更新为已读
        userMessageMapper.updateAllUnreadToRead(userId);
    }
}
