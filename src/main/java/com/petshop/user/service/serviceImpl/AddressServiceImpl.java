package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.petshop.common.BusinessException;
import com.petshop.common.ResultCode;
import com.petshop.user.entity.Address;
import com.petshop.user.mapper.AddressMapper;
import com.petshop.user.model.dto.AddressDTO;
import com.petshop.user.model.vo.AddressVO;
import com.petshop.user.service.AddressService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;

    @Override
    public List<AddressVO> listByUserId(Long userId) {
        QueryWrapper<Address> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
          .orderByDesc("is_default")
          .orderByDesc("create_time");
        return addressMapper.selectList(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AddressVO create(Long userId, AddressDTO dto) {
        // 如果设为首选，先清除其他默认地址
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearUserDefault(userId);
        }

        Address addr = new Address();
        addr.setUserId(userId);
        addr.setReceiver(dto.getReceiver());
        addr.setPhone(dto.getPhone());
        addr.setProvince(dto.getProvince());
        addr.setCity(dto.getCity());
        addr.setDistrict(dto.getDistrict());
        addr.setDetail(dto.getDetail());
        addr.setLongitude(dto.getLongitude());
        addr.setLatitude(dto.getLatitude());
        addr.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 0);

        addressMapper.insert(addr);
        return toVO(addr);
    }

    @Override
    @Transactional
    public void update(Long userId, Long addressId, AddressDTO dto) {
        Address addr = addressMapper.selectById(addressId);
        if (addr == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!addr.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 如果本次要设为默认，先清除其他默认
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearUserDefault(userId);
        }

        if (dto.getReceiver() != null) addr.setReceiver(dto.getReceiver());
        if (dto.getPhone() != null) addr.setPhone(dto.getPhone());
        if (dto.getProvince() != null) addr.setProvince(dto.getProvince());
        if (dto.getCity() != null) addr.setCity(dto.getCity());
        if (dto.getDistrict() != null) addr.setDistrict(dto.getDistrict());
        if (dto.getDetail() != null) addr.setDetail(dto.getDetail());
        if (dto.getLongitude() != null) addr.setLongitude(dto.getLongitude());
        if (dto.getLatitude() != null) addr.setLatitude(dto.getLatitude());
        if (dto.getIsDefault() != null) addr.setIsDefault(dto.getIsDefault());

        addressMapper.updateById(addr);
    }

    @Override
    public void delete(Long userId, Long addressId) {
        Address addr = addressMapper.selectById(addressId);
        if (addr == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!addr.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        addressMapper.deleteById(addressId);
    }

    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {
        Address addr = addressMapper.selectById(addressId);
        if (addr == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!addr.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 清除该用户所有默认地址
        clearUserDefault(userId);

        // 设置目标地址为默认
        addr.setIsDefault(1);
        addressMapper.updateById(addr);
    }

    /** 将用户的所有地址设为非默认 */
    private void clearUserDefault(Long userId) {
        UpdateWrapper<Address> uw = new UpdateWrapper<>();
        uw.eq("user_id", userId)
          .set("is_default", 0);
        addressMapper.update(null, uw);
    }

    /** Address 实体 -> AddressVO */
    private AddressVO toVO(Address addr) {
        AddressVO vo = new AddressVO();
        BeanUtils.copyProperties(addr, vo);
        return vo;
    }
}
