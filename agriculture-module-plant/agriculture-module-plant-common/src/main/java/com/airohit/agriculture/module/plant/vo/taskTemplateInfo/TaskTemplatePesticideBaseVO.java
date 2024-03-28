package com.airohit.agriculture.module.plant.vo.taskTemplateInfo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 打药 Base VO，提供给添加、修改、详细的子 VO 使用
 * 如果子 VO 存在差异的字段，请不要添加到这里，影响 Swagger 文档生成
 */
@Data
@ApiModel("打药")
public class TaskTemplatePesticideBaseVO {

    @ApiModelProperty(value = "任务基本信息id")
    private Integer agroTaskTemplateId;

    @ApiModelProperty(value = "药品名称")
    private String pesticideName;

    @ApiModelProperty(value = "药品用量")
    private String pesticideDosage;

    @ApiModelProperty(value = "农机")
    private String farmMachinery;

}
