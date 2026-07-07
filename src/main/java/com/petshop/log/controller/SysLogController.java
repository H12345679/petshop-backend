package com.petshop.log.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.common.Result;
import com.petshop.log.entity.SysLog;
import com.petshop.log.service.SysLogService;
import com.petshop.order.entity.OrderStatusLog;
import com.petshop.order.mapper.OrderStatusLogMapper;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import com.petshop.security.RequireRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sys/log")
public class SysLogController {

    @Autowired
    private SysLogService sysLogService;
    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/page")
    public Result<Page<SysLog>> getPage(@RequestParam(defaultValue = "1") Integer current,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) String username,
                          @RequestParam(required = false) String operation) {
        Page<SysLog> page = new Page<>(current, size);
        QueryWrapper<SysLog> queryWrapper = new QueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            queryWrapper.like("username", username);
        }
        if (operation != null && !operation.isEmpty()) {
            queryWrapper.like("operation", operation);
        }
        queryWrapper.orderByDesc("create_time");
        return Result.success(sysLogService.page(page, queryWrapper));
    }

    @GetMapping("/order-status/page")
    @RequireRole({"ADMIN", "MERCHANT"})
    public Result<Map<String, Object>> getOrderStatusLogs(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String operatorRole,
            @RequestParam(required = false) String remark) {

        LambdaQueryWrapper<OrderStatusLog> w = new LambdaQueryWrapper<>();
        if (orderId != null && !orderId.trim().isEmpty()) {
            w.apply("CAST(order_id AS CHAR) LIKE CONCAT('%',{0},'%')", orderId.trim());
        }
        if (operatorRole != null && !operatorRole.isEmpty()) w.eq(OrderStatusLog::getOperatorRole, operatorRole);
        if (remark != null && !remark.isEmpty()) w.like(OrderStatusLog::getRemark, remark);
        w.orderByDesc(OrderStatusLog::getCreateTime);

        Page<OrderStatusLog> page = orderStatusLogMapper.selectPage(new Page<>(current, size), w);

        // 批量查操作人名称
        Set<Long> operatorIds = new HashSet<>();
        for (OrderStatusLog log : page.getRecords()) {
            if (log.getOperatorId() != null) operatorIds.add(log.getOperatorId());
        }
        Map<Long, String> nameMap = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(operatorIds);
            for (User u : users) {
                nameMap.put(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername());
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (OrderStatusLog log : page.getRecords()) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", log.getId());
            vo.put("orderId", log.getOrderId());
            vo.put("fromStatus", log.getFromStatus());
            vo.put("toStatus", log.getToStatus());
            vo.put("operatorId", log.getOperatorId());
            vo.put("operatorName", nameMap.getOrDefault(log.getOperatorId(), "系统"));
            vo.put("operatorRole", log.getOperatorRole());
            vo.put("remark", log.getRemark());
            vo.put("createTime", log.getCreateTime());
            records.add(vo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("current", current);
        result.put("size", size);
        return Result.success(result);
    }
}
