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

        BoundingBox box = buildBoundingBox(longitude, latitude, radius);
        LambdaQueryWrapper<MapShop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MapShop::getStatus, 1)
                .isNotNull(MapShop::getLongitude)
                .isNotNull(MapShop::getLatitude)
                .between(MapShop::getLongitude, box.minLongitude, box.maxLongitude)
                .between(MapShop::getLatitude, box.minLatitude, box.maxLatitude);

        List<MapShopResponse> list = mapShopMapper.selectList(wrapper).stream()
                .map(shop -> toResponse(shop, longitude, latitude))
                .filter(Objects::nonNull)
                .filter(shop -> shop.getDistanceKm().doubleValue() <= radius)
                .sorted(Comparator.comparing(MapShopResponse::getDistanceKm))
                .limit(limit)
                .collect(Collectors.toList());

        // Fallback: 如果指定半径内没有找到任何门店，则返回全局最近的门店，避免页面空白，引导用户了解门店分布
        if (list.isEmpty()) {
            LambdaQueryWrapper<MapShop> fallbackWrapper = new LambdaQueryWrapper<>();
            fallbackWrapper.eq(MapShop::getStatus, 1)
                    .isNotNull(MapShop::getLongitude)
                    .isNotNull(MapShop::getLatitude);
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
        MapShop shop = mapShopMapper.selectOne(new LambdaQueryWrapper<MapShop>()
                .eq(MapShop::getId, shopId)
                .eq(MapShop::getStatus, 1));
        if (shop == null) {
            throw new BusinessException("商店不存在或已停业");
        }
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
        double fromLon = query.getFromLongitude().doubleValue();
        double fromLat = query.getFromLatitude().doubleValue();
        double toLon = query.getToLongitude().doubleValue();
        double toLat = query.getToLatitude().doubleValue();

        double km = distanceKm(fromLat, fromLon, toLat, toLon);
        BigDecimal distance = BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);

        // 驾车按城市道路均速30km/h，步行按5km/h
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

    private BoundingBox buildBoundingBox(double longitude, double latitude, double radiusKm) {
        double latitudeDelta = radiusKm / 111.0;
        double longitudeDelta = radiusKm / (111.0 * Math.max(Math.cos(Math.toRadians(latitude)), 0.01));

        return new BoundingBox(
                longitude - longitudeDelta,
                longitude + longitudeDelta,
                latitude - latitudeDelta,
                latitude + latitudeDelta
        );
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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
