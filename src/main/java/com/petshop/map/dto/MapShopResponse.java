package com.petshop.map.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ApiModel("地图商店点位")
public class MapShopResponse {

    @ApiModelProperty("商店ID")
    private Long id;

    @ApiModelProperty("商店名称")
    private String name;

    @ApiModelProperty("联系电话")
    private String phone;

    @ApiModelProperty("商店Logo")
    private String logo;

    @ApiModelProperty("经度")
    private BigDecimal longitude;

    @ApiModelProperty("纬度")
    private BigDecimal latitude;

    @ApiModelProperty("完整地址")
    private String address;

    @ApiModelProperty("距离，单位公里")
    private BigDecimal distanceKm;
}
