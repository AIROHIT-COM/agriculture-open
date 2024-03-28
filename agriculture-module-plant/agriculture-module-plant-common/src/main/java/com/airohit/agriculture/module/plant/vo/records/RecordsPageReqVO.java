package com.airohit.agriculture.module.plant.vo.records;

import com.airohit.agriculture.framework.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

import static com.airohit.agriculture.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@ApiModel("管理后台 - 农事记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RecordsPageReqVO extends PageParam {

    @ApiModelProperty(value = "视频链接")
    private String videoUrl;

    @ApiModelProperty(value = "图片链接")
    private String imageUrls;

    @ApiModelProperty(value = "农事类型")
    private Integer type;

    @ApiModelProperty(value = "任务对应的表名")
    private String typeTableName;

    @ApiModelProperty(value = "任务对应的模型名称")
    private String typeModelName;

    @ApiModelProperty(value = "操作时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private Date[] operateTime;

    @ApiModelProperty(value = "状态")
    private Integer status;

    @ApiModelProperty(value = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private Date[] createTime;

    @ApiModelProperty(value = "备注")
    private String remark;

}
