package com.petshop.map.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ApiModel("两点距离计算结果")
public class MapDistanceResponse {

    @ApiModelProperty("起点经度")
    private BigDecimal fromLongitude;

    @ApiModelProperty("起点纬度")
    private BigDecimal fromLatitude;

    @ApiModelProperty("终点经度")
    private BigDecimal toLongitude;

    @ApiModelProperty("终点纬度")
    private BigDecimal toLatitude;

    @ApiModelProperty("直线距离，单位公里")
    private BigDecimal distanceKm;

    @ApiModelProperty("预估驾车时长，单位分钟（按城市道路均速30km/h）")
    private Integer estimatedMinutes;

    @ApiModelProperty("预估步行时长，单位分钟（按步行速度5km/h）")
    private Integer walkingMinutes;
}
