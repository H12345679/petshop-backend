package com.petshop.map.controller;

import com.petshop.common.Result;
import com.petshop.map.dto.MapDistanceQuery;
import com.petshop.map.dto.MapDistanceResponse;
import com.petshop.map.dto.MapShopQuery;
import com.petshop.map.dto.MapShopResponse;
import com.petshop.map.service.MapShopService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
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

    @ApiOperation("获取单个商店坐标信息（地图弹窗用）")
    @GetMapping("/shops/{id}")
    public Result<MapShopResponse> shopLocation(
            @ApiParam(value = "商店ID", required = true)
            @PathVariable @Min(value = 1, message = "商店ID必须大于0") Long id) {
        return Result.success(mapShopService.getShopLocation(id));
    }

    @ApiOperation("计算两点距离与预估时长")
    @GetMapping("/distance")
    public Result<MapDistanceResponse> calcDistance(@Validated MapDistanceQuery query) {
        return Result.success(mapShopService.calcDistance(query));
    }
}
