package com.petshop.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "两点距离计算结果")
public class MapDistanceResponse {

    @Schema(description = "起点经度")
    private BigDecimal fromLongitude;

    @Schema(description = "起点纬度")
    private BigDecimal fromLatitude;

    @Schema(description = "终点经度")
    private BigDecimal toLongitude;

    @Schema(description = "终点纬度")
    private BigDecimal toLatitude;

    @Schema(description = "直线距离，单位公里")
    private BigDecimal distanceKm;

    @Schema(description = "预估驾车时长,单位分钟(按城市道路均速30km/h)")
    private Integer estimatedMinutes;

    @Schema(description = "预估步行时长,单位分钟(按步行速度5km/h)")
    private Integer walkingMinutes;
}
