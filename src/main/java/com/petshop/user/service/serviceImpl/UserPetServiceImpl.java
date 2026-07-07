package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.ResultCode;
import com.petshop.security.UserContext;
import com.petshop.user.entity.UserPet;
import com.petshop.user.mapper.UserPetMapper;
import com.petshop.user.service.UserPetService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/** 用户宠物档案 Service 实现 */
@Service
public class UserPetServiceImpl extends ServiceImpl<UserPetMapper, UserPet> implements UserPetService {

    /** 每个用户最多可建的宠物档案数 */
    private static final int MAX_PETS_PER_USER = 10;

    @Override
    public List<UserPet> myPets() {
        Long userId = requireUserId();
        return this.list(new LambdaQueryWrapper<UserPet>()
                .eq(UserPet::getUserId, userId)
                .orderByAsc(UserPet::getCreateTime));
    }

    @Override
    public void addPet(UserPet pet) {
        Long userId = requireUserId();
        validate(pet);
        long count = this.count(new LambdaQueryWrapper<UserPet>().eq(UserPet::getUserId, userId));
        if (count >= MAX_PETS_PER_USER) {
            throw new BusinessException("最多可添加 " + MAX_PETS_PER_USER + " 只宠物");
        }
        pet.setId(null);
        pet.setUserId(userId);
        this.save(pet);
    }

    @Override
    public void updatePet(Long id, UserPet pet) {
        UserPet db = requireOwned(id);
        validate(pet);
        pet.setId(db.getId());
        pet.setUserId(db.getUserId());
        this.updateById(pet);
    }

    @Override
    public void deletePet(Long id) {
        requireOwned(id);
        this.removeById(id);
    }

    @Override
    public List<String> petTagNames(Long userId) {
        List<String> tags = new ArrayList<>();
        if (userId == null) return tags;
        List<UserPet> pets = this.list(new LambdaQueryWrapper<UserPet>().eq(UserPet::getUserId, userId));
        for (UserPet p : pets) {
            addSpeciesTag(p, tags);
            addAgeTag(p, tags);
            addSizeTag(p, tags);
        }
        return tags;
    }

    private void addSpeciesTag(UserPet p, List<String> tags) {
        String speciesTag = speciesTagName(p.getSpecies());
        if (speciesTag != null && !tags.contains(speciesTag)) tags.add(speciesTag);
    }

    private void addAgeTag(UserPet p, List<String> tags) {
        if (p.getBirthday() == null) return;
        int years = Period.between(p.getBirthday(), LocalDate.now()).getYears();
        String ageTag = years < 1 ? "幼年" : (years <= 7 ? "成年" : "老年");
        if (!tags.contains(ageTag)) tags.add(ageTag);
    }

    private void addSizeTag(UserPet p, List<String> tags) {
        if (p.getSpecies() == null || p.getSpecies() != 2 || p.getWeightKg() == null) return;
        String sizeTag = p.getWeightKg().doubleValue() >= 15 ? "大型犬" : "小型犬";
        if (!tags.contains(sizeTag)) tags.add(sizeTag);
    }

    @Override
    public List<Integer> petSpecies(Long userId) {
        List<Integer> species = new ArrayList<>();
        if (userId == null) return species;
        List<UserPet> pets = this.list(new LambdaQueryWrapper<UserPet>().eq(UserPet::getUserId, userId));
        for (UserPet p : pets) {
            if (p.getSpecies() != null && !species.contains(p.getSpecies())) {
                species.add(p.getSpecies());
            }
        }
        return species;
    }

    // ========== 内部 ==========

    private String speciesTagName(Integer species) {
        if (species == null) return null;
        switch (species) {
            case 1: return "猫咪";
            case 2: return "狗狗";
            case 3: return "兔子";
            case 4: return "鸟类";
            default: return null;
        }
    }

    private void validate(UserPet pet) {
        if (pet == null || pet.getSpecies() == null) {
            throw new BusinessException("请选择宠物种类");
        }
        int s = pet.getSpecies();
        if (s != 1 && s != 2 && s != 3 && s != 4 && s != 9) {
            throw new BusinessException("宠物种类不合法");
        }
        if (pet.getBirthday() != null && pet.getBirthday().isAfter(LocalDate.now())) {
            throw new BusinessException("宠物生日不能晚于今天");
        }
    }

    private UserPet requireOwned(Long id) {
        Long userId = requireUserId();
        UserPet db = this.getById(id);
        if (db == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(db.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);
        return db;
    }

    private Long requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return uid;
    }
}
