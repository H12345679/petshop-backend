package com.petshop.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.common.PageResult;
import com.petshop.content.dto.VideoCreateDTO;
import com.petshop.content.dto.VideoPageQuery;
import com.petshop.content.entity.Video;
import com.petshop.content.vo.VideoDetailVO;

/**
 * 视频模块 Service 接口（E 模块 - E2 视频接口）
 *
 * <p>包含：视频元数据保存、分页查询、视频详情（含播放量递增）、更新、删除。
 * 文件上传由 VideoController 直接处理（存本地），url 传入本接口保存元数据。</p>
 */
public interface VideoService extends IService<Video> {

    /**
     * 保存视频元数据（创建）
     *
     * @param dto     视频创建请求体
     * @param loginShopOwnerId 当前登录用户ID（用于 MERCHANT 归属校验）
     * @param role    当前登录角色
     * @return 创建完成的 Video 实体（含 ID）
     */
    Video createVideo(VideoCreateDTO dto, Long loginShopOwnerId, String role);

    /**
     * 分页查询视频列表
     *
     * @param query 分页 + 过滤条件
     * @return 分页结果
     */
    PageResult<Video> pageVideos(VideoPageQuery query);

    /**
     * 获取视频详情（含关联商品基本信息），并将播放量 +1
     *
     * @param id 视频ID
     * @return VideoDetailVO（含 productName / productMainImage / productPrice，无关联商品时为 null）
     */
    VideoDetailVO getVideoAndIncrViews(Long id);

    /**
     * 更新视频元数据
     *
     * @param id          视频ID
     * @param dto         更新请求体
     * @param loginUserId 当前登录用户ID
     * @param role        当前登录角色
     */
    void updateVideo(Long id, VideoCreateDTO dto, Long loginUserId, String role);

    /**
     * 逻辑删除视频
     *
     * @param id          视频ID
     * @param loginUserId 当前登录用户ID
     * @param role        当前登录角色
     */
    void deleteVideo(Long id, Long loginUserId, String role);
}
