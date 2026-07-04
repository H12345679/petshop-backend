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
        // 如果是管理员，默认上架(1)；如果是商家或普通用户，强制待审核(2)
        if ("ADMIN".equals(role)) {
            video.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        } else {
            video.setStatus(2);
        }
        save(video);
        return video;
    }

    /**
     * 获取公开的视频分页列表（供小程序/前台用户浏览时使用）
     * 
     * @param query 包含了各种过滤条件的查询参数对象
     * @return 包含视频列表和分页信息的结果集
     */
    @Override
    public PageResult<Video> pageVideos(VideoPageQuery query) {
        // 1. 将自定义的查询参数对象转换成 MyBatis-Plus 的 Page 分页对象
        Page<Video> page = query.toPage();
        
        // 2. 构造查询条件包装器
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                // 核心安全控制：公开接口【绝对只能】返回状态为 1 (已上架/已审核通过) 的视频
                .eq(Video::getStatus, 1) 
                // 可选条件：如果前端传了标题，就进行模糊搜索 (LIKE '%标题%')
                .like(StringUtils.hasText(query.getTitle()), Video::getTitle, query.getTitle())
                // 可选条件：如果传了商品ID，就只查关联了该商品的视频
                .eq(query.getProductId() != null && query.getProductId() > 0,
                        Video::getProductId, query.getProductId())
                // 可选条件：如果传了店铺ID，就只查该店铺发布的视频
                .eq(query.getShopId() != null, Video::getShopId, query.getShopId())
                // 排序规则：永远按照创建时间倒序排列（最新的视频在最上面）
                .orderByDesc(Video::getCreateTime);

        // 3. 高级过滤逻辑：根据【商品分类】来过滤视频
        // 因为视频表本身没有商品分类字段，所以需要通过商品表桥接一下
        if (query.getProductCategoryId() != null && query.getProductCategoryId() > 0) {
            // 第一步：去商品表里查出属于这个分类的所有商品
            List<Product> products = productService.list(new LambdaQueryWrapper<Product>()
                    .eq(Product::getCategoryId, query.getProductCategoryId()));
                    
            if (!products.isEmpty()) {
                // 如果查到了商品，就把这些商品的 ID 提取出来变成一个 List
                List<Long> productIds = products.stream().map(Product::getId).collect(java.util.stream.Collectors.toList());
                // 第二步：让视频的 productId 必须在这个 List 里面（使用 SQL 的 IN 语法）
                wrapper.in(Video::getProductId, productIds);
            } else {
                // 如果这个分类下没有任何商品，那么显然也不可能有相关的视频。
                // 故意拼接一个绝对不可能成立的条件 (productId = -1L)，让数据库直接返回空数据。
                wrapper.eq(Video::getProductId, -1L);
            }
        }

        // 4. 执行底层分页查询，并将底层的 Page 对象包装成前端认识的 PageResult 对象返回
        return PageResult.of(page(page, wrapper));
    }

    @Override
    public PageResult<Video> manageVideos(VideoPageQuery query) {
        Page<Video> page = query.toPage();
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .like(StringUtils.hasText(query.getTitle()), Video::getTitle, query.getTitle())
                .eq(query.getProductId() != null && query.getProductId() > 0,
                        Video::getProductId, query.getProductId())
                .eq(query.getShopId() != null, Video::getShopId, query.getShopId())
                .eq(query.getStatus() != null, Video::getStatus, query.getStatus())
                .orderByDesc(Video::getCreateTime);

        // 分类过滤逻辑
        if (query.getProductCategoryId() != null && query.getProductCategoryId() > 0) {
            List<Product> products = productService.list(new LambdaQueryWrapper<Product>()
                    .eq(Product::getCategoryId, query.getProductCategoryId()));
            if (!products.isEmpty()) {
                List<Long> productIds = products.stream().map(Product::getId).collect(java.util.stream.Collectors.toList());
                wrapper.in(Video::getProductId, productIds);
            } else {
                wrapper.eq(Video::getProductId, -1L);
            }
        }

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
        // 1. 先去数据库里查出这个视频原来的老数据
        Video existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }

        // 2. 权限校验：如果是商家（MERCHANT）来修改，必须得确保这个视频是他自己店铺里的
        ownershipChecker.assertShopOwned(existing.getShopId());

        // 3. 准备一个干净的新对象，只装载前端传过来（需要修改）的字段
        Video update = new Video();
        update.setId(id);

        // -- 逐个判断前端有没有传值过来。传了就更新，没传就保持原样（不覆盖原有数据） --
        if (StringUtils.hasText(dto.getTitle())) {
            update.setTitle(dto.getTitle());
        }
        if (StringUtils.hasText(dto.getCover())) {
            update.setCover(dto.getCover());
        }
        if (StringUtils.hasText(dto.getUrl())) {
            update.setUrl(dto.getUrl());
        }
        if (StringUtils.hasText(dto.getDescription())) {
            update.setDescription(dto.getDescription());
        }
        if (dto.getProductId() != null) {
            update.setProductId(dto.getProductId());
        }
        
        // 4. 审核状态处理
        if ("ADMIN".equals(role)) {
            // 只有超管有权力直接指定状态（比如前端传 1 就是上架，传 0 就是下架）
            if (dto.getStatus() != null) {
                update.setStatus(dto.getStatus());
            }
        } else {
            // 普通商家只要修改了视频的标题、封面、内容等信息，为了安全，系统强制将其打回“待审核(2)”状态
            update.setStatus(2);
        }
        
        // 5. 将这些装载好的新字段，更新到数据库里
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
