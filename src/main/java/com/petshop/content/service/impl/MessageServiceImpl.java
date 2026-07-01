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

        // 找出所有我可见且未读的消息（通过分页逻辑的 SQL 来查找也可以，但这里为了简单复用）
        // 更高效的方法是用一个自定义 SQL 批量更新，但为了简化直接调用 selectMyMessages 找出所有未读
        // 为了避免分页，可以用一个大 size，或者写一个专用的 Mapper 方法查未读 ID 列表
        
        // 简单实现：由于可能数量有限，直接拉取当前用户所有未读消息（或者用自定义SQL更新）
        // 这里提供一个稍微直接一点的实现：查询所有可见消息，如果没在 user_message 中且是广播，就插入；如果在且为0，就更新。
        // 但最安全高效的是让数据库执行，不过由于 JPA/MP 限制，我们手写一层逻辑：
        
        // 我们利用现有的 selectMyMessages 获取所有未读
        Page<MessageVO> page = new Page<>(1, 10000); // 假设未读不会超过10000
        Page<MessageVO> resultPage = this.baseMapper.selectMyMessages(page, userId);
        
        List<MessageVO> unreadList = new ArrayList<>();
        for (MessageVO vo : resultPage.getRecords()) {
            if (vo.getIsRead() == 0) {
                unreadList.add(vo);
            }
        }
        
        for (MessageVO vo : unreadList) {
            readMessage(vo.getId());
        }
    }
}
