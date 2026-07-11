package com.petshop.content.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.content.dto.VideoCreateDTO;
import com.petshop.content.dto.VideoPageQuery;
import com.petshop.content.entity.Video;
import com.petshop.content.service.VideoService;
import com.petshop.content.vo.VideoDetailVO;
import com.petshop.file.QiniuService;
import com.petshop.log.annotation.LogOperation;
import com.petshop.security.RequireRole;
import com.petshop.security.UserContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * E 模块 - 视频接口(E2)
 *
 * <p>接口列表:
 * <ul>
 *   <li>POST /api/videos/upload — 视频文件上传(ADMIN / MERCHANT)</li>
 *   <li>POST /api/videos — 保存视频元数据(ADMIN / MERCHANT)</li>
 *   <li>GET  /api/videos — 视频分页列表(公开)</li>
 *   <li>GET  /api/videos/{id} — 视频详情 + 播放量+1(公开)</li>
 *   <li>PUT  /api/videos/{id} — 编辑视频(ADMIN / MERCHANT)</li>
 *   <li>DELETE /api/videos/{id} — 删除视频(ADMIN / MERCHANT)</li>
 * </ul>
 * </p>
 */
@Tag(name = "06-E模块-视频管理")
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Autowired
    private VideoService videoService;

    /** 七牛云上传服务(视频统一存七牛, 与商品图片一致). */
    @Autowired
    private QiniuService qiniuService;

    // ==================== E2 - 视频文件上传 ====================

    @Operation(summary = "视频文件上传(返回可访问URL), 仅 ADMIN/MERCHANT")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadVideo(
            @Parameter(description = "视频文件(mp4/mov 等)")
            @RequestParam("file") MultipartFile file) {
        // 直传七牛云(空文件校验、UUID 命名都在 QiniuService 里), 返回可访问 URL
        String url = qiniuService.upload(file, "videos");
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success("上传成功", data);
    }

    // ==================== E2 - 视频元数据创建 ====================

    @Operation(summary = "保存视频元数据(创建视频), 仅 ADMIN/MERCHANT")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping
    public Result<Video> createVideo(@Validated @RequestBody VideoCreateDTO dto) {
        UserContext.LoginUser loginUser = UserContext.get();
        Video video = videoService.createVideo(dto, loginUser.getUserId(), loginUser.getRole());
        return Result.success("创建成功", video);
    }

    // ==================== E2 - 视频分页列表(公开) ====================

    @Operation(summary = "视频分页列表(公开, 支持标题/商品/店铺过滤)")
    @GetMapping
    public Result<PageResult<Video>> pageVideos(VideoPageQuery query) {
        return Result.success(videoService.pageVideos(query));
    }

    // ==================== E2 - 视频分页列表(后台管理) ====================

    @Operation(summary = "视频分页列表(后台管理, 支持所有状态过滤)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/manage")
    public Result<PageResult<Video>> manageVideos(VideoPageQuery query) {
        return Result.success(videoService.manageVideos(query));
    }

    // ==================== E2 - 视频详情(公开, 播放量+1) ====================

    @Operation(summary = "视频详情(播放量自动+1, 含关联商品名称/图片/价格, 用于播放页'可跳商品'功能)")
    @GetMapping("/{id}")
    public Result<VideoDetailVO> getVideo(
            @Parameter(description = "视频ID") @PathVariable Long id) {
        return Result.success(videoService.getVideoAndIncrViews(id));
    }

    // ==================== E7 - 后台编辑视频 ====================

    @Operation(summary = "编辑视频元数据, 仅 ADMIN/MERCHANT(MERCHANT 只能改本店视频)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @LogOperation("编辑/审核视频")
    @PutMapping("/{id}")
    public Result<Void> updateVideo(
            @Parameter(description = "视频ID") @PathVariable Long id,
            @RequestBody VideoCreateDTO dto) {
        UserContext.LoginUser loginUser = UserContext.get();
        videoService.updateVideo(id, dto, loginUser.getUserId(), loginUser.getRole());
        return Result.success();
    }

    // ==================== E7 - 后台删除视频 ====================

    @Operation(summary = "删除视频(逻辑删除), 仅 ADMIN/MERCHANT(MERCHANT 只能删本店视频)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @DeleteMapping("/{id}")
    public Result<Void> deleteVideo(
            @Parameter(description = "视频ID") @PathVariable Long id) {
        UserContext.LoginUser loginUser = UserContext.get();
        videoService.deleteVideo(id, loginUser.getUserId(), loginUser.getRole());
        return Result.success();
    }
}
