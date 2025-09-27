package xiaozhi.modules.sys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import xiaozhi.common.utils.Result;
import xiaozhi.modules.sys.dto.UserPremiumSubscriptionDTO;
import xiaozhi.modules.sys.service.UserPremiumSubscriptionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * 支付回调处理控制器
 * 用于处理来自Stripe、PayPal等支付服务的webhook通知
 */
@RestController
@RequestMapping("/admin/payment")
@Tag(name = "Payment Webhook Management")
@AllArgsConstructor
@Slf4j
public class PaymentWebhookController {
    
    private final UserPremiumSubscriptionService premiumSubscriptionService;
    
    @PostMapping("webhook/stripe")
    @Operation(summary = "Handle Stripe webhook notifications")
    public Result<String> handleStripeWebhook(@RequestBody Map<String, Object> payload,
                                              @RequestHeader("Stripe-Signature") String signature) {
        try {
            log.info("Received Stripe webhook: {}", payload.get("type"));
            
            String eventType = (String) payload.get("type");
            
            if ("checkout.session.completed".equals(eventType) || "payment_intent.succeeded".equals(eventType)) {
                handleSuccessfulPayment(payload, "STRIPE");
            } else if ("invoice.payment_failed".equals(eventType)) {
                handleFailedPayment(payload, "STRIPE");
            }
            
            return new Result<String>().ok("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Error processing Stripe webhook: {}", e.getMessage(), e);
            return new Result<String>().error("Webhook processing failed: " + e.getMessage());
        }
    }
    
    @PostMapping("webhook/paypal")
    @Operation(summary = "Handle PayPal webhook notifications")
    public Result<String> handlePayPalWebhook(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Received PayPal webhook: {}", payload.get("event_type"));
            
            String eventType = (String) payload.get("event_type");
            
            if ("PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
                handleSuccessfulPayment(payload, "PAYPAL");
            }
            
            return new Result<String>().ok("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Error processing PayPal webhook: {}", e.getMessage(), e);
            return new Result<String>().error("Webhook processing failed: " + e.getMessage());
        }
    }
    
    @PostMapping("subscription/create")
    @Operation(summary = "Manually create premium subscription (for testing)")
    public Result<String> createTestSubscription(@RequestParam Long userId,
                                                 @RequestParam String subscriptionType,
                                                 @RequestParam BigDecimal amount) {
        try {
            UserPremiumSubscriptionDTO subscription = new UserPremiumSubscriptionDTO();
            subscription.setUserId(userId);
            subscription.setSubscriptionType(subscriptionType);
            subscription.setPaymentAmount(amount);
            subscription.setPaymentCurrency("USD");
            subscription.setPaymentMethod("TEST");
            subscription.setPaymentTransactionId("test_" + System.currentTimeMillis());
            subscription.setSubscriptionStartDate(new Date());
            
            // Set end date based on subscription type
            LocalDateTime endDate = LocalDateTime.now();
            if ("MONTHLY".equals(subscriptionType)) {
                endDate = endDate.plusMonths(1);
            } else if ("YEARLY".equals(subscriptionType)) {
                endDate = endDate.plusYears(1);
            } else {
                endDate = endDate.plusDays(30); // Default to 30 days
            }
            subscription.setSubscriptionEndDate(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
            
            premiumSubscriptionService.createSubscription(subscription);
            
            return new Result<String>().ok("Test subscription created successfully");
        } catch (Exception e) {
            log.error("Error creating test subscription: {}", e.getMessage(), e);
            return new Result<String>().error("Failed to create subscription: " + e.getMessage());
        }
    }
    
    private void handleSuccessfulPayment(Map<String, Object> payload, String paymentMethod) {
        try {
            // Extract payment information from payload
            // This is a simplified example - real implementation would parse the specific structure
            
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) return;
            
            Map<String, Object> object = (Map<String, Object>) data.get("object");
            if (object == null) return;
            
            // Extract relevant information
            String transactionId = (String) object.get("id");
            Object amountObj = object.get("amount_total");
            Long amountCents = null;
            
            if (amountObj instanceof Integer) {
                amountCents = ((Integer) amountObj).longValue();
            } else if (amountObj instanceof Long) {
                amountCents = (Long) amountObj;
            }
            
            if (transactionId == null || amountCents == null) {
                log.warn("Missing required payment information in webhook payload");
                return;
            }
            
            // Convert cents to dollars
            BigDecimal paymentAmount = new BigDecimal(amountCents).divide(new BigDecimal(100));
            
            // Extract user ID from metadata (you would need to include this when creating the payment)
            Map<String, Object> metadata = (Map<String, Object>) object.get("metadata");
            if (metadata == null) {
                log.warn("No metadata found in payment webhook");
                return;
            }
            
            String userIdStr = (String) metadata.get("user_id");
            String subscriptionType = (String) metadata.get("subscription_type");
            
            if (userIdStr == null) {
                log.warn("No user_id found in payment metadata");
                return;
            }
            
            Long userId = Long.parseLong(userIdStr);
            
            // Create premium subscription
            UserPremiumSubscriptionDTO subscription = new UserPremiumSubscriptionDTO();
            subscription.setUserId(userId);
            subscription.setSubscriptionType(subscriptionType != null ? subscriptionType : "MONTHLY");
            subscription.setPaymentAmount(paymentAmount);
            subscription.setPaymentCurrency("USD");
            subscription.setPaymentMethod(paymentMethod);
            subscription.setPaymentTransactionId(transactionId);
            subscription.setSubscriptionStartDate(new Date());
            
            // Set end date based on subscription type
            LocalDateTime endDate = LocalDateTime.now();
            if ("YEARLY".equals(subscription.getSubscriptionType())) {
                endDate = endDate.plusYears(1);
            } else {
                endDate = endDate.plusMonths(1);
            }
            subscription.setSubscriptionEndDate(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
            
            premiumSubscriptionService.createSubscription(subscription);
            
            log.info("Created premium subscription for user {} with transaction {}", userId, transactionId);
            
        } catch (Exception e) {
            log.error("Error handling successful payment: {}", e.getMessage(), e);
        }
    }
    
    private void handleFailedPayment(Map<String, Object> payload, String paymentMethod) {
        try {
            // Handle failed payments - could mark subscriptions as failed, send notifications, etc.
            log.info("Payment failed for method: {}", paymentMethod);
            // Implementation would depend on your business logic
        } catch (Exception e) {
            log.error("Error handling failed payment: {}", e.getMessage(), e);
        }
    }
}