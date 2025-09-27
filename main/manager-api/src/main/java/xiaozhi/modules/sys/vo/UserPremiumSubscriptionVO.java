package xiaozhi.modules.sys.vo;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.common.utils.DateUtils;

/**
 * User premium subscription display VO
 */
@Data
@Schema(description = "User premium subscription information")
public class UserPremiumSubscriptionVO {
    
    @Schema(description = "Subscription ID")
    private Long id;
    
    @Schema(description = "User ID")
    private Long userId;
    
    @Schema(description = "User phone number")
    private String userMobile;
    
    @Schema(description = "Subscription type")
    private String subscriptionType;
    
    @Schema(description = "Payment amount")
    private BigDecimal paymentAmount;
    
    @Schema(description = "Payment currency")
    private String paymentCurrency;
    
    @Schema(description = "Payment method")
    private String paymentMethod;
    
    @Schema(description = "Payment transaction ID")
    private String paymentTransactionId;
    
    @Schema(description = "Subscription start date")
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date subscriptionStartDate;
    
    @Schema(description = "Subscription end date")
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date subscriptionEndDate;
    
    @Schema(description = "Status")
    private String status;
    
    @Schema(description = "Auto renewal")
    private Integer autoRenew;
    
    @Schema(description = "Days until expiry")
    private Long daysUntilExpiry;
    
    @Schema(description = "Is currently active")
    private Boolean isActive;
    
    @Schema(description = "Creation date")
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date createdDate;
}