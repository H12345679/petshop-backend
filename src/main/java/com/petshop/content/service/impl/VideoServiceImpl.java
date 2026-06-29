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
import com.petshop.content.vo.VideoDetailVO;
import com.petshop.product.entity.Product;
import com.petshop.product.service.ProductService;
import com.petshop.security.OwnershipChecker;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 视频模块 Service 实现（E 模块 - E2）
 */
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Autowired
    private ProductService productService;

    @Autowired
    private OwnershipChecker ownershipChecker;

    @Override
    public Video createVideo(VideoCreateDTO dto, Long loginUserId, String role) {
        // MERCHANT 创建视频时，校验 shopId 是否属于本人名下
        ownershipChecker.assertShopOwned(dto.getShopId());
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

        // MERCHANT 只看自己名下店铺的视频；ADMIN/null/empty 不过滤
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null) {
            if (shopIds.isEmpty()) {
                wrapper.eq(Video::getShopId, -1L); // MERCHANT 无店铺 → 返回空
            } else {
                wrapper.in(Video::getShopId, shopIds);
            }
        }

        return PageResult.of(page(page, wrapper));
    }

    @Override
    public VideoDetailVO getVideoAndIncrViews(Long id) {
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

        // 组装 VO
        VideoDetailVO vo = new VideoDetailVO();
        BeanUtils.copyProperties(video, vo);

        // 关联商品信息：productId > 0 时查询商品基本信息，供前端"可跳商品"展示
        if (video.getProductId() != null && video.getProductId() > 0) {
            Product product = productService.getById(video.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductMainImage(product.getMainImage());
                vo.setProductPrice(product.getPrice());
                vo.setProductStatus(product.getStatus());
            }
        }
        return vo;
    }

    @Override
    public void updateVideo(Long id, VideoCreateDTO dto, Long loginUserId, String role) {
        Video existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }
        // MERCHANT 数据归属校验：通过 shop.owner_id 验证该店铺确实属于当前登录商家
        ownershipChecker.assertShopOwned(existing.getShopId());
        // 只更新非空字段
        Video update = new Video();
        update.setId(id);
        if (StringUtils.hasText(dto.getTitle()))        update.setTitle(dto.getTitle());
        if (StringUtils.hasText(dto.getCover()))        update.setCover(dto.getCover());
        if (StringUtils.hasText(dto.getUrl()))          update.setUrl(dto.getUrl());
        if (StringUtils.hasText(dto.getDescription()))  update.setDescription(dto.getDescription());
        if (dto.getProductId() != null)                 update.setProductId(dto.getProductId());
        if (dto.getStatus() != null)                    update.setStatus(dto.getStatus());
        updateById(update);
    }

    @Override
    public void deleteVideo(Long id, Long loginUserId, String role) {
        Video existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }
        // MERCHANT 只能删本人店铺的视频，通过 shop.owner_id 校验
        ownershipChecker.assertShopOwned(existing.getShopId());
        removeById(id);
    }

}
