package com.petshop.log.controller;



import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.common.Result;
import com.petshop.log.entity.SysLog;
import com.petshop.log.service.SysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sys/log")
public class SysLogController {

    @Autowired
    private SysLogService sysLogService;

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
}
