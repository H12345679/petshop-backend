package com.petshop.user.service;

import com.petshop.user.model.dto.AddressDTO;
import com.petshop.user.model.vo.AddressVO;

import java.util.List;

public interface AddressService {

    /** 查询当前用户的收货地址列表 */
    List<AddressVO> listByUserId(Long userId);

    /** 新增收货地址 */
    AddressVO create(Long userId, AddressDTO dto);

    /** 修改收货地址 */
    void update(Long userId, Long addressId, AddressDTO dto);

    /** 删除收货地址 */
    void delete(Long userId, Long addressId);

    /** 设为默认地址 */
    void setDefault(Long userId, Long addressId);
}
