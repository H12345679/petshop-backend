package com.petshop.map.service;

import com.petshop.map.dto.MapDistanceQuery;
import com.petshop.map.dto.MapDistanceResponse;
import com.petshop.map.dto.MapShopQuery;
import com.petshop.map.dto.MapShopResponse;

import java.util.List;

public interface MapShopService {

    List<MapShopResponse> findNearbyShops(MapShopQuery query);

    MapShopResponse getShopLocation(Long shopId);

    MapDistanceResponse calcDistance(MapDistanceQuery query);
}
