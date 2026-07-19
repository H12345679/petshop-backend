package com.petshop.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Schema(description = "两点距离计算参数")
public class MapDistanceQuery {

    @NotNull(message = "起点经度不能为空")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(description = "起点经度", example = "113.9526")
    private BigDecimal fromLongitude;

    @NotNull(message = "起点纬度不能为空")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(description = "起点纬度", example = "22.5362")
    private BigDecimal fromLatitude;

    @NotNull(message = "终点经度不能为空")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(description = "终点经度", example = "114.0699")
    private BigDecimal toLongitude;

    @NotNull(message = "终点纬度不能为空")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(description = "终点纬度", example = "22.5429")
    private BigDecimal toLatitude;
}
