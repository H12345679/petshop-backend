package com.petshop.file;

import com.petshop.common.Result;
import com.petshop.security.RequireRole;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传接口（七牛云）。
 * <p>
 * 用法：前端先调本接口上传文件拿到 URL，再把 URL 填进商品/视频表单一起提交。
 * 仅 ADMIN/MERCHANT 可上传（普通用户不该往后台传图）。
 */
@Api(tags = "00-文件上传")
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private QiniuService qiniuService;

    @ApiOperation("上传图片（商品主图/图册/SKU 图），返回可访问 URL")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(wrap(qiniuService.upload(file, "images")));
    }

    @ApiOperation("上传视频，返回可访问 URL")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping("/video")
    public Result<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        return Result.success(wrap(qiniuService.upload(file, "videos")));
    }

    @ApiOperation("上传用户头像，返回可访问 URL")
    @com.petshop.security.RequireLogin
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(wrap(qiniuService.upload(file, "avatars")));
    }
    @ApiOperation("上传评价晒图，返回可访问 URL")
    @com.petshop.security.RequireLogin
    @PostMapping("/review")
    public Result<Map<String, String>> uploadReview(@RequestParam("file") MultipartFile file) {
        return Result.success(wrap(qiniuService.upload(file, "reviews")));
    }

    @ApiOperation("上传退款凭证，返回可访问 URL")
    @com.petshop.security.RequireLogin
    @PostMapping("/refund")
    public Result<Map<String, String>> uploadRefund(@RequestParam("file") MultipartFile file) {
        return Result.success(wrap(qiniuService.upload(file, "refunds")));
    }

    /** 统一包成 { "url": "..." } 返回，与前端约定一致。 */
    private Map<String, String> wrap(String url) {
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return data;
    }
}
