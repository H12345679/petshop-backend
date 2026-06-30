package com.petshop.demo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 框架自检演示实体（对应 demo_item 表）。
 * 验证 BaseEntity 自动填充、逻辑删除、雪花 ID 等机制。
 * 正式上线前可删除本类及相关 Demo 代码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demo_item")
@ApiModel("演示实体")
public class DemoItem extends BaseEntity {

    @ApiModelProperty("名称")
    private String name;
}
