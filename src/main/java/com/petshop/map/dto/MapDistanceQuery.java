package com.petshop.map.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@ApiModel("两点距离计算参数")
public class MapDistanceQuery {

    @NotNull(message = "起点经度不能为空")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @ApiModelProperty(value = "起点经度", required = true, example = "113.9526")
    private BigDecimal fromLongitude;

    @NotNull(message = "起点纬度不能为空")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @ApiModelProperty(value = "起点纬度", required = true, example = "22.5362")
    private BigDecimal fromLatitude;

    @NotNull(message = "终点经度不能为空")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @ApiModelProperty(value = "终点经度", required = true, example = "114.0699")
    private BigDecimal toLongitude;

    @NotNull(message = "终点纬度不能为空")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @ApiModelProperty(value = "终点纬度", required = true, example = "22.5429")
    private BigDecimal toLatitude;
}
