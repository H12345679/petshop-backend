package com.petshop.map.controller;

import com.petshop.common.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 地图配置控制器，用于动态安全下发高德地图前端 Key 与安全密钥
 */
@Tag(name = "D-地图LBS")
@RestController
@RequestMapping("/api/map")
public class MapConfigController {

    private final String amapKey;
    private final String amapSecurityJsCode;

    public MapConfigController(
            @Value("${amap.key:}") String amapKey,
            @Value("${amap.security-js-code:}") String amapSecurityJsCode) {
        this.amapKey = amapKey;
        this.amapSecurityJsCode = amapSecurityJsCode;
    }

    @Operation(summary = "获取高德地图前端配置")
    @GetMapping("/config")
    public Result<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>(2);
        config.put("key", this.amapKey);
        config.put("securityJsCode", this.amapSecurityJsCode);
        return Result.success(config);
    }
}
