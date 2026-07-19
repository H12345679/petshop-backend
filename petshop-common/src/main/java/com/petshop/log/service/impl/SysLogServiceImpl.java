package com.petshop.log.service.impl;

import com.petshop.log.entity.SysLog;
import com.petshop.log.service.SysLogService;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.log.mapper.SysLogMapper;
import org.springframework.stereotype.Service;

@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {
}
