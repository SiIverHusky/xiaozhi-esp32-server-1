package xiaozhi.modules.sys.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 用户高级订阅记录
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user_premium_subscription")
public class UserPremiumSubscriptionEntity extends BaseEntity {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 订阅类型：MONTHLY, YEARLY
     */
    private String subscriptionType;
    
    /**
     * 支付金额
     */
    private BigDecimal paymentAmount;
    
    /**
     * 支付货币
     */
    private String paymentCurrency;
    
    /**
     * 支付方式：STRIPE, PAYPAL, WECHAT, ALIPAY
     */
    private String paymentMethod;
    
    /**
     * 外部支付交易ID
     */
    private String paymentTransactionId;
    
    /**
     * 订阅开始日期
     */
    private Date subscriptionStartDate;
    
    /**
     * 订阅结束日期
     */
    private Date subscriptionEndDate;
    
    /**
     * 状态：ACTIVE, EXPIRED, CANCELLED, REFUNDED
     */
    private String status;
    
    /**
     * 自动续费：0=否，1=是
     */
    private Integer autoRenew;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdDate;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedDate;
    
    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private Long creator;
    
    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;
}