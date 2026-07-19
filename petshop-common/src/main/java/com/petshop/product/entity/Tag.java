package com.petshop.product.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tag")
public class Tag {
    private Long id;
    private String name;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
