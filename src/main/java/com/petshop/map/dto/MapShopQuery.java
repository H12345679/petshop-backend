package com.petshop.map.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@ApiModel("地图附近商店查询参数")
public class MapShopQuery {

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度不能小于-180")
    @DecimalMax(value = "180.0", message = "经度不能大于180")
    @ApiModelProperty(value = "用户当前经度", required = true, example = "113.943123")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度不能小于-90")
    @DecimalMax(value = "90.0", message = "纬度不能大于90")
    @ApiModelProperty(value = "用户当前纬度", required = true, example = "22.540124")
    private BigDecimal latitude;

    @DecimalMin(value = "0.1", message = "搜索半径必须大于0")
    @DecimalMax(value = "100.0", message = "搜索半径不能超过100公里")
    @ApiModelProperty(value = "搜索半径，单位公里", example = "5")
    private BigDecimal radius = BigDecimal.valueOf(5);

    @Min(value = 1, message = "返回数量至少为1")
    @Max(value = 200, message = "返回数量不能超过200")
    @ApiModelProperty(value = "返回数量限制", example = "50")
    private Integer limit = 50;
}
