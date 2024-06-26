package com.airohit.agriculture.module.infra.controller.admin.logger;

import com.airohit.agriculture.framework.common.pojo.CommonResult;
import com.airohit.agriculture.framework.common.pojo.PageResult;
import com.airohit.agriculture.framework.excel.core.util.ExcelUtils;
import com.airohit.agriculture.framework.operatelog.core.annotations.OperateLog;
import com.airohit.agriculture.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogExcelVO;
import com.airohit.agriculture.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogExportReqVO;
import com.airohit.agriculture.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogPageReqVO;
import com.airohit.agriculture.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogRespVO;
import com.airohit.agriculture.module.infra.convert.logger.ApiErrorLogConvert;
import com.airohit.agriculture.module.infra.dal.dataobject.logger.ApiErrorLogDO;
import com.airohit.agriculture.module.infra.service.logger.ApiErrorLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

import static com.airohit.agriculture.framework.common.pojo.CommonResult.success;
import static com.airohit.agriculture.framework.operatelog.core.enums.OperateTypeEnum.EXPORT;
import static com.airohit.agriculture.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Api(tags = "管理后台 - API 错误日志")
@RestController
@RequestMapping("/infra/api-error-log")
@Validated
public class ApiErrorLogController {

    @Resource
    private ApiErrorLogService apiErrorLogService;

    @PutMapping("/update-status")
    @ApiOperation("更新 API 错误日志的状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "编号", required = true, example = "1024", dataTypeClass = Long.class),
            @ApiImplicitParam(name = "processStatus", value = "处理状态", required = true, example = "1", dataTypeClass = Integer.class)
    })
    @PreAuthorize("@ss.hasPermission('infra:api-error-log:update-status')")
    public CommonResult<Boolean> updateApiErrorLogProcess(@RequestParam("id") Long id,
                                                          @RequestParam("processStatus") Integer processStatus) {
        apiErrorLogService.updateApiErrorLogProcess(id, processStatus, getLoginUserId());
        return success(true);
    }

    @GetMapping("/page")
    @ApiOperation("获得 API 错误日志分页")
    @PreAuthorize("@ss.hasPermission('infra:api-error-log:query')")
    public CommonResult<PageResult<ApiErrorLogRespVO>> getApiErrorLogPage(@Valid ApiErrorLogPageReqVO pageVO) {
        PageResult<ApiErrorLogDO> pageResult = apiErrorLogService.getApiErrorLogPage(pageVO);
        return success(ApiErrorLogConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/export-excel")
    @ApiOperation("导出 API 错误日志 Excel")
    @PreAuthorize("@ss.hasPermission('infra:api-error-log:export')")
    @OperateLog(type = EXPORT)
    public void exportApiErrorLogExcel(@Valid ApiErrorLogExportReqVO exportReqVO,
                                       HttpServletResponse response) throws IOException {
        List<ApiErrorLogDO> list = apiErrorLogService.getApiErrorLogList(exportReqVO);
        // 导出 Excel
        List<ApiErrorLogExcelVO> datas = ApiErrorLogConvert.INSTANCE.convertList02(list);
        ExcelUtils.write(response, "API 错误日志.xls", "数据", ApiErrorLogExcelVO.class, datas);
    }

}
