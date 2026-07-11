package com.petshop.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Schema(description = "地图附近商店查询参数")
public class MapShopQuery {

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度不能小于-180")
    @DecimalMax(value = "180.0", message = "经度不能大于180")
    @Schema(description = "用户当前经度", example = "113.943123")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度不能小于-90")
    @DecimalMax(value = "90.0", message = "纬度不能大于90")
    @Schema(description = "用户当前纬度", example = "22.540124")
    private BigDecimal latitude;

    @DecimalMin(value = "0.1", message = "搜索半径必须大于0")
    @DecimalMax(value = "100.0", message = "搜索半径不能超过100公里")
    @Schema(description = "搜索半径，单位公里", example = "5")
    private BigDecimal radius = BigDecimal.valueOf(5);

    @Min(value = 1, message = "返回数量至少为1")
    @Max(value = 200, message = "返回数量不能超过200")
    @Schema(description = "返回数量限制", example = "50")
    private Integer limit = 50;
}
