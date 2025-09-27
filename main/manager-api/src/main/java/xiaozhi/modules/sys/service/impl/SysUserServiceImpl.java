package xiaozhi.modules.sys.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.security.password.PasswordUtils;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.sys.dto.AdminPageUserDTO;
import xiaozhi.modules.sys.dto.PasswordDTO;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.enums.SuperAdminEnum;
import xiaozhi.modules.sys.event.MaxChatCountUpdatedEvent;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.sys.service.SysUserService;
import xiaozhi.modules.sys.vo.AdminPageUserVO;
import xiaozhi.modules.sys.vo.ChatCountVO;
import xiaozhi.modules.sys.vo.UserChatStatsVO;
import xiaozhi.modules.sys.dao.UserPremiumSubscriptionDao;

/**
 * 系统用户
 */
@AllArgsConstructor
@Service
public class SysUserServiceImpl extends BaseServiceImpl<SysUserDao, SysUserEntity> implements SysUserService {
    private static final Logger logger = LoggerFactory.getLogger(SysUserServiceImpl.class);
    
    private final SysUserDao sysUserDao;
    private final UserPremiumSubscriptionDao premiumSubscriptionDao;

    private final DeviceService deviceService;

    private final AgentService agentService;

    private final SysParamsService sysParamsService;

    @Override
    public SysUserDTO getByUsername(String username) {
        QueryWrapper<SysUserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        List<SysUserEntity> users = sysUserDao.selectList(queryWrapper);
        if (users == null || users.isEmpty()) {
            return null;
        }
        SysUserEntity entity = users.getFirst();
        return ConvertUtils.sourceToTarget(entity, SysUserDTO.class);
    }

    @Override
    public SysUserDTO getByUserId(Long userId) {
        SysUserEntity sysUserEntity = sysUserDao.selectById(userId);

        return ConvertUtils.sourceToTarget(sysUserEntity, SysUserDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SysUserDTO dto) {
        SysUserEntity entity = ConvertUtils.sourceToTarget(dto, SysUserEntity.class);

        // 密码强度
        if (!isStrongPassword(entity.getPassword())) {
            throw new RenException(ErrorCode.PASSWORD_WEAK_ERROR);
        }

        // 密码加密
        String password = PasswordUtils.encode(entity.getPassword());
        entity.setPassword(password);

        // 保存用户
        Long userCount = getUserCount();
        if (userCount == 0) {
            entity.setSuperAdmin(SuperAdminEnum.YES.value());
        } else {
            entity.setSuperAdmin(SuperAdminEnum.NO.value());
        }
        entity.setStatus(1);

        insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        // 删除用户
        baseDao.deleteById(id);
        // 删除设备
        deviceService.deleteByUserId(id);
        // 删除智能体
        agentService.deleteAgentByUserId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordDTO passwordDTO) {
        SysUserEntity sysUserEntity = sysUserDao.selectById(userId);

        if (null == sysUserEntity) {
            throw new RenException(ErrorCode.TOKEN_INVALID);
        }

        // 判断旧密码是否正确
        if (!PasswordUtils.matches(passwordDTO.getPassword(), sysUserEntity.getPassword())) {
            throw new RenException("current password does not match");
        }

        // 新密码强度
        if (!isStrongPassword(passwordDTO.getNewPassword())) {
            throw new RenException(ErrorCode.PASSWORD_WEAK_ERROR);
        }

        // 密码加密
        String password = PasswordUtils.encode(passwordDTO.getNewPassword());
        sysUserEntity.setPassword(password);

        updateById(sysUserEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePasswordDirectly(Long userId, String password) {
        // 新密码强度
        if (!isStrongPassword(password)) {
            throw new RenException(ErrorCode.PASSWORD_WEAK_ERROR);
        }
        SysUserEntity sysUserEntity = new SysUserEntity();
        sysUserEntity.setId(userId);
        sysUserEntity.setPassword(PasswordUtils.encode(password));
        updateById(sysUserEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long userId) {
        String password = generatePassword();
        changePasswordDirectly(userId, password);
        return password;
    }

    private Long getUserCount() {
        QueryWrapper<SysUserEntity> queryWrapper = new QueryWrapper<>();
        return baseDao.selectCount(queryWrapper);
    }

    @Override
    public PageData<AdminPageUserVO> page(AdminPageUserDTO dto) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put(Constant.PAGE, dto.getPage());
        params.put(Constant.LIMIT, dto.getLimit());
        IPage<SysUserEntity> page = baseDao.selectPage(
                getPage(params, "id", true),
                new QueryWrapper<SysUserEntity>().like(StringUtils.isNotBlank(dto.getMobile()), "username",
                        dto.getMobile()));
        // 循环处理page获取回来的数据，返回需要的字段
        List<AdminPageUserVO> list = page.getRecords().stream().map(user -> {
            AdminPageUserVO adminPageUserVO = new AdminPageUserVO();
            adminPageUserVO.setUserid(user.getId().toString());
            adminPageUserVO.setMobile(user.getUsername());
            String deviceCount = deviceService.selectCountByUserId(user.getId()).toString();
            adminPageUserVO.setDeviceCount(deviceCount);
            adminPageUserVO.setStatus(user.getStatus());
            adminPageUserVO.setCreateDate(user.getCreateDate());
            // Check premium status using DAO directly to avoid circular dependency
            boolean isPremium = premiumSubscriptionDao.getActiveSubscriptionByUserId(user.getId()) != null;
            adminPageUserVO.setIsPremium(isPremium);
            return adminPageUserVO;
        }).toList();
        return new PageData<>(list, page.getTotal());
    }

    private boolean isStrongPassword(String password) {
        // 弱密码的正则表达式
        String weakPasswordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).+$";
        Pattern pattern = Pattern.compile(weakPasswordRegex);
        Matcher matcher = pattern.matcher(password);
        return matcher.matches();
    }

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
    private static final Random random = new Random();

    /**
     * 生成随机密码
     * 
     * @return 随机生成的密码
     */
    private String generatePassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return password.toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Integer status, String[] userIds) {
        for (String userId : userIds) {
            SysUserEntity entity = new SysUserEntity();
            entity.setId(Long.parseLong(userId));
            entity.setStatus(status);
            updateById(entity);
        }
    }

    @Override
    public boolean getAllowUserRegister() {
        String allowUserRegister = sysParamsService.getValue(Constant.SERVER_ALLOW_USER_REGISTER, true);
        if (allowUserRegister.equals("true")) {
            return true;
        }
        Long userCount = baseDao.selectCount(new QueryWrapper<SysUserEntity>());
        if (userCount == 0) {
            return true;
        }
        return false;
    }

    @Override
    public List<ChatCountVO> getChatCount(String date, Integer minCount) {
        logger.info("Getting chat count for date: {} with minCount: {}", date, minCount);
        try {
            List<ChatCountVO> result = sysUserDao.getChatCount(date, minCount);
            logger.info("Chat count result: {} records found", result != null ? result.size() : 0);
            if (result != null && !result.isEmpty()) {
                for (ChatCountVO vo : result) {
                    logger.debug("Chat count result: userId={}, username={}, count={}", 
                            vo.getUserId(), vo.getUsername(), vo.getChatCount());
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Error getting chat count for date: {} with minCount: {}", date, minCount, e);
            throw e;
        }
    }

    @Override
    public List<UserChatStatsVO> getUserChatStats() {
        logger.info("Getting user chat statistics for all users");
        try {
            List<UserChatStatsVO> result = sysUserDao.getUserChatStats();
            logger.info("User chat stats result: {} records found", result != null ? result.size() : 0);
            return result;
        } catch (Exception e) {
            logger.error("Error getting user chat statistics", e);
            throw e;
        }
    }

    @Override
    public void checkAndReEnableAccountsForNewLimit(Integer newMaxChatCount) {
        logger.info("=== CHAT LIMIT RE-ENABLE === Checking accounts for re-enablement with new limit: {}", newMaxChatCount);
        
        try {
            // 找到所有因聊天限制而被自动禁用的账户
            QueryWrapper<SysUserEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", 0) // 已禁用
                    .eq("auto_disabled_reason", "MONTHLY_CHAT_LIMIT_EXCEEDED"); // 因聊天限制禁用
            
            List<SysUserEntity> disabledUsers = baseDao.selectList(queryWrapper);
            logger.info("=== CHAT LIMIT RE-ENABLE === Found {} users disabled due to chat limit", disabledUsers.size());
            
            if (disabledUsers.isEmpty()) {
                return;
            }
            
            // 获取所有用户的当前聊天统计
            List<UserChatStatsVO> allUserStats = getUserChatStats();
            Map<Long, Integer> userCurrentChatCounts = new HashMap<>();
            
            for (UserChatStatsVO stat : allUserStats) {
                userCurrentChatCounts.put(stat.getUserId(), stat.getCurrentMonthCount());
            }
            
            // 检查每个被禁用的用户
            int reEnabledCount = 0;
            for (SysUserEntity user : disabledUsers) {
                Integer currentChatCount = userCurrentChatCounts.getOrDefault(user.getId(), 0);
                
                if (currentChatCount <= newMaxChatCount) {
                    // 当前聊天次数在新限制内，重新启用账户
                    user.setStatus(1); // 1 = enabled
                    user.setAutoDisabledReason(null); // 清除自动禁用原因
                    baseDao.updateById(user);
                    
                    logger.info("=== CHAT LIMIT RE-ENABLE === Re-enabled user {} (current chats: {} <= new limit: {})", 
                            user.getId(), currentChatCount, newMaxChatCount);
                    reEnabledCount++;
                } else {
                    logger.debug("=== CHAT LIMIT RE-ENABLE === User {} still exceeds new limit (current chats: {} > new limit: {})", 
                            user.getId(), currentChatCount, newMaxChatCount);
                }
            }
            
            logger.info("=== CHAT LIMIT RE-ENABLE === Re-enabled {} out of {} disabled users", reEnabledCount, disabledUsers.size());
            
        } catch (Exception e) {
            logger.error("=== CHAT LIMIT RE-ENABLE === Error checking and re-enabling accounts for new limit", e);
            throw e;
        }
    }

    /**
     * Check and disable accounts that exceed the new lower chat limit
     */
    @Override
    @Transactional
    public void checkAndDisableAccountsForLowerLimit(Integer newMaxChatCount) {
        logger.info("=== CHAT LIMIT DISABLE === Checking accounts for disabling with lower limit: {}", newMaxChatCount);
        
        try {
            // 找到所有当前启用的账户
            QueryWrapper<SysUserEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", 1); // 1 = enabled
            
            List<SysUserEntity> enabledUsers = baseDao.selectList(queryWrapper);
            logger.info("=== CHAT LIMIT DISABLE === Found {} enabled users to check", enabledUsers.size());
            
            if (enabledUsers.isEmpty()) {
                return;
            }
            
            // 获取所有用户的当前聊天统计
            List<UserChatStatsVO> allUserStats = getUserChatStats();
            Map<Long, Integer> userCurrentChatCounts = new HashMap<>();
            
            for (UserChatStatsVO stat : allUserStats) {
                userCurrentChatCounts.put(stat.getUserId(), stat.getCurrentMonthCount());
            }
            
            // 检查每个启用的用户
            int disabledCount = 0;
            for (SysUserEntity user : enabledUsers) {
                Integer currentChatCount = userCurrentChatCounts.getOrDefault(user.getId(), 0);
                
                if (currentChatCount > newMaxChatCount) {
                    // 当前聊天次数超过新限制，禁用账户
                    user.setStatus(0); // 0 = disabled
                    user.setAutoDisabledReason("MONTHLY_CHAT_LIMIT_EXCEEDED"); // 设置自动禁用原因
                    baseDao.updateById(user);
                    
                    logger.info("=== CHAT LIMIT DISABLE === Disabled user {} (current chats: {} > new limit: {})", 
                            user.getId(), currentChatCount, newMaxChatCount);
                    disabledCount++;
                } else {
                    logger.debug("=== CHAT LIMIT DISABLE === User {} within new limit (current chats: {} <= new limit: {})", 
                            user.getId(), currentChatCount, newMaxChatCount);
                }
            }
            
            logger.info("=== CHAT LIMIT DISABLE === Disabled {} out of {} enabled users due to lower limit", disabledCount, enabledUsers.size());
            
        } catch (Exception e) {
            logger.error("=== CHAT LIMIT DISABLE === Error checking and disabling accounts for lower limit", e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void syncChatCountToDatabase() {
        logger.info("=== CHAT COUNT SYNC === Starting synchronization of chat counts to database");
        
        try {
            // 获取所有用户的准确聊天统计数据（Manager Portal使用的数据源）
            List<UserChatStatsVO> userChatStats = getUserChatStats();
            logger.info("=== CHAT COUNT SYNC === Retrieved {} user chat statistics from reliable source", userChatStats.size());
            
            int updatedCount = 0;
            String currentMonth = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            
            for (UserChatStatsVO stat : userChatStats) {
                Long userId = stat.getUserId();
                Integer actualCurrentMonthCount = stat.getCurrentMonthCount();
                
                // 更新sys_user表中的chat_count_month字段
                SysUserEntity user = baseDao.selectById(userId);
                if (user != null) {
                    Integer oldChatCount = user.getChatCountMonth();
                    
                    // 更新聊天次数和重置月份
                    user.setChatCountMonth(actualCurrentMonthCount);
                    user.setLastResetMonth(currentMonth);
                    
                    baseDao.updateById(user);
                    updatedCount++;
                    
                    logger.debug("=== CHAT COUNT SYNC === Updated user {}: {} -> {} chats", 
                            userId, oldChatCount, actualCurrentMonthCount);
                } else {
                    logger.warn("=== CHAT COUNT SYNC === User {} not found in sys_user table", userId);
                }
            }
            
            logger.info("=== CHAT COUNT SYNC === Successfully synchronized {} user chat counts to database", updatedCount);
            
        } catch (Exception e) {
            logger.error("=== CHAT COUNT SYNC === Error synchronizing chat counts to database", e);
            throw e;
        }
    }
    
    /**
     * Event listener for MAX_CHAT_COUNT parameter updates
     */
    @EventListener
    @Transactional
    public void handleMaxChatCountUpdated(MaxChatCountUpdatedEvent event) {
        logger.info("=== CHAT LIMIT EVENT === Received MAX_CHAT_COUNT update event: {} -> {}", 
                event.getOldValue(), event.getNewValue());
        
        if (event.getNewValue() != null && event.getOldValue() != null) {
            if (event.getNewValue() > event.getOldValue()) {
                logger.info("=== CHAT LIMIT EVENT === Chat limit increased, checking for accounts to re-enable");
                checkAndReEnableAccountsForNewLimit(event.getNewValue());
            } else if (event.getNewValue() < event.getOldValue()) {
                logger.info("=== CHAT LIMIT EVENT === Chat limit decreased, checking for accounts to disable");
                checkAndDisableAccountsForLowerLimit(event.getNewValue());
            } else {
                logger.debug("=== CHAT LIMIT EVENT === Chat limit unchanged, no action needed");
            }
        } else {
            logger.debug("=== CHAT LIMIT EVENT === Missing old or new value, no action taken");
        }
    }
}
