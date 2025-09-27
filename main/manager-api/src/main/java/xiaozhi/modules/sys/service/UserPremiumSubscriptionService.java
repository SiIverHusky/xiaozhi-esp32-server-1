package xiaozhi.modules.sys.service;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.sys.dto.UserPremiumSubscriptionDTO;
import xiaozhi.modules.sys.entity.UserPremiumSubscriptionEntity;

import java.util.Map;

/**
 * 用户高级订阅服务接口
 */
public interface UserPremiumSubscriptionService extends BaseService<UserPremiumSubscriptionEntity> {
    
    /**
     * 分页查询订阅记录
     */
    PageData<UserPremiumSubscriptionDTO> page(Map<String, Object> params);
    
    /**
     * 根据用户ID获取活跃订阅
     */
    UserPremiumSubscriptionDTO getActiveSubscriptionByUserId(Long userId);
    
    /**
     * 创建新的订阅记录
     */
    void createSubscription(UserPremiumSubscriptionDTO dto);
    
    /**
     * 更新订阅状态
     */
    void updateSubscriptionStatus(Long subscriptionId, String status);
    
    /**
     * 检查并更新用户高级状态
     */
    boolean checkAndUpdateUserPremiumStatus(Long userId);
    
    /**
     * 检查用户是否为高级用户
     */
    boolean isUserPremium(Long userId);
    
    /**
     * 批量检查即将到期的订阅
     */
    void checkExpiringSubscriptions();
    
    /**
     * 根据支付交易ID验证订阅
     */
    boolean validateSubscriptionByTransactionId(String transactionId);
}