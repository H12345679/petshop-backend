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
import com.petshop.shop.entity.Shop;
import com.petshop.shop.service.ShopService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 视频模块 Service 实现（E 模块 - E2）
 */
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Autowired
    private ShopService shopService;

    @Autowired
    private ProductService productService;

    @Override
    public Video createVideo(VideoCreateDTO dto, Long loginUserId, String role) {
        // MERCHANT 创建视频时，校验 shopId 是否属于本人名下
        if ("MERCHANT".equals(role)) {
            checkShopOwnership(dto.getShopId(), loginUserId);
        }
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
        if ("MERCHANT".equals(role)) {
            checkShopOwnership(existing.getShopId(), loginUserId);
        }
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
        if ("MERCHANT".equals(role)) {
            checkShopOwnership(existing.getShopId(), loginUserId);
        }
        removeById(id);
    }

    /**
     * 校验店铺归属：查询 shop 表确认 owner_id == loginUserId，不匹配则抛 403。
     *
     * @param shopId      视频关联的店铺ID
     * @param loginUserId 当前登录商家的用户ID
     */
    private void checkShopOwnership(Long shopId, Long loginUserId) {
        if (shopId == null) {
            throw new BusinessException(400, "店铺ID不能为空");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在：" + shopId);
        }
        if (!loginUserId.equals(shop.getOwnerId())) {
            throw new BusinessException(403, "无权操作该店铺的视频");
        }
    }
}
