package com.petshop.content.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.content.dto.MessageSendDTO;
import com.petshop.content.entity.Message;
import com.petshop.content.service.MessageService;
import com.petshop.content.vo.MessageVO;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * E 模块 - 消息推送（E4）
 */
@Api(tags = "08-E模块-消息推送")
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @ApiOperation("后台发送/推送消息（仅 ADMIN）")
    @RequireRole({"ADMIN"})
    @PostMapping
    public Result<Void> sendMessage(@Validated @RequestBody MessageSendDTO dto) {
        messageService.sendMessage(dto);
        return Result.success();
    }

    @ApiOperation("获取后台历史消息分页（仅 ADMIN）")
    @RequireRole({"ADMIN"})
    @GetMapping("/manage")
    public Result<PageResult<Message>> pageManageMessages(
            @ApiParam("页码") @RequestParam(defaultValue = "1") long current,
            @ApiParam("每页大小") @RequestParam(defaultValue = "10") long size) {
        return Result.success(messageService.pageManageMessages(current, size));
    }

    @ApiOperation("获取我的消息列表分页（需登录）")
    @RequireLogin
    @GetMapping("/my")
    public Result<PageResult<MessageVO>> pageMyMessages(
            @ApiParam("页码") @RequestParam(defaultValue = "1") long current,
            @ApiParam("每页大小") @RequestParam(defaultValue = "10") long size) {
        return Result.success(messageService.pageMyMessages(current, size));
    }

    @ApiOperation("标记单条消息为已读")
    @RequireLogin
    @PutMapping("/my/{id}/read")
    public Result<Void> readMessage(
            @ApiParam(value = "消息ID", required = true) @PathVariable Long id) {
        messageService.readMessage(id);
        return Result.success();
    }

    @ApiOperation("一键全部标记为已读")
    @RequireLogin
    @PutMapping("/my/read-all")
    public Result<Void> readAllMessages() {
        messageService.readAllMessages();
        return Result.success();
    }

    @ApiOperation("统计我的未读消息数")
    @RequireLogin
    @GetMapping("/my/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(messageService.countUnread());
    }
}
