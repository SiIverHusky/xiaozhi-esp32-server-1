package xiaozhi.modules.sys.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.sys.entity.UserPremiumSubscriptionEntity;

import java.util.List;

/**
 * 用户高级订阅DAO
 */
@Mapper
public interface UserPremiumSubscriptionDao extends BaseDao<UserPremiumSubscriptionEntity> {
    
    /**
     * 根据用户ID获取活跃的订阅记录
     */
    @Select("SELECT * FROM user_premium_subscription WHERE user_id = #{userId} AND status = 'ACTIVE' " +
            "AND subscription_start_date <= NOW() AND subscription_end_date >= NOW() " +
            "ORDER BY subscription_end_date DESC LIMIT 1")
    UserPremiumSubscriptionEntity getActiveSubscriptionByUserId(@Param("userId") Long userId);
    
    /**
     * 根据用户ID获取所有订阅记录（按时间倒序）
     */
    @Select("SELECT * FROM user_premium_subscription WHERE user_id = #{userId} " +
            "ORDER BY created_date DESC")
    List<UserPremiumSubscriptionEntity> getSubscriptionsByUserId(@Param("userId") Long userId);
    
    /**
     * 根据支付交易ID查找订阅记录
     */
    @Select("SELECT * FROM user_premium_subscription WHERE payment_transaction_id = #{transactionId}")
    UserPremiumSubscriptionEntity getSubscriptionByTransactionId(@Param("transactionId") String transactionId);
    
    /**
     * 获取即将到期的订阅（用于提醒）
     */
    @Select("SELECT * FROM user_premium_subscription WHERE status = 'ACTIVE' " +
            "AND subscription_end_date BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL #{days} DAY)")
    List<UserPremiumSubscriptionEntity> getExpiringSubscriptions(@Param("days") int days);
}