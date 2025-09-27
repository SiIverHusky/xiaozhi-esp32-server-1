package xiaozhi.modules.sys.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import xiaozhi.common.utils.DateUtils;

/**
 * 用户高级订阅DTO
 */
@Data
@Schema(description = "User Premium Subscription")
public class UserPremiumSubscriptionDTO implements Serializable {
    
    @Schema(description = "Subscription ID")
    private Long id;
    
    @Schema(description = "User ID")
    @NotNull(message = "User ID cannot be null")
    private Long userId;
    
    @Schema(description = "Subscription type: MONTHLY, YEARLY")
    private String subscriptionType = "MONTHLY";
    
    @Schema(description = "Payment amount")
    @Positive(message = "Payment amount must be positive")
    private BigDecimal paymentAmount;
    
    @Schema(description = "Payment currency")
    private String paymentCurrency = "USD";
    
    @Schema(description = "Payment method: STRIPE, PAYPAL, WECHAT, ALIPAY")
    private String paymentMethod;
    
    @Schema(description = "Payment transaction ID")
    private String paymentTransactionId;
    
    @Schema(description = "Subscription start date")
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date subscriptionStartDate;
    
    @Schema(description = "Subscription end date")
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date subscriptionEndDate;
    
    @Schema(description = "Status: ACTIVE, EXPIRED, CANCELLED, REFUNDED")
    private String status = "ACTIVE";
    
    @Schema(description = "Auto renewal: 0=no, 1=yes")
    private Integer autoRenew = 0;
    
    @Schema(description = "Creation date")
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date createdDate;
    
    @Schema(description = "Update date")
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date updatedDate;
}