package xiaozhi.modules.sys.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 系统用户
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user")
public class SysUserEntity extends BaseEntity {
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
    /**
     * 超级管理员 0：否 1：是
     */
    private Integer superAdmin;
    /**
     * 状态 0：停用 1：正常
     */
    private Integer status;
    /**
     * 自动禁用原因，用于区分手动禁用和自动禁用的账户
     */
    private String autoDisabledReason;
    /**
     * 当月聊天数量
     */
    private Integer chatCountMonth;
    /**
     * 上次重置月份 (YYYY-MM)
     */
    private String lastResetMonth;
    /**
     * 高级用户状态：0=普通用户，1=高级用户
     */
    private Integer isPremium;
    /**
     * 高级用户到期时间，NULL表示非高级用户
     */
    private Date premiumExpiresAt;
    /**
     * 上次高级状态检查时间
     */
    private Date premiumLastCheck;
    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateDate;

}