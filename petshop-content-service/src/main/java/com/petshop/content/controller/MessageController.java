package com.petshop.content.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.content.dto.MessageSendDTO;
import com.petshop.content.entity.Message;
import com.petshop.content.service.MessageService;
import com.petshop.content.vo.MessageVO;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * E 模块 - 消息推送(E4)
 */
@Tag(name = "08-E模块-消息推送")
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Operation(summary = "后台发送/推送消息(仅 ADMIN 和 MERCHANT)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping
    public Result<Void> sendMessage(@Validated @RequestBody MessageSendDTO dto) {
        messageService.sendMessage(dto);
        return Result.success();
    }

    @Operation(summary = "获取后台历史消息分页(仅 ADMIN 和 MERCHANT)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/manage")
    public Result<PageResult<Message>> pageManageMessages(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") long size) {
        return Result.success(messageService.pageManageMessages(current, size));
    }

    @Operation(summary = "获取我的消息列表分页(需登录)")
    @RequireLogin
    @GetMapping("/my")
    public Result<PageResult<MessageVO>> pageMyMessages(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") long size) {
        return Result.success(messageService.pageMyMessages(current, size));
    }

    @Operation(summary = "标记单条消息为已读")
    @RequireLogin
    @PutMapping("/my/{id}/read")
    public Result<Void> readMessage(
            @Parameter(description = "消息ID") @PathVariable Long id) {
        messageService.readMessage(id);
        return Result.success();
    }

    @Operation(summary = "一键全部标记为已读")
    @RequireLogin
    @PutMapping("/my/read-all")
    public Result<Void> readAllMessages() {
        messageService.readAllMessages();
        return Result.success();
    }
}
