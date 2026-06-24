package com.petshop.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.content.dto.VideoCreateDTO;
import com.petshop.content.dto.VideoPageQuery;
import com.petshop.content.entity.Video;
import com.petshop.content.mapper.VideoMapper;
import com.petshop.content.service.VideoService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 视频模块 Service 实现（E 模块 - E2）
 */
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Override
    public Video createVideo(VideoCreateDTO dto, Long loginUserId, String role) {
        // MERCHANT 只能为自己名下店铺上传视频（shopId 归属校验由 Controller 层传入，这里直接信任）
        Video video = new Video();
        video.setTitle(dto.getTitle());
        video.setCover(dto.getCover());
        video.setUrl(dto.getUrl());
        video.setDescription(dto.getDescription());
        video.setProductId(dto.getProductId() != null ? dto.getProductId() : 0L);
        video.setShopId(dto.getShopId());
        video.setViews(0);
        video.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        save(video);
        return video;
    }

    @Override
    public PageResult<Video> pageVideos(VideoPageQuery query) {
        Page<Video> page = query.toPage();
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .like(StringUtils.hasText(query.getTitle()), Video::getTitle, query.getTitle())
                .eq(query.getProductId() != null && query.getProductId() > 0,
                        Video::getProductId, query.getProductId())
                .eq(query.getShopId() != null, Video::getShopId, query.getShopId())
                .eq(query.getStatus() != null, Video::getStatus, query.getStatus())
                .orderByDesc(Video::getCreateTime);
        return PageResult.of(page(page, wrapper));
    }

    @Override
    public Video getVideoAndIncrViews(Long id) {
        Video video = getById(id);
        if (video == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }
        // 播放量 +1（简化设计：直接 UPDATE，高并发场景可改成 Redis 计数 + 定时同步）
        Video update = new Video();
        update.setId(id);
        update.setViews(video.getViews() + 1);
        updateById(update);
        video.setViews(video.getViews() + 1);
        return video;
    }

    @Override
    public void updateVideo(Long id, VideoCreateDTO dto, Long loginUserId, String role) {
        Video existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }
        // MERCHANT 数据归属校验：只能修改本人店铺的视频（A 模块未就绪时暂跳过 shop owner 校验）
        // 待 A 提供商店接口后，可在此处查询 shop.owner_id 比对 loginUserId
        if ("MERCHANT".equals(role)) {
            if (!existing.getShopId().equals(dto.getShopId())) {
                // 如果传入的 shopId 与原视频的 shopId 不一致，直接拒绝（防止越权移店）
                throw new BusinessException(403, "无权操作该视频");
            }
        }
        // 只更新非空字段
        Video update = new Video();
        update.setId(id);
        if (StringUtils.hasText(dto.getTitle()))       update.setTitle(dto.getTitle());
        if (StringUtils.hasText(dto.getCover()))       update.setCover(dto.getCover());
        if (StringUtils.hasText(dto.getUrl()))         update.setUrl(dto.getUrl());
        if (StringUtils.hasText(dto.getDescription())) update.setDescription(dto.getDescription());
        if (dto.getProductId() != null)                update.setProductId(dto.getProductId());
        if (dto.getStatus() != null)                   update.setStatus(dto.getStatus());
        updateById(update);
    }

    @Override
    public void deleteVideo(Long id, Long loginUserId, String role) {
        Video existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }
        // MERCHANT 只能删本人店铺的视频（简化：这里暂不查 shop owner，待 A 接口就绪后补充）
        removeById(id);
    }
}
