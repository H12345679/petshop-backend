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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统日志与订单状态流转日志控制器
 * 负责处理后台管理员对系统操作日志、订单状态变更记录的查询请求
 */
@Api(tags = "10-系统与订单日志管理")
@RestController
@RequestMapping("/api/sys/log")
public class SysLogController {

    @Autowired
    private SysLogService sysLogService;
    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 分页查询系统操作日志
     * 
     * @param current 当前页码
     * @param size 每页记录数
     * @param username 操作人用户名（模糊搜索）
     * @param operation 操作内容（模糊搜索）
     * @return 包含系统日志的分页结果
     */
    @ApiOperation("分页查询系统操作日志")
    @GetMapping("/page")
    public Result<Page<SysLog>> getPage(@RequestParam(defaultValue = "1") Integer current,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) String username,
                          @RequestParam(required = false) String operation) {
        Page<SysLog> page = new Page<>(current, size);
        QueryWrapper<SysLog> queryWrapper = new QueryWrapper<>();
        
        // 按用户名模糊匹配
        if (username != null && !username.isEmpty()) {
            queryWrapper.like("username", username);
        }
        // 按操作内容模糊匹配
        if (operation != null && !operation.isEmpty()) {
            queryWrapper.like("operation", operation);
        }
        // 默认按创建时间倒序排列，最新的操作日志排在最前面
        queryWrapper.orderByDesc("create_time");
        
        return Result.success(sysLogService.page(page, queryWrapper));
    }

    /**
     * 分页查询订单状态流转日志
     * 仅限 ADMIN 和 MERCHANT 角色访问，主要用于后台【订单状态变更记录】页面展示
     * 
     * @param current 当前页码
     * @param size 每页记录数
     * @param orderId 订单ID（模糊匹配）
     * @param operatorRole 操作人角色（精确匹配）
     * @param remark 备注内容（模糊匹配）
     * @return 包含订单状态流转日志（附带操作人姓名）的分页结果
     */
    @ApiOperation("分页查询订单状态流转日志")
    @GetMapping("/order-status/page")
    @RequireRole({"ADMIN", "MERCHANT"})
    public Result<Map<String, Object>> getOrderStatusLogs(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String operatorRole,
            @RequestParam(required = false) String remark) {

        // 1. 根据前端传参构建查询条件
        LambdaQueryWrapper<OrderStatusLog> w = buildOrderStatusQuery(orderId, operatorRole, remark);
        
        // 2. 执行分页查询，获取数据库中的日志 PO 对象
        Page<OrderStatusLog> page = orderStatusLogMapper.selectPage(new Page<>(current, size), w);

        // 3. 提取分页结果中的所有操作人ID，批量查询获取真实的姓名/昵称字典 (解决N+1查询问题)
        Map<Long, String> nameMap = buildOperatorNameMap(page.getRecords());
        
        // 4. 将 PO 对象组合拓展字典映射，转换为前端需要的视图对象 VO 列表
        List<Map<String, Object>> records = buildOrderStatusVoList(page.getRecords(), nameMap);

        // 5. 组装并返回标准分页结构数据
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("current", current);
        result.put("size", size);
        return Result.success(result);
    }

    /**
     * 构建订单状态日志的查询条件包装器
     */
    private LambdaQueryWrapper<OrderStatusLog> buildOrderStatusQuery(String orderId, String operatorRole, String remark) {
        LambdaQueryWrapper<OrderStatusLog> w = new LambdaQueryWrapper<>();
        
        // 处理 orderId: 将数据库中的长整型 order_id 转为字符后进行模糊搜索 (LIKE %id%)
        if (orderId != null && !orderId.trim().isEmpty()) {
            w.apply("CAST(order_id AS CHAR) LIKE CONCAT('%',{0},'%')", orderId.trim());
        }
        if (operatorRole != null && !operatorRole.isEmpty()) {
            w.eq(OrderStatusLog::getOperatorRole, operatorRole);
        }
        if (remark != null && !remark.isEmpty()) {
            w.like(OrderStatusLog::getRemark, remark);
        }
        
        // 按照操作时间倒序排列，最近的操作排在最上面
        w.orderByDesc(OrderStatusLog::getCreateTime);
        return w;
    }

    /**
     * 批量查询操作人信息字典
     * 根据日志记录中的 operatorId，一次性查出对应的用户，构建 ID -> 姓名的映射字典，
     * 以避免在循环中逐条查询 user 表导致严重的性能问题
     * 
     * @param records 订单状态日志记录列表
     * @return 操作人ID -> 用户昵称(或用户名)的字典
     */
    private Map<Long, String> buildOperatorNameMap(List<OrderStatusLog> records) {
        Set<Long> operatorIds = new HashSet<>();
        // 收集所有的操作人ID并去重
        for (OrderStatusLog log : records) {
            if (log.getOperatorId() != null) {
                operatorIds.add(log.getOperatorId());
            }
        }
        
        Map<Long, String> nameMap = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            // 使用 IN 查询一次性获取所有相关用户信息
            List<User> users = userMapper.selectBatchIds(operatorIds);
            for (User u : users) {
                // 优先展示用户设置的昵称，如果未设置则展示登录账号名
                nameMap.put(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername());
            }
        }
        return nameMap;
    }

    /**
     * 构建前端最终渲染所需的日志视图列表
     * 
     * @param records 数据库查出的日志记录
     * @param nameMap 操作人ID与姓名的映射字典
     * @return 注入了操作人真实姓名的 Map 列表
     */
    private List<Map<String, Object>> buildOrderStatusVoList(List<OrderStatusLog> records, Map<Long, String> nameMap) {
        List<Map<String, Object>> voList = new ArrayList<>();
        for (OrderStatusLog log : records) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", log.getId());
            vo.put("orderId", log.getOrderId());
            vo.put("fromStatus", log.getFromStatus());
            vo.put("toStatus", log.getToStatus());
            vo.put("operatorId", log.getOperatorId());
            // 映射操作人姓名。如果字典中不存在或者没有操作人，默认显示为"系统"自动操作
            vo.put("operatorName", nameMap.getOrDefault(log.getOperatorId(), "系统"));
            vo.put("operatorRole", log.getOperatorRole());
            vo.put("remark", log.getRemark());
            vo.put("createTime", log.getCreateTime());
            voList.add(vo);
        }
        return voList;
    }
}
