# Premium Account Feature Implementation

This document describes the comprehensive implementation of the premium account functionality that allows users with active subscriptions to bypass monthly chat limits, including the user management interface enhancements and payment solution architecture.

## Overview

Premium accounts are users who have made payments for subscription services and are granted unlimited chat access during their subscription period. This feature integrates seamlessly with the existing chat limit system and includes:

- **Premium Status Display**: Visual indicators in user management interface
- **Automatic Chat Limit Bypass**: Premium users have unlimited chat access
- **Payment Integration**: Support for Stripe, PayPal, and other payment providers
- **Real-time Status Updates**: Premium status is checked during each interaction
- **Administrative Controls**: Full premium subscription management via admin APIs

## Implementation Status

### ✅ Completed Features

1. **Database Schema**: Complete premium subscription tracking system
2. **Backend Services**: Full premium subscription management service layer
3. **Chat Limit Integration**: Premium users bypass all chat restrictions
4. **User Management UI**: Premium status column with visual indicators
5. **Account Status API**: Enhanced with premium status information
6. **Xiaozhi-Server Integration**: Premium status recognized in Python server
7. **Payment Webhooks**: Stripe and PayPal webhook processing
8. **Administrative APIs**: Complete CRUD operations for subscriptions

### 🚧 In Progress

1. **Payment Frontend Integration**: Stripe/PayPal checkout flows
2. **Subscription Management UI**: User-facing subscription portal
3. **Email Notifications**: Subscription lifecycle communications

## Database Schema

### New Table: `user_premium_subscription`

This table tracks all premium subscription records for users.

```sql
CREATE TABLE user_premium_subscription (
    id bigint NOT NULL AUTO_INCREMENT COMMENT 'Subscription ID',
    user_id bigint NOT NULL COMMENT 'User ID from sys_user table',
    subscription_type varchar(50) NOT NULL DEFAULT 'MONTHLY' COMMENT 'Subscription type: MONTHLY, YEARLY',
    payment_amount decimal(10,2) NOT NULL COMMENT 'Payment amount',
    payment_currency varchar(10) NOT NULL DEFAULT 'USD' COMMENT 'Payment currency',
    payment_method varchar(50) COMMENT 'Payment method: STRIPE, PAYPAL, WECHAT, ALIPAY',
    payment_transaction_id varchar(255) COMMENT 'External payment transaction ID',
    subscription_start_date datetime NOT NULL COMMENT 'Subscription start date',
    subscription_end_date datetime NOT NULL COMMENT 'Subscription end date',
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, EXPIRED, CANCELLED, REFUNDED',
    auto_renew tinyint(1) DEFAULT 0 COMMENT 'Auto renewal enabled: 0=no, 1=yes',
    created_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation date',
    updated_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update date',
    creator bigint COMMENT 'Creator user ID',
    updater bigint COMMENT 'Updater user ID',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_subscription_dates (subscription_start_date, subscription_end_date),
    INDEX idx_status (status),
    INDEX idx_payment_transaction (payment_transaction_id),
    CONSTRAINT fk_premium_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User premium subscription records';
```

### Updated Table: `sys_user`

Added premium-related fields to the user table for performance optimization:

```sql
ALTER TABLE sys_user 
ADD COLUMN is_premium tinyint(1) DEFAULT 0 COMMENT 'Premium status: 0=regular, 1=premium',
ADD COLUMN premium_expires_at datetime NULL COMMENT 'Premium expiry date, NULL if not premium',
ADD COLUMN premium_last_check datetime NULL COMMENT 'Last premium status check timestamp';

CREATE INDEX idx_premium_status ON sys_user(is_premium, premium_expires_at);
```

## Database Schema

### New Table: `user_premium_subscription`

This table tracks all premium subscription records for users.

```sql
CREATE TABLE user_premium_subscription (
    id bigint NOT NULL AUTO_INCREMENT COMMENT 'Subscription ID',
    user_id bigint NOT NULL COMMENT 'User ID from sys_user table',
    subscription_type varchar(50) NOT NULL DEFAULT 'MONTHLY' COMMENT 'Subscription type: MONTHLY, YEARLY',
    payment_amount decimal(10,2) NOT NULL COMMENT 'Payment amount',
    payment_currency varchar(10) NOT NULL DEFAULT 'USD' COMMENT 'Payment currency',
    payment_method varchar(50) COMMENT 'Payment method: STRIPE, PAYPAL, WECHAT, ALIPAY',
    payment_transaction_id varchar(255) COMMENT 'External payment transaction ID',
    subscription_start_date datetime NOT NULL COMMENT 'Subscription start date',
    subscription_end_date datetime NOT NULL COMMENT 'Subscription end date',
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, EXPIRED, CANCELLED, REFUNDED',
    auto_renew tinyint(1) DEFAULT 0 COMMENT 'Auto renewal enabled: 0=no, 1=yes',
    created_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation date',
    updated_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update date',
    creator bigint COMMENT 'Creator user ID',
    updater bigint COMMENT 'Updater user ID',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_subscription_dates (subscription_start_date, subscription_end_date),
    INDEX idx_status (status),
    INDEX idx_payment_transaction (payment_transaction_id),
    CONSTRAINT fk_premium_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User premium subscription records';
```

### Updated Table: `sys_user`

Added premium-related fields to the user table for performance optimization:

```sql
ALTER TABLE sys_user 
ADD COLUMN is_premium tinyint(1) DEFAULT 0 COMMENT 'Premium status: 0=regular, 1=premium',
ADD COLUMN premium_expires_at datetime NULL COMMENT 'Premium expiry date, NULL if not premium',
ADD COLUMN premium_last_check datetime NULL COMMENT 'Last premium status check timestamp';

CREATE INDEX idx_premium_status ON sys_user(is_premium, premium_expires_at);
```

## Backend Implementation

### Core Classes

1. **UserPremiumSubscriptionEntity** - Entity class for subscription records with comprehensive payment tracking
2. **UserPremiumSubscriptionService** - Service interface with premium status checking and subscription management
3. **UserPremiumSubscriptionServiceImpl** - Service implementation with direct database queries to avoid circular dependencies
4. **UserPremiumSubscriptionDao** - Data access layer with custom SQL queries for active subscription retrieval
5. **PremiumSubscriptionController** (`/admin/premium`) - REST API endpoints for administrative subscription management
6. **PaymentWebhookController** (`/admin/payment`) - Webhook handler for Stripe/PayPal payment notifications

### Enhanced User Management

#### Premium Status Display
- **Location**: User Management page (`UserManagement.vue`)
- **Visual Design**: 
  - **Premium Users**: Yellow/warning "Premium" tag
  - **Regular Users**: Gray/info "Regular" tag
- **Data Source**: Real-time premium status from `AdminPageUserVO.isPremium`
- **Performance**: Direct DAO queries to avoid service layer circular dependencies

#### Account Status Integration
The `ConfigServiceImpl.checkAccountStatus()` method now returns enhanced information:

```java
public Map<String, Object> checkAccountStatus(String macAddress) {
    // ... existing code ...
    boolean isPremium = premiumSubscriptionService.isUserPremium(user.getId());
    
    result.put("accountDisabled", isDisabled);
    result.put("isPremium", isPremium);  // NEW: Premium status information
    // ... rest of method ...
}
```

### Key Features

#### Automatic Premium Status Checking

The system includes comprehensive premium status validation:

- **Chat Limit Bypass**: Premium users skip chat limit enforcement entirely in `AgentChatHistoryBizServiceImpl`
- **Real-time Validation**: Premium status checked during each chat interaction
- **Xiaozhi-Server Integration**: Python server receives and processes premium status
- **Performance Optimized**: Direct database queries minimize overhead

#### Chat Limit Integration

Enhanced `AgentChatHistoryBizServiceImpl.checkAndEnforceChatLimit()` with premium support:

```java
private void checkAndEnforceChatLimit(String macAddress) {
    // ... existing superadmin check ...
    
    // NEW: Check premium status - skip limits for premium users
    if (premiumSubscriptionService.isUserPremium(userId)) {
        log.info("=== CHAT LIMIT CHECK === User {} is premium, skipping chat limit", userId);
        return;
    }
    
    // ... existing chat limit enforcement ...
}
```

#### Xiaozhi-Server Premium Recognition

The Python server now handles premium status in `ConnectionHandler._check_account_status()`:

```python
def _check_account_status(self):
    status_result = check_account_status(self.device_id)
    if status_result:
        self.account_disabled = status_result.get("accountDisabled", False)
        is_premium = status_result.get("isPremium", False)
        
        if self.account_disabled:
            return False
        elif is_premium:
            self.logger.bind(tag=TAG).debug(f"Premium account for device {self.device_id}")
            return True  # Premium users bypass all restrictions
        # ... regular account handling ...
```

#### Payment Integration

The system supports webhook integration with popular payment providers:

- **Stripe Webhooks**: Handles `checkout.session.completed` and `payment_intent.succeeded` events
- **PayPal Webhooks**: Processes `PAYMENT.CAPTURE.COMPLETED` notifications  
- **Transaction Verification**: Validates payments using external transaction IDs
- **Automatic Subscription Creation**: Creates subscription records from successful payments

### API Endpoints

#### Premium Subscription Management (`/admin/premium`)

- `GET /admin/premium/page` - List subscriptions with pagination and filtering
- `GET /admin/premium/user/{userId}` - Get active subscription for a specific user
- `POST /admin/premium` - Create new premium subscription record
- `PUT /admin/premium/status/{id}` - Update subscription status (ACTIVE/EXPIRED/CANCELLED)
- `GET /admin/premium/is-premium/{userId}` - Check if user currently has premium status
- `POST /admin/premium/check-status/{userId}` - Manually trigger premium status update
- `POST /admin/premium/validate-transaction/{transactionId}` - Validate subscription by payment transaction ID
- `POST /admin/premium/check-expiring` - Process and update expiring subscriptions

#### Payment Webhooks (`/admin/payment`)

- `POST /admin/payment/webhook/stripe` - Handle Stripe webhook notifications with signature verification
- `POST /admin/payment/webhook/paypal` - Handle PayPal webhook notifications
- `POST /admin/payment/subscription/create` - Create test subscription (development/testing)

#### Enhanced Account Status API (`/config`)

- `POST /config/check-account-status` - Enhanced to include premium status information
  ```json
  {
    "accountDisabled": false,
    "isPremium": true,
    "reason": "Premium account active"
  }
  ```

#### User Management API (`/admin`)

- `GET /admin/users` - Enhanced user list now includes `isPremium` field in response
  ```json
  {
    "list": [
      {
        "userid": "1",
        "mobile": "1234567890", 
        "status": 1,
        "deviceCount": "2",
        "createDate": "2025-09-27T00:00:00.000Z",
        "isPremium": true
      }
    ]
  }
  ```

### Integration Points

#### Chat Limit Enforcement

The premium functionality integrates with existing chat limit enforcement in `AgentChatHistoryBizServiceImpl.checkAndEnforceChatLimit()`:

```java
private void checkAndEnforceChatLimit(String macAddress) {
    // ... device and user validation ...
    
    // Check superadmin status first
    if (Objects.equals(user.getSuperAdmin(), 1)) {
        log.info("=== CHAT LIMIT CHECK === User {} is superadmin, skipping chat limit", userId);
        return;
    }
    
    // NEW: Check premium status - skip limits for premium users  
    if (premiumSubscriptionService.isUserPremium(userId)) {
        log.info("=== CHAT LIMIT CHECK === User {} is premium, skipping chat limit", userId);
        return;
    }
    
    // Continue with regular chat limit enforcement for non-premium users
    // ... existing chat limit logic ...
}
```

#### User Management Interface Integration

Enhanced `UserManagement.vue` with premium status column:

```vue
<el-table-column label="Premium" prop="isPremium" align="center" width="100">
  <template slot-scope="scope">
    <el-tag v-if="scope.row.isPremium" type="warning" size="small">Premium</el-tag>
    <el-tag v-else type="info" size="small">Regular</el-tag>
  </template>
</el-table-column>
```

The frontend receives premium status through the enhanced user list API and displays it with clear visual indicators.

#### Xiaozhi-Server Integration

The Python xiaozhi-server now receives premium status information when checking account status:

```python
def _check_account_status(self):
    """Check if device owner's account is disabled due to chat limits"""
    if not self.read_config_from_api:
        return True
        
    try:
        if self.device_id:
            status_result = check_account_status(self.device_id)
            if status_result:
                self.account_disabled = status_result.get("accountDisabled", False)
                is_premium = status_result.get("isPremium", False)
                
                if self.account_disabled:
                    self.logger.bind(tag=TAG).warning(
                        f"Account disabled for device {self.device_id}: {self.account_disabled_reason}"
                    )
                    return False
                elif is_premium:
                    self.logger.bind(tag=TAG).debug(
                        f"Premium account for device {self.device_id}"
                    )
                    return True  # Premium users bypass all restrictions
                else:
                    return True  # Regular active account
```

#### Database Access Optimization

To avoid circular dependencies, the user list building process uses direct DAO queries:

```java
// In SysUserServiceImpl.page() method
List<AdminPageUserVO> list = page.getRecords().stream().map(user -> {
    // ... existing user data mapping ...
    
    // Check premium status using DAO directly to avoid circular dependency
    boolean isPremium = premiumSubscriptionDao.getActiveSubscriptionByUserId(user.getId()) != null;
    adminPageUserVO.setIsPremium(isPremium);
    
    return adminPageUserVO;
}).toList();
```

## Payment Solution Architecture

### Overview

The premium subscription system supports multiple payment providers through a unified webhook-based architecture. This design enables seamless integration with various payment gateways while maintaining security and reliability.

### Supported Payment Providers

1. **Stripe** - Primary payment processor with comprehensive webhook support
2. **PayPal** - Alternative payment method with business account integration
3. **WeChat Pay** - For Chinese market integration (planned)
4. **Alipay** - Additional Asian market support (planned)

### Payment Flow Architecture

#### 1. Frontend Payment Initiation

```javascript
// Example: Stripe Checkout Integration
const stripe = Stripe('pk_live_...');

// Create subscription checkout session
const createCheckoutSession = async (userId, plan) => {
  const response = await fetch('/api/create-checkout-session', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: userId,
      priceId: plan.priceId, // Stripe Price ID for the subscription plan
      successUrl: `${window.location.origin}/subscription/success`,
      cancelUrl: `${window.location.origin}/subscription/cancel`
    })
  });
  
  const session = await response.json();
  return stripe.redirectToCheckout({ sessionId: session.id });
};

// Metadata to include user information
const sessionMetadata = {
  user_id: userId.toString(),
  subscription_type: 'MONTHLY', // or 'YEARLY'
  device_id: deviceId // Optional: specific device association
};
```

#### 2. Backend Payment Session Creation

```java
@PostMapping("/create-checkout-session")
public Result<Map<String, String>> createCheckoutSession(@RequestBody CheckoutRequest request) {
    try {
        // Create Stripe checkout session
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(request.getSuccessUrl())
            .setCancelUrl(request.getCancelUrl())
            .putMetadata("user_id", request.getUserId().toString())
            .putMetadata("subscription_type", request.getSubscriptionType())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(request.getPriceId()) // Pre-configured price in Stripe
                    .setQuantity(1L)
                    .build()
            )
            .build();

        Session session = Session.create(params);
        
        Map<String, String> response = new HashMap<>();
        response.put("sessionId", session.getId());
        response.put("url", session.getUrl());
        
        return new Result<Map<String, String>>().ok(response);
    } catch (StripeException e) {
        return new Result<Map<String, String>>().error("Payment session creation failed: " + e.getMessage());
    }
}
```

#### 3. Webhook Processing

The system processes payment confirmations through secure webhooks:

##### Stripe Webhook Handler

```java
@PostMapping("/admin/payment/webhook/stripe")
public Result<String> handleStripeWebhook(
    @RequestBody String payload,
    @RequestHeader("Stripe-Signature") String sigHeader) {
    
    Event event = null;
    try {
        // Verify webhook signature for security
        event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
        log.error("Invalid signature");
        return new Result<String>().error("Invalid signature");
    }

    switch (event.getType()) {
        case "checkout.session.completed":
            Session session = (Session) event.getDataObjectDeserializer()
                .getObject().orElse(null);
            handleSuccessfulCheckout(session);
            break;
            
        case "invoice.payment_succeeded":
            Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);
            handleSuccessfulPayment(invoice);
            break;
            
        case "invoice.payment_failed":
            Invoice failedInvoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);
            handleFailedPayment(failedInvoice);
            break;
            
        case "customer.subscription.deleted":
            Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);
            handleSubscriptionCancellation(subscription);
            break;
    }
    
    return new Result<String>().ok("Webhook processed");
}

private void handleSuccessfulCheckout(Session session) {
    try {
        Map<String, String> metadata = session.getMetadata();
        Long userId = Long.parseLong(metadata.get("user_id"));
        String subscriptionType = metadata.get("subscription_type");
        
        // Create premium subscription record
        UserPremiumSubscriptionDTO subscription = new UserPremiumSubscriptionDTO();
        subscription.setUserId(userId);
        subscription.setSubscriptionType(subscriptionType);
        subscription.setPaymentAmount(new BigDecimal(session.getAmountTotal()).divide(new BigDecimal(100))); // Convert from cents
        subscription.setPaymentCurrency(session.getCurrency().toUpperCase());
        subscription.setPaymentMethod("STRIPE");
        subscription.setPaymentTransactionId(session.getId());
        subscription.setStatus("ACTIVE");
        
        // Set subscription dates
        LocalDateTime now = LocalDateTime.now();
        subscription.setSubscriptionStartDate(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()));
        
        LocalDateTime endDate = "YEARLY".equals(subscriptionType) 
            ? now.plusYears(1) 
            : now.plusMonths(1);
        subscription.setSubscriptionEndDate(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
        
        premiumSubscriptionService.createSubscription(subscription);
        
        log.info("Created premium subscription for user {} via Stripe", userId);
        
    } catch (Exception e) {
        log.error("Error creating subscription from Stripe checkout: {}", e.getMessage(), e);
    }
}
```

#### 4. PayPal Webhook Integration

```java
@PostMapping("/admin/payment/webhook/paypal")
public Result<String> handlePayPalWebhook(
    @RequestBody Map<String, Object> payload,
    @RequestHeader Map<String, String> headers) {
    
    try {
        // Verify PayPal webhook signature
        if (!verifyPayPalSignature(payload, headers)) {
            return new Result<String>().error("Invalid PayPal signature");
        }
        
        String eventType = (String) payload.get("event_type");
        
        switch (eventType) {
            case "PAYMENT.CAPTURE.COMPLETED":
                handlePayPalPaymentCompleted(payload);
                break;
            case "BILLING.SUBSCRIPTION.CREATED":
                handlePayPalSubscriptionCreated(payload);
                break;
            case "BILLING.SUBSCRIPTION.CANCELLED":
                handlePayPalSubscriptionCancelled(payload);
                break;
        }
        
        return new Result<String>().ok("PayPal webhook processed");
        
    } catch (Exception e) {
        log.error("Error processing PayPal webhook: {}", e.getMessage(), e);
        return new Result<String>().error("Webhook processing failed");
    }
}
```

### Security Implementation

#### 1. Webhook Signature Verification

```java
// Stripe signature verification
public boolean verifyStripeSignature(String payload, String sigHeader, String secret) {
    try {
        Event event = Webhook.constructEvent(payload, sigHeader, secret);
        return true;
    } catch (SignatureVerificationException e) {
        log.error("Stripe signature verification failed: {}", e.getMessage());
        return false;
    }
}

// PayPal signature verification
public boolean verifyPayPalSignature(Map<String, Object> payload, Map<String, String> headers) {
    try {
        String authAlgo = headers.get("PAYPAL-AUTH-ALGO");
        String transmission = headers.get("PAYPAL-TRANSMISSION-ID");
        String certId = headers.get("PAYPAL-CERT-ID");
        String signature = headers.get("PAYPAL-TRANSMISSION-SIG");
        String timestamp = headers.get("PAYPAL-TRANSMISSION-TIME");
        
        // Use PayPal SDK to verify webhook signature
        return PayPalWebhook.verify(authAlgo, transmission, certId, 
                                  signature, timestamp, payload);
    } catch (Exception e) {
        log.error("PayPal signature verification failed: {}", e.getMessage());
        return false;
    }
}
```

#### 2. Idempotency Protection

```java
@Service
public class WebhookIdempotencyService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String WEBHOOK_KEY_PREFIX = "webhook:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(1);
    
    public boolean isWebhookProcessed(String webhookId) {
        String key = WEBHOOK_KEY_PREFIX + webhookId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    public void markWebhookProcessed(String webhookId) {
        String key = WEBHOOK_KEY_PREFIX + webhookId;
        redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_TTL);
    }
}
```

### Subscription Plans Configuration

#### Predefined Subscription Plans

```java
public enum SubscriptionPlan {
    MONTHLY_BASIC("price_monthly_basic", "MONTHLY", new BigDecimal("9.99"), "USD"),
    YEARLY_BASIC("price_yearly_basic", "YEARLY", new BigDecimal("99.99"), "USD"),
    MONTHLY_PREMIUM("price_monthly_premium", "MONTHLY", new BigDecimal("19.99"), "USD"),
    YEARLY_PREMIUM("price_yearly_premium", "YEARLY", new BigDecimal("199.99"), "USD");
    
    private final String stripePriceId;
    private final String durationType;
    private final BigDecimal amount;
    private final String currency;
    
    // ... constructor and getters
}
```

#### Dynamic Pricing Support

```java
@RestController
@RequestMapping("/api/pricing")
public class PricingController {
    
    @GetMapping("/plans")
    public Result<List<PricingPlan>> getAvailablePlans(@RequestParam(required = false) String currency) {
        List<PricingPlan> plans = pricingService.getPlansForCurrency(currency != null ? currency : "USD");
        return new Result<List<PricingPlan>>().ok(plans);
    }
    
    @GetMapping("/plans/{region}")  
    public Result<List<PricingPlan>> getRegionalPlans(@PathVariable String region) {
        // Support for regional pricing (US, EU, CN, etc.)
        List<PricingPlan> plans = pricingService.getPlansForRegion(region);
        return new Result<List<PricingPlan>>().ok(plans);
    }
}
```

### Error Handling & Retry Logic

#### Webhook Retry Mechanism

```java
@Component
public class WebhookRetryHandler {
    
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void processWebhookWithRetry(String webhookId, Runnable processor) {
        try {
            if (!webhookIdempotencyService.isWebhookProcessed(webhookId)) {
                processor.run();
                webhookIdempotencyService.markWebhookProcessed(webhookId);
            }
        } catch (Exception e) {
            log.error("Webhook processing failed for ID {}: {}", webhookId, e.getMessage());
            throw e; // Trigger retry
        }
    }
    
    @Recover
    public void handleWebhookFailure(Exception e, String webhookId, Runnable processor) {
        log.error("Webhook processing permanently failed for ID {}: {}", webhookId, e.getMessage());
        // Send alert to administrators
        alertService.sendWebhookFailureAlert(webhookId, e.getMessage());
    }
}
```

### Testing & Development Tools

#### Webhook Testing Endpoints

```java
@RestController
@RequestMapping("/admin/payment/test")
@Profile({"dev", "test"}) // Only available in development
public class PaymentTestController {
    
    @PostMapping("/create-test-subscription")
    public Result<String> createTestSubscription(
            @RequestParam Long userId,
            @RequestParam String subscriptionType,
            @RequestParam BigDecimal amount) {
        
        UserPremiumSubscriptionDTO subscription = new UserPremiumSubscriptionDTO();
        subscription.setUserId(userId);
        subscription.setSubscriptionType(subscriptionType);
        subscription.setPaymentAmount(amount);
        subscription.setPaymentCurrency("USD");
        subscription.setPaymentMethod("TEST");
        subscription.setPaymentTransactionId("test_" + System.currentTimeMillis());
        subscription.setStatus("ACTIVE");
        
        LocalDateTime now = LocalDateTime.now();
        subscription.setSubscriptionStartDate(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()));
        
        LocalDateTime endDate = "YEARLY".equals(subscriptionType) 
            ? now.plusYears(1) 
            : now.plusMonths(1);
        subscription.setSubscriptionEndDate(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
        
        premiumSubscriptionService.createSubscription(subscription);
        
        return new Result<String>().ok("Test subscription created successfully");
    }
    
    @PostMapping("/simulate-webhook/{provider}")
    public Result<String> simulateWebhook(@PathVariable String provider, @RequestBody Map<String, Object> payload) {
        try {
            switch (provider.toLowerCase()) {
                case "stripe":
                    return paymentWebhookController.handleStripeWebhook(payload, Map.of());
                case "paypal":
                    return paymentWebhookController.handlePayPalWebhook(payload, Map.of());
                default:
                    return new Result<String>().error("Unsupported provider: " + provider);
            }
        } catch (Exception e) {
            return new Result<String>().error("Webhook simulation failed: " + e.getMessage());
        }
    }
}
```

### Monitoring & Analytics

#### Payment Metrics Tracking

```java
@Component
public class PaymentMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter successfulPayments;
    private final Counter failedPayments;
    private final Timer webhookProcessingTime;
    
    public PaymentMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.successfulPayments = Counter.builder("payments.successful")
            .description("Number of successful payments")
            .tag("provider", "all")
            .register(meterRegistry);
            
        this.failedPayments = Counter.builder("payments.failed")
            .description("Number of failed payments")
            .register(meterRegistry);
            
        this.webhookProcessingTime = Timer.builder("webhook.processing.time")
            .description("Time taken to process webhooks")
            .register(meterRegistry);
    }
    
    public void recordSuccessfulPayment(String provider, BigDecimal amount, String currency) {
        successfulPayments.increment(
            Tags.of(
                "provider", provider,
                "currency", currency
            )
        );
        
        meterRegistry.gauge("payment.amount", Tags.of("provider", provider), amount.doubleValue());
    }
    
    public void recordFailedPayment(String provider, String reason) {
        failedPayments.increment(
            Tags.of(
                "provider", provider,
                "reason", reason
            )
        );
    }
}
```

This payment solution architecture provides:

1. **Multi-provider Support**: Seamless integration with Stripe, PayPal, and future payment providers
2. **Security**: Webhook signature verification and idempotency protection
3. **Reliability**: Retry mechanisms and comprehensive error handling
4. **Scalability**: Redis-based idempotency and efficient webhook processing
5. **Monitoring**: Metrics collection and failure alerting
6. **Testing**: Development tools for testing payment flows
7. **Flexibility**: Support for multiple subscription plans and regional pricing

## Scheduled Tasks

### Premium Status Updates

- **Schedule**: Every day at 3:00 AM
- **Function**: `ChatLimitScheduledService.checkExpiringPremiumSubscriptions()`
- **Purpose**: Updates user premium status for expired subscriptions

### Monthly Account Re-enabling

- **Schedule**: 1st of every month at 2:00 AM  
- **Function**: `ChatLimitScheduledService.reEnableAutoDisabledAccounts()`
- **Purpose**: Re-enables accounts that were disabled due to chat limits (regular users only)

## Usage Examples

### Administrative API Usage

#### Creating a Premium Subscription via API

```bash
curl -X POST http://localhost:8002/xiaozhi/admin/premium \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin-token>" \
  -d '{
    "userId": 1,
    "subscriptionType": "MONTHLY",
    "paymentAmount": 9.99,
    "paymentCurrency": "USD",
    "paymentMethod": "STRIPE",
    "paymentTransactionId": "pi_1234567890",
    "subscriptionStartDate": "2025-09-27T00:00:00",
    "subscriptionEndDate": "2025-10-27T00:00:00",
    "status": "ACTIVE"
  }'
```

#### Testing Premium Status

```bash
# Check if user is premium
curl -X GET "http://localhost:8002/xiaozhi/admin/premium/is-premium/1" \
  -H "Authorization: Bearer <admin-token>"

# Get user's active subscription
curl -X GET "http://localhost:8002/xiaozhi/admin/premium/user/1" \
  -H "Authorization: Bearer <admin-token>"

# Create test subscription (development only)
curl -X POST "http://localhost:8002/xiaozhi/admin/payment/test/create-test-subscription?userId=1&subscriptionType=MONTHLY&amount=9.99" \
  -H "Authorization: Bearer <admin-token>"
```

#### User Management with Premium Status

```bash
# Get user list with premium status
curl -X GET "http://localhost:8002/xiaozhi/admin/users?page=1&limit=10" \
  -H "Authorization: Bearer <admin-token>"

# Response includes isPremium field:
{
  "code": 0,
  "data": {
    "list": [
      {
        "userid": "1",
        "mobile": "1234567890",
        "status": 1,
        "deviceCount": "2", 
        "createDate": "2025-09-27T00:00:00.000Z",
        "isPremium": true
      }
    ],
    "total": 1
  }
}
```

### Frontend Integration Examples

#### Stripe Checkout Integration

```javascript
// Initialize Stripe
const stripe = Stripe('pk_live_...');

// Create checkout session
const createSubscription = async (planId) => {
  try {
    const response = await fetch('/api/create-checkout-session', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userToken}`
      },
      body: JSON.stringify({
        userId: currentUser.id,
        priceId: planId,
        successUrl: `${window.location.origin}/subscription/success`,
        cancelUrl: `${window.location.origin}/subscription/cancel`
      })
    });
    
    const session = await response.json();
    
    if (session.code === 0) {
      // Redirect to Stripe checkout
      return stripe.redirectToCheckout({ sessionId: session.data.sessionId });
    } else {
      throw new Error(session.msg);
    }
  } catch (error) {
    console.error('Subscription creation failed:', error);
  }
};

// Usage in Vue.js component
export default {
  data() {
    return {
      plans: [
        { id: 'price_monthly', name: 'Monthly', price: '$9.99', type: 'MONTHLY' },
        { id: 'price_yearly', name: 'Yearly', price: '$99.99', type: 'YEARLY' }
      ]
    };
  },
  methods: {
    async subscribeToPlan(plan) {
      await createSubscription(plan.id);
    }
  }
};
```

#### Premium Status Display

```vue
<template>
  <div class="user-status">
    <div v-if="user.isPremium" class="premium-badge">
      <i class="icon-crown"></i>
      Premium Member
      <span class="expires">Expires: {{ formatDate(user.premiumExpiresAt) }}</span>
    </div>
    <div v-else class="upgrade-prompt">
      <button @click="showUpgradeModal = true" class="upgrade-btn">
        Upgrade to Premium
      </button>
    </div>
  </div>
</template>

<script>
export default {
  props: ['user'],
  data() {
    return {
      showUpgradeModal: false
    };
  },
  methods: {
    formatDate(date) {
      return new Date(date).toLocaleDateString();
    }
  }
};
</script>
```

### Webhook Testing Examples

#### Stripe Webhook Simulation

```bash
# Simulate successful checkout completion
curl -X POST "http://localhost:8002/xiaozhi/admin/payment/test/simulate-webhook/stripe" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "checkout.session.completed",
    "data": {
      "object": {
        "id": "cs_test_123",
        "amount_total": 999,
        "currency": "usd",
        "metadata": {
          "user_id": "1",
          "subscription_type": "MONTHLY"
        }
      }
    }
  }'
```

#### PayPal Webhook Testing

```bash
# Simulate PayPal payment completion
curl -X POST "http://localhost:8002/xiaozhi/admin/payment/test/simulate-webhook/paypal" \
  -H "Content-Type: application/json" \
  -d '{
    "event_type": "PAYMENT.CAPTURE.COMPLETED",
    "resource": {
      "id": "PAYPAL_CAPTURE_123",
      "amount": {
        "value": "9.99",
        "currency_code": "USD"
      },
      "custom_id": "user_1_monthly"
    }
  }'
```

### Device Integration Testing

#### Account Status Checking

```bash
# Check account status for a device
curl -X POST "http://localhost:8002/xiaozhi/config/check-account-status" \
  -H "Content-Type: application/json" \
  -d '{"macAddress": "AA:BB:CC:DD:EE:FF"}'

# Response with premium status:
{
  "code": 0,
  "data": {
    "accountDisabled": false,
    "isPremium": true,
    "reason": "Premium account active until 2025-10-27"
  }
}
```

## Benefits

1. **Seamless Integration**: Works with existing chat limit system without disruption to regular users
2. **Performance Optimized**: Direct DAO queries and cached premium status reduce database overhead
3. **Payment Flexibility**: Supports multiple payment providers (Stripe, PayPal) via secure webhooks
4. **Administrative Visibility**: Premium status clearly displayed in user management interface
5. **Automatic Management**: Scheduled tasks handle subscription lifecycle and expiration automatically
6. **Real-time Validation**: Premium status checked during each chat interaction for immediate benefit
7. **Comprehensive Logging**: Detailed logs for debugging, monitoring, and audit trail
8. **User Experience**: Premium users get unlimited access without any service interruption
9. **Scalable Architecture**: Webhook-based payment processing supports high-volume transactions
10. **Security-First Design**: Signature verification, idempotency protection, and comprehensive error handling

## Security Considerations

1. **Webhook Verification**: All payment webhooks verify signatures (Stripe-Signature, PayPal signatures)
2. **Idempotency Protection**: Redis-based deduplication prevents duplicate subscription creation
3. **Transaction Validation**: All payments verified against external transaction IDs
4. **Status Synchronization**: Regular scheduled tasks ensure premium status accuracy
5. **Fail-Safe Design**: System fails open (allows access) if premium check fails to prevent service disruption
6. **Secure Token Handling**: Payment provider tokens and secrets stored securely
7. **Audit Trail**: All subscription changes logged with timestamps and user information
8. **Access Control**: Premium management APIs require administrative authentication

## Future Enhancements

### Phase 1 (Immediate)
1. **Frontend Subscription Portal**: User-facing subscription management interface
2. **Email Notifications**: Automated renewal reminders and expiry notifications
3. **Mobile Payment Support**: Apple Pay, Google Pay integration
4. **Regional Pricing**: Currency-specific pricing for different markets

### Phase 2 (Medium Term)
1. **Tiered Subscriptions**: Multiple premium levels with different benefits
2. **Family Plans**: Shared premium subscriptions across multiple devices/users
3. **Usage Analytics**: Premium user engagement and chat pattern analysis
4. **Promotional Codes**: Discount and promotional pricing support

### Phase 3 (Long Term)
1. **Enterprise Solutions**: Corporate subscription management
2. **API Rate Limiting**: Premium users get higher API rate limits
3. **Priority Support**: Dedicated support channels for premium users
4. **Advanced Features**: Premium-only AI features and capabilities
5. **White-label Solutions**: Customizable premium branding options

## Monitoring & Maintenance

### Key Metrics to Track
- **Subscription Conversion Rate**: Free to premium conversion percentage
- **Churn Rate**: Premium subscription cancellation rate  
- **Payment Success Rate**: Webhook processing success percentage
- **Premium Usage Patterns**: Chat frequency and engagement of premium users
- **Revenue Metrics**: Monthly/annual recurring revenue tracking

### Recommended Alerts
- **Payment Webhook Failures**: Immediate notification for failed payment processing
- **Subscription Expiry**: Daily report of expiring subscriptions
- **High Churn Events**: Alert when cancellation rate exceeds threshold
- **Technical Issues**: Database connectivity or service availability problems

### Regular Maintenance Tasks
- **Monthly Revenue Reports**: Automated financial reporting
- **Subscription Audit**: Verify all active subscriptions have valid payment records  
- **Performance Monitoring**: Track premium status query performance
- **Security Audits**: Review webhook signature verification logs

## Database Migration

To apply the premium account feature, run the Liquibase changelog:

```bash
# The system uses Liquibase for database migrations
# Changes are automatically applied on application startup through:
# main/manager-api/src/main/resources/db/changelog/

# Manual application (if needed):
cat /path/to/changelog/202509270001.sql | mysql -u root -p xiaozhi_esp32_server
```

## Testing & Validation

### Comprehensive Test Coverage

The implementation includes multiple testing layers:

1. **Unit Tests**: Individual service method testing
2. **Integration Tests**: End-to-end payment flow validation
3. **API Tests**: REST endpoint functionality verification
4. **Webhook Tests**: Payment provider webhook simulation
5. **UI Tests**: Premium status display verification

### Test Scenarios

#### Premium Status Validation
```bash
# Test premium user chat limit bypass
# 1. Create test premium subscription
# 2. Trigger chat interactions beyond normal limits  
# 3. Verify chat continues without restriction
# 4. Confirm logs show premium status recognition
```

#### Payment Flow Testing
```bash
# Test Stripe integration
# 1. Create checkout session with test API keys
# 2. Complete test payment in Stripe dashboard
# 3. Verify webhook creates subscription record
# 4. Confirm user premium status activates immediately
```

#### User Interface Testing
```bash
# Test premium status display
# 1. Navigate to User Management page
# 2. Verify premium users show "Premium" tag
# 3. Verify regular users show "Regular" tag
# 4. Test premium status updates in real-time
```

### Performance Testing

#### Premium Status Query Performance
```bash
# Monitor premium status check performance:
# - Direct DAO query time: < 10ms
# - User list with premium status: < 100ms for 50 users
# - Chat limit bypass decision: < 5ms
```

## Troubleshooting Guide

### Common Issues & Solutions

#### 1. Circular Dependency Error
```
Error: The dependencies of some of the beans form a cycle:
sysUserServiceImpl ↔ userPremiumSubscriptionServiceImpl
```
**Solution**: Use direct DAO queries instead of service dependencies in user list building

#### 2. Webhook Signature Verification Failed
```
Error: Invalid Stripe signature
```
**Solution**: 
- Verify webhook endpoint URL in Stripe dashboard
- Check webhook secret configuration
- Ensure raw request body is used for signature verification

#### 3. Premium Status Not Updating
```
Issue: User shows as regular despite active subscription
```
**Solution**:
- Check subscription end date hasn't passed
- Verify subscription status is 'ACTIVE' 
- Run manual premium status check API
- Check scheduled task execution logs

#### 4. Payment Webhook Not Processing
```
Issue: Payment completed but subscription not created
```
**Solution**:
- Check webhook endpoint is accessible from internet
- Verify webhook event types are configured correctly
- Review webhook processing logs for errors
- Test webhook endpoint with ngrok for local development

### Debug Commands

```bash
# Check premium status for user
curl -X GET "http://localhost:8002/xiaozhi/admin/premium/is-premium/{userId}"

# Manually trigger premium status update  
curl -X POST "http://localhost:8002/xiaozhi/admin/premium/check-status/{userId}"

# View user's subscription details
curl -X GET "http://localhost:8002/xiaozhi/admin/premium/user/{userId}"

# Test webhook processing
curl -X POST "http://localhost:8002/xiaozhi/admin/payment/test/simulate-webhook/stripe" \
  -d '{"type":"checkout.session.completed", ...}'
```