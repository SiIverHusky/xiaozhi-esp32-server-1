package xiaozhi.modules.sys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import xiaozhi.common.annotation.LogOperation;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.sys.dto.UserPremiumSubscriptionDTO;
import xiaozhi.modules.sys.service.UserPremiumSubscriptionService;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * 用户高级订阅管理
 */
@RestController
@RequestMapping("/admin/premium")
@Tag(name = "Premium Subscription Management")
@AllArgsConstructor
public class PremiumSubscriptionController {
    
    private final UserPremiumSubscriptionService premiumSubscriptionService;
    
    @GetMapping("page")
    @Operation(summary = "Premium subscription pagination")
    public Result<PageData<UserPremiumSubscriptionDTO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        PageData<UserPremiumSubscriptionDTO> page = premiumSubscriptionService.page(params);
        return new Result<PageData<UserPremiumSubscriptionDTO>>().ok(page);
    }
    
    @GetMapping("user/{userId}")
    @Operation(summary = "Get active subscription by user ID")
    public Result<UserPremiumSubscriptionDTO> getActiveSubscription(@PathVariable Long userId) {
        UserPremiumSubscriptionDTO subscription = premiumSubscriptionService.getActiveSubscriptionByUserId(userId);
        return new Result<UserPremiumSubscriptionDTO>().ok(subscription);
    }
    
    @PostMapping
    @Operation(summary = "Create premium subscription")
    @LogOperation("Create Premium Subscription")
    public Result<Void> createSubscription(@RequestBody @Valid UserPremiumSubscriptionDTO dto) {
        premiumSubscriptionService.createSubscription(dto);
        return new Result<Void>().ok(null);
    }
    
    @PutMapping("status/{id}")
    @Operation(summary = "Update subscription status")
    @LogOperation("Update Subscription Status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        premiumSubscriptionService.updateSubscriptionStatus(id, status);
        return new Result<Void>().ok(null);
    }
    
    @PostMapping("check-status/{userId}")
    @Operation(summary = "Check and update user premium status")
    public Result<Boolean> checkPremiumStatus(@PathVariable Long userId) {
        boolean isPremium = premiumSubscriptionService.checkAndUpdateUserPremiumStatus(userId);
        return new Result<Boolean>().ok(isPremium);
    }
    
    @GetMapping("is-premium/{userId}")
    @Operation(summary = "Check if user is premium")
    public Result<Boolean> isUserPremium(@PathVariable Long userId) {
        boolean isPremium = premiumSubscriptionService.isUserPremium(userId);
        return new Result<Boolean>().ok(isPremium);
    }
    
    @PostMapping("validate-transaction/{transactionId}")
    @Operation(summary = "Validate subscription by transaction ID")
    public Result<Boolean> validateTransaction(@PathVariable String transactionId) {
        boolean isValid = premiumSubscriptionService.validateSubscriptionByTransactionId(transactionId);
        return new Result<Boolean>().ok(isValid);
    }
    
    @PostMapping("check-expiring")
    @Operation(summary = "Check expiring subscriptions")
    @LogOperation("Check Expiring Subscriptions")
    public Result<Void> checkExpiringSubscriptions() {
        premiumSubscriptionService.checkExpiringSubscriptions();
        return new Result<Void>().ok(null);
    }
}