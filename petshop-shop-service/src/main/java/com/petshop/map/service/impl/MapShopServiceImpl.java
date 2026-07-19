package com.petshop.map.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.common.BusinessException;
import com.petshop.map.dto.MapDistanceQuery;
import com.petshop.map.dto.MapDistanceResponse;
import com.petshop.map.dto.MapShopQuery;
import com.petshop.map.dto.MapShopResponse;
import com.petshop.map.entity.MapShop;
import com.petshop.map.mapper.MapShopMapper;
import com.petshop.map.service.MapShopService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MapShopServiceImpl implements MapShopService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final MapShopMapper mapShopMapper;

    public MapShopServiceImpl(MapShopMapper mapShopMapper) {
        this.mapShopMapper = mapShopMapper;
    }

    @Override
    public List<MapShopResponse> findNearbyShops(MapShopQuery query) {
        double longitude = query.getLongitude().doubleValue();
        double latitude = query.getLatitude().doubleValue();
        double radius = query.getRadius().doubleValue();
        int limit = query.getLimit();

        // 1. 初筛：基于用户的经纬度和搜索半径，构建一个虚拟的正方形“外接边界框（BoundingBox）”
        BoundingBox box = buildBoundingBox(longitude, latitude, radius);
        
        // 2. 数据库查询：利用正方形边界进行初步筛选，利用数据库的 BETWEEN 查询大幅提升检索效率
        LambdaQueryWrapper<MapShop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MapShop::getStatus, 1) // 必须是正常营业状态的店铺
                .isNotNull(MapShop::getLongitude) // 必须有合法的经纬度定位
                .isNotNull(MapShop::getLatitude)
                .between(MapShop::getLongitude, box.minLongitude, box.maxLongitude)
                .between(MapShop::getLatitude, box.minLatitude, box.maxLatitude);

        // 3. 内存精筛：上面查出来的是“正方形”里的店铺，这里利用 Java Stream 计算精确的球面（圆形）距离
        List<MapShopResponse> list = mapShopMapper.selectList(wrapper).stream()
                .map(shop -> toResponse(shop, longitude, latitude)) // 这一步会通过 Haversine 公式计算出该店到用户的精确直线距离
                .filter(Objects::nonNull)
                .filter(shop -> shop.getDistanceKm().doubleValue() <= radius) // 过滤掉落在正方形四个角（实际上超出了圆形半径）的店铺
                .sorted(Comparator.comparing(MapShopResponse::getDistanceKm)) // 按照距离由近到远依次排序
                .limit(limit) // 限制返回的数量，比如一页最多取前 10 家
                .collect(Collectors.toList());

        // 4. Fallback (兜底逻辑): 如果指定半径内实在太偏僻、没有找到任何门店
        // 则强制返回全国范围内距离用户最近的几家门店，避免小程序页面呈现出一片空白的死寂，引导用户了解我们的门店分布
        if (list.isEmpty()) {
            LambdaQueryWrapper<MapShop> fallbackWrapper = new LambdaQueryWrapper<>();
            fallbackWrapper.eq(MapShop::getStatus, 1)
                    .isNotNull(MapShop::getLongitude)
                    .isNotNull(MapShop::getLatitude);
            
            // 全表查出所有营业中的店铺，在内存中全部算一遍距离并排个序，强行取最近的几个
            list = mapShopMapper.selectList(fallbackWrapper).stream()
                    .map(shop -> toResponse(shop, longitude, latitude))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(MapShopResponse::getDistanceKm))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        return list;
    }

    @Override
    public MapShopResponse getShopLocation(Long shopId) {
        // 1. 根据传入的店铺 ID 去数据库捞取这条记录，并且要求状态必须是营业中 (status = 1)
        MapShop shop = mapShopMapper.selectOne(new LambdaQueryWrapper<MapShop>()
                .eq(MapShop::getId, shopId)
                .eq(MapShop::getStatus, 1));
        
        if (shop == null) {
            throw new BusinessException("商店不存在或已停业");
        }
        // 2. 如果这家店的经纬度是空的（可能是商家刚入驻还没在地图上选点），直接报错拦截
        if (shop.getLongitude() == null || shop.getLatitude() == null) {
            throw new BusinessException("该商店未标注位置");
        }
        MapShopResponse response = new MapShopResponse();
        response.setId(shop.getId());
        response.setName(shop.getName());
        response.setPhone(shop.getPhone());
        response.setLogo(shop.getLogo());
        response.setLongitude(shop.getLongitude());
        response.setLatitude(shop.getLatitude());
        response.setAddress(fullAddress(shop));
        response.setDistanceKm(BigDecimal.ZERO);
        return response;
    }

    @Override
    public MapDistanceResponse calcDistance(MapDistanceQuery query) {
        // 1. 提取起点和终点的经纬度
        double fromLon = query.getFromLongitude().doubleValue();
        double fromLat = query.getFromLatitude().doubleValue();
        double toLon = query.getToLongitude().doubleValue();
        double toLat = query.getToLatitude().doubleValue();

        // 2. 调用球面距离公式，计算真实的物理直线距离（公里）
        double km = distanceKm(fromLat, fromLon, toLat, toLon);
        // 将双精度浮点数保留两位小数，方便前端展示，比如 2.56 km
        BigDecimal distance = BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);

        // 3. 极其接地气的“耗时估算”：
        // 驾车按城市道路平均车速 30km/h 估算；步行按人类平均步速 5km/h 估算。
        // Math.ceil() 向上取整，哪怕只要 0.1 分钟也算 1 分钟，给用户一点余量
        int driveMinutes = (int) Math.ceil(km / 30.0 * 60);
        int walkMinutes = (int) Math.ceil(km / 5.0 * 60);

        MapDistanceResponse response = new MapDistanceResponse();
        response.setFromLongitude(query.getFromLongitude());
        response.setFromLatitude(query.getFromLatitude());
        response.setToLongitude(query.getToLongitude());
        response.setToLatitude(query.getToLatitude());
        response.setDistanceKm(distance);
        response.setEstimatedMinutes(driveMinutes);
        response.setWalkingMinutes(walkMinutes);
        return response;
    }

    private MapShopResponse toResponse(MapShop shop, double longitude, double latitude) {
        if (shop.getLongitude() == null || shop.getLatitude() == null) {
            return null;
        }

        double distance = distanceKm(
                latitude,
                longitude,
                shop.getLatitude().doubleValue(),
                shop.getLongitude().doubleValue()
        );

        MapShopResponse response = new MapShopResponse();
        response.setId(shop.getId());
        response.setName(shop.getName());
        response.setPhone(shop.getPhone());
        response.setLogo(shop.getLogo());
        response.setLongitude(shop.getLongitude());
        response.setLatitude(shop.getLatitude());
        response.setAddress(fullAddress(shop));
        response.setDistanceKm(BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP));
        return response;
    }

    /**
     * 构建经纬度“外接正方形边界（Bounding Box）”
     * 作用：把地球球面上的“圆形搜索半径”，转换成经度最大/最小、纬度最大/最小的四个坐标值。
     * 这样在 SQL 里就可以用 BETWEEN 进行极速的范围过滤，避免全表扫描算距离。
     */
    private BoundingBox buildBoundingBox(double longitude, double latitude, double radiusKm) {
        // 纬度变化：地球上每跨越 1 度纬度，大概相距 111 公里（这是个常识近似值）
        double latitudeDelta = radiusKm / 111.0;
        
        // 经度变化：经度的间隔随纬度升高而缩小（到了北极点所有经线都交于一点）。
        // 所以必须除以 Math.cos(纬度)，做一个球面补偿修正，保证画出来的正方形不会变形
        double longitudeDelta = radiusKm / (111.0 * Math.max(Math.cos(Math.toRadians(latitude)), 0.01));

        return new BoundingBox(
                longitude - longitudeDelta,
                longitude + longitudeDelta,
                latitude - latitudeDelta,
                latitude + latitudeDelta
        );
    }

    /**
     * 黑科技：Haversine (半正矢) 球面距离公式
     * 作用：把地球当作一个完美的球体（半径约 6371km），计算两对经纬度在球面上的大圆劣弧距离。
     * 它是比勾股定理精确得多的地理坐标系测距算法。
     */
    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        // 先把日常使用的角度制（度）转换成三角函数使用的弧度制（Rad）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        // 核心计算步骤：包含了非常复杂的球面三角运算
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        // 最后乘以地球半径，得出真实的物理千米数 (km)
        return EARTH_RADIUS_KM * c;
    }

    private String fullAddress(MapShop shop) {
        StringBuilder address = new StringBuilder();
        append(address, shop.getProvince());
        append(address, shop.getCity());
        append(address, shop.getDistrict());
        append(address, shop.getAddress());
        return address.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value);
        }
    }

    private static class BoundingBox {
        private final double minLongitude;
        private final double maxLongitude;
        private final double minLatitude;
        private final double maxLatitude;

        private BoundingBox(double minLongitude, double maxLongitude, double minLatitude, double maxLatitude) {
            this.minLongitude = minLongitude;
            this.maxLongitude = maxLongitude;
            this.minLatitude = minLatitude;
            this.maxLatitude = maxLatitude;
        }
    }
}
