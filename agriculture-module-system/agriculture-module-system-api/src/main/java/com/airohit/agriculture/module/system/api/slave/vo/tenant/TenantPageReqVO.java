package com.airohit.agriculture.module.system.api.slave.vo.tenant;

import com.airohit.agriculture.framework.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static com.airohit.agriculture.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@ApiModel("管理后台 - 租户分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TenantPageReqVO extends PageParam {

    @ApiModelProperty(value = "租户名", example = "农业")
    private String name;

    @ApiModelProperty(value = "联系人", example = "shiminghao")
    private String contactName;

    @ApiModelProperty(value = "联系手机", example = "15601691300")
    private String contactMobile;

    @ApiModelProperty(value = "租户状态（0正常 1停用）", example = "1")
    private Integer status;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime[] createTime;

}
