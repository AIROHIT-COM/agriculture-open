package com.airohit.agriculture.module.statistics.vo.message;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ApiModel("管理后台 - 预警消息更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WarningMessageUpdateReqVO extends WarningMessageBaseVO {

    @ApiModelProperty(value = "Id")
    private Integer id;

}
