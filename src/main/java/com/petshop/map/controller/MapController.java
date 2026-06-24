package com.petshop.map.controller;

import com.petshop.common.Result;
import com.petshop.map.dto.MapShopQuery;
import com.petshop.map.dto.MapShopResponse;
import com.petshop.map.service.MapShopService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "D-地图LBS")
@Validated
@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapShopService mapShopService;

    public MapController(MapShopService mapShopService) {
        this.mapShopService = mapShopService;
    }

    @ApiOperation("地图找附近商店")
    @GetMapping("/shops")
    public Result<List<MapShopResponse>> nearbyShops(@Validated MapShopQuery query) {
        return Result.success(mapShopService.findNearbyShops(query));
    }
}
