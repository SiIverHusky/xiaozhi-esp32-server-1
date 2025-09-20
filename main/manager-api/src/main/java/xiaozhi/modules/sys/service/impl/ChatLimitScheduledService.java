package xiaozhi.modules.sys.service.impl;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.sys.dao.SysUserDao;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.service.SysUserService;

/**
 * 聊天限制相关的定时任务服务
 */
@Service
@AllArgsConstructor
@Slf4j
public class ChatLimitScheduledService {

    private final SysUserService sysUserService;
    private final SysUserDao sysUserDao;

    /**
     * 每月第一天凌晨2点重新启用因聊天次数限制被禁用的账户
     * 这样确保不会在午夜重启时影响用户，同时给系统一些缓冲时间
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    @Transactional
    public void reEnableAutoDisabledAccounts() {
        log.info("=== CHAT LIMIT RESET === Starting monthly re-enable of auto-disabled accounts");
        
        try {
            // 查询所有因聊天限制被自动禁用的用户
            QueryWrapper<SysUserEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", 0) // 被禁用的账户
                       .eq("auto_disabled_reason", "MONTHLY_CHAT_LIMIT_EXCEEDED");
            
            List<SysUserEntity> autoDisabledUsers = sysUserDao.selectList(queryWrapper);
            
            if (autoDisabledUsers == null || autoDisabledUsers.isEmpty()) {
                log.info("=== CHAT LIMIT RESET === No auto-disabled accounts found to re-enable");
                return;
            }
            
            log.info("=== CHAT LIMIT RESET === Found {} auto-disabled accounts to re-enable", autoDisabledUsers.size());
            
            // 重新启用这些账户
            UpdateWrapper<SysUserEntity> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("status", 0)
                        .eq("auto_disabled_reason", "MONTHLY_CHAT_LIMIT_EXCEEDED")
                        .set("status", 1) // 重新启用
                        .set("auto_disabled_reason", null); // 清除自动禁用原因
            
            int updatedCount = sysUserDao.update(null, updateWrapper);
            
            if (updatedCount > 0) {
                log.info("=== CHAT LIMIT RESET === Successfully re-enabled {} accounts", updatedCount);
                
                // 记录详细信息
                for (SysUserEntity user : autoDisabledUsers) {
                    log.info("=== CHAT LIMIT RESET === Re-enabled user: {} (ID: {})", 
                            user.getUsername(), user.getId());
                }
            } else {
                log.error("=== CHAT LIMIT RESET === Failed to re-enable accounts");
            }
            
        } catch (Exception e) {
            log.error("=== CHAT LIMIT RESET === Error during monthly account re-enabling", e);
        }
    }
}
