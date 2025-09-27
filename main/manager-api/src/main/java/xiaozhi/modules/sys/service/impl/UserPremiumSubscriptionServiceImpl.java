package xiaozhi.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.sys.dao.UserPremiumSubscriptionDao;
import xiaozhi.modules.sys.dto.UserPremiumSubscriptionDTO;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.entity.UserPremiumSubscriptionEntity;
import xiaozhi.modules.sys.service.SysUserService;
import xiaozhi.modules.sys.service.UserPremiumSubscriptionService;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户高级订阅服务实现
 */
@AllArgsConstructor
@Service
public class UserPremiumSubscriptionServiceImpl extends BaseServiceImpl<UserPremiumSubscriptionDao, UserPremiumSubscriptionEntity>
        implements UserPremiumSubscriptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserPremiumSubscriptionServiceImpl.class);
    
    private final SysUserService sysUserService;
    
    @Override
    public PageData<UserPremiumSubscriptionDTO> page(Map<String, Object> params) {
        IPage<UserPremiumSubscriptionEntity> page = baseDao.selectPage(
            getPage(params, "created_date", false),
            getWrapper(params)
        );
        return getPageData(page, UserPremiumSubscriptionDTO.class);
    }
    
    @Override
    public UserPremiumSubscriptionDTO getActiveSubscriptionByUserId(Long userId) {
        UserPremiumSubscriptionEntity entity = baseDao.getActiveSubscriptionByUserId(userId);
        return ConvertUtils.sourceToTarget(entity, UserPremiumSubscriptionDTO.class);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSubscription(UserPremiumSubscriptionDTO dto) {
        UserPremiumSubscriptionEntity entity = ConvertUtils.sourceToTarget(dto, UserPremiumSubscriptionEntity.class);
        
        // 设置创建时间
        if (entity.getCreatedDate() == null) {
            entity.setCreatedDate(new Date());
        }
        if (entity.getUpdatedDate() == null) {
            entity.setUpdatedDate(new Date());
        }
        
        // 保存订阅记录
        insert(entity);
        
        // 更新用户高级状态
        checkAndUpdateUserPremiumStatus(dto.getUserId());
        
        logger.info("Created premium subscription for user {}: {} - {} to {}", 
                dto.getUserId(), dto.getSubscriptionType(), 
                dto.getSubscriptionStartDate(), dto.getSubscriptionEndDate());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSubscriptionStatus(Long subscriptionId, String status) {
        UserPremiumSubscriptionEntity entity = selectById(subscriptionId);
        if (entity != null) {
            entity.setStatus(status);
            entity.setUpdatedDate(new Date());
            updateById(entity);
            
            // 更新用户高级状态
            checkAndUpdateUserPremiumStatus(entity.getUserId());
            
            logger.info("Updated subscription {} status to {}", subscriptionId, status);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndUpdateUserPremiumStatus(Long userId) {
        try {
            // 获取用户的活跃订阅
            UserPremiumSubscriptionEntity activeSubscription = baseDao.getActiveSubscriptionByUserId(userId);
            
            // 获取用户信息
            SysUserEntity user = sysUserService.selectById(userId);
            if (user == null) {
                logger.warn("User {} not found for premium status check", userId);
                return false;
            }
            
            boolean isPremium = activeSubscription != null;
            Date premiumExpiresAt = isPremium ? activeSubscription.getSubscriptionEndDate() : null;
            
            // 更新用户高级状态
            user.setIsPremium(isPremium ? 1 : 0);
            user.setPremiumExpiresAt(premiumExpiresAt);
            user.setPremiumLastCheck(new Date());
            
            sysUserService.updateById(user);
            
            logger.debug("Updated premium status for user {}: isPremium={}, expiresAt={}", 
                    userId, isPremium, premiumExpiresAt);
            
            return isPremium;
            
        } catch (Exception e) {
            logger.error("Error checking premium status for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean isUserPremium(Long userId) {
        try {
            SysUserEntity user = sysUserService.selectById(userId);
            if (user == null) {
                return false;
            }
            
            // 检查高级状态和过期时间
            if (user.getIsPremium() != null && user.getIsPremium() == 1) {
                if (user.getPremiumExpiresAt() == null || user.getPremiumExpiresAt().after(new Date())) {
                    return true;
                }
            }
            
            // 如果状态可能过期，重新检查
            return checkAndUpdateUserPremiumStatus(userId);
            
        } catch (Exception e) {
            logger.error("Error checking if user {} is premium: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkExpiringSubscriptions() {
        try {
            // 获取7天内到期的订阅
            List<UserPremiumSubscriptionEntity> expiringSubscriptions = baseDao.getExpiringSubscriptions(7);
            
            logger.info("Found {} subscriptions expiring in the next 7 days", expiringSubscriptions.size());
            
            for (UserPremiumSubscriptionEntity subscription : expiringSubscriptions) {
                // 更新用户高级状态
                checkAndUpdateUserPremiumStatus(subscription.getUserId());
                
                // 这里可以添加发送邮件或推送通知的逻辑
                logger.info("Subscription {} for user {} expires on {}", 
                        subscription.getId(), subscription.getUserId(), subscription.getSubscriptionEndDate());
            }
            
        } catch (Exception e) {
            logger.error("Error checking expiring subscriptions: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public boolean validateSubscriptionByTransactionId(String transactionId) {
        try {
            UserPremiumSubscriptionEntity subscription = baseDao.getSubscriptionByTransactionId(transactionId);
            return subscription != null && "ACTIVE".equals(subscription.getStatus());
        } catch (Exception e) {
            logger.error("Error validating subscription by transaction ID {}: {}", transactionId, e.getMessage(), e);
            return false;
        }
    }
    
    private QueryWrapper<UserPremiumSubscriptionEntity> getWrapper(Map<String, Object> params) {
        QueryWrapper<UserPremiumSubscriptionEntity> wrapper = new QueryWrapper<>();
        
        String userId = (String) params.get("userId");
        if (userId != null && !userId.isEmpty()) {
            wrapper.eq("user_id", userId);
        }
        
        String status = (String) params.get("status");
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        
        String subscriptionType = (String) params.get("subscriptionType");
        if (subscriptionType != null && !subscriptionType.isEmpty()) {
            wrapper.eq("subscription_type", subscriptionType);
        }
        
        return wrapper;
    }
}