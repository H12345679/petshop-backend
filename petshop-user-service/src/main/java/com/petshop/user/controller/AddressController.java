package com.petshop.user.controller;

import com.petshop.common.Result;
import com.petshop.security.RequireLogin;
import com.petshop.security.UserContext;
import com.petshop.user.model.dto.AddressDTO;
import com.petshop.user.model.vo.AddressVO;
import com.petshop.user.service.AddressService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 收货地址接口（需登录）。
 */
@Tag(name = "03-收货地址")
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    @Autowired
    private AddressService addressService;

    @Operation(summary = "收货地址列表")
    @RequireLogin
    @GetMapping
    public Result<List<AddressVO>> list() {
        Long userId = UserContext.getUserId();
        List<AddressVO> list = addressService.listByUserId(userId);
        return Result.success(list);
    }

    @Operation(summary = "新增收货地址")
    @RequireLogin
    @PostMapping
    public Result<AddressVO> create(@Valid @RequestBody AddressDTO dto) {
        Long userId = UserContext.getUserId();
        AddressVO vo = addressService.create(userId, dto);
        return Result.success(vo);
    }

    @Operation(summary = "修改收货地址")
    @RequireLogin
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AddressDTO dto) {
        Long userId = UserContext.getUserId();
        addressService.update(userId, id, dto);
        return Result.success();
    }

    @Operation(summary = "删除收货地址")
    @RequireLogin
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        addressService.delete(userId, id);
        return Result.success();
    }

    @Operation(summary = "设为默认地址")
    @RequireLogin
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        addressService.setDefault(userId, id);
        return Result.success();
    }
}
