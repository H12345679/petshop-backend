package com.petshop.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "地图商店点位")
public class MapShopResponse {

    @Schema(description = "商店ID")
    private Long id;

    @Schema(description = "商店名称")
    private String name;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "商店Logo")
    private String logo;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "完整地址")
    private String address;

    @Schema(description = "距离，单位公里")
    private BigDecimal distanceKm;
}
