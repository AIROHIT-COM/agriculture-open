package com.airohit.agriculture.module.device.vo.farmyun;

import lombok.Data;

/**
 * Created with IDEA
 * 智慧环控2.0设备实时数据
 *
 * @author :shiminghao
 * @date :2023/5/5 13:53
 */
@Data
public class DeviceIrrigationTwoRealTimeVo {

    /**
     * 节点编号
     */
    private int nodeId;
    /**
     * 节点名称
     */
    private String nodeName;
    /**
     * 1: 模拟量1使能模拟量2使能
     * 2: 模拟量1使能模拟量2禁用
     * 3: 模拟量1禁用模拟量2使能
     * 4: 浮点型设备
     * 5: 开关量型设备
     * 6: 32位有符号整形
     * 7: 32位无符号整形
     * 8:遥调设备
     */
    private int nodeType;
    /**
     * 模拟量1名称
     */
    private String temName;
    /**
     * 模拟量1单位
     */
    private String temUnit;
    /**
     * 模拟量1原值
     */
    private double temValue;
    /**
     * 模拟量1显示值
     */
    private String temValueStr;
    /**
     * 报警等级
     * 0:正常
     * 1:超上限报警
     * 2:超下限报警
     * 3:开关闭合报警
     * 4:开关断开报警
     * 5:遥调报警
     */
    private int temAlarmStatus;
    /**
     * 模拟量2名称
     */
    private String humName;
    /**
     * 模拟量2单位
     */
    private String humUnit;
    /**
     * 模拟量2原值
     */
    private double humValue;
    /**
     * 模拟量2显示值
     */
    private String humValueStr;
    /**
     * 报警等级
     * 0:正常
     * 1:超上限报警
     * 2:超下限报警
     */
    private int humAlarmStatus;
    /**
     * 继电器状态 1:打开，0:关闭
     */
    private String valveStatus;
    /**
     * 运行模式   1为手动，2为自动，3为定时-定点，4为定时-星期 5为自动--平均值
     */
    private int factorMode;
    /**
     * 节点id
     */
    private String factorId;
    /**
     * 节点是否开启 0：关闭 1：开启
     */
    private int enable;
    /**
     * 1采集器 2阀门
     */
    private int factorType;
}
