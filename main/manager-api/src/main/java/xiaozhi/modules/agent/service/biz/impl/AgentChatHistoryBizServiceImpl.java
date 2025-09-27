package xiaozhi.modules.agent.service.biz.impl;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.agent.dto.AgentChatHistoryReportDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentChatAudioService;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.biz.AgentChatHistoryBizService;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.sys.service.SysUserService;
import xiaozhi.modules.sys.service.UserPremiumSubscriptionService;

/**
 * {@link AgentChatHistoryBizService} impl
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentChatHistoryBizServiceImpl implements AgentChatHistoryBizService {
    private final AgentService agentService;
    private final AgentChatHistoryService agentChatHistoryService;
    private final AgentChatAudioService agentChatAudioService;
    private final RedisUtils redisUtils;
    private final DeviceService deviceService;
    private final SysUserService sysUserService;
    private final SysParamsService sysParamsService;
    private final UserPremiumSubscriptionService premiumSubscriptionService;

    /**
     * 处理聊天记录上报，包括文件上传和相关信息记录
     *
     * @param report 包含聊天上报所需信息的输入对象
     * @return 上传结果，true表示成功，false表示失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean report(AgentChatHistoryReportDTO report) {
        String macAddress = report.getMacAddress();
        Byte chatType = report.getChatType();
        Long reportTimeMillis = null != report.getReportTime() ? report.getReportTime() * 1000 : System.currentTimeMillis();
        log.info("Xiaozhi Decice Chat Report Request: macAddress={}, type={} reportTime={}", macAddress, chatType, reportTimeMillis);

        // 根据设备MAC地址查询对应的默认智能体，判断是否需要上报
        AgentEntity agentEntity = agentService.getDefaultAgentByMacAddress(macAddress);
        if (agentEntity == null) {
            return Boolean.FALSE;
        }

        Integer chatHistoryConf = agentEntity.getChatHistoryConf();
        String agentId = agentEntity.getId();

        if (Objects.equals(chatHistoryConf, Constant.ChatHistoryConfEnum.RECORD_TEXT.getCode())) {
            saveChatText(report, agentId, macAddress, null, reportTimeMillis);
        } else if (Objects.equals(chatHistoryConf, Constant.ChatHistoryConfEnum.RECORD_TEXT_AUDIO.getCode())) {
            String audioId = saveChatAudio(report);
            saveChatText(report, agentId, macAddress, audioId, reportTimeMillis);
        }

        // Check chat limit for user chats (chatType=1) after saving the record
        if (Objects.equals(chatType, (byte) 1)) {
            checkAndEnforceChatLimit(macAddress);
        }

        // 更新设备最后对话时间
        redisUtils.set(RedisKeys.getAgentDeviceLastConnectedAtById(agentId), new Date());
        return Boolean.TRUE;
    }

    /**
     * base64解码report.getOpusDataBase64(),存入ai_agent_chat_audio表
     */
    private String saveChatAudio(AgentChatHistoryReportDTO report) {
        String audioId = null;

        if (report.getAudioBase64() != null && !report.getAudioBase64().isEmpty()) {
            try {
                byte[] audioData = Base64.getDecoder().decode(report.getAudioBase64());
                audioId = agentChatAudioService.saveAudio(audioData);
                log.info("Audio Data Saved Successfully, audioId={}", audioId);
            } catch (Exception e) {
                log.error("Failed to save Audio Data ", e);
                return null;
            }
        }
        return audioId;
    }

    /**
     * 组装上报数据
     */
    private void saveChatText(AgentChatHistoryReportDTO report, String agentId, String macAddress, String audioId, Long reportTime) {
        // 构建聊天记录实体
        AgentChatHistoryEntity entity = AgentChatHistoryEntity.builder()
                .macAddress(macAddress)
                .agentId(agentId)
                .sessionId(report.getSessionId())
                .chatType(report.getChatType())
                .content(report.getContent())
                .audioId(audioId)
                .createdAt(new Date(reportTime))
                // NOTE(haotian): 2025/5/26 updateAt可以不设置，重点是createAt，而且这样可以看到上报延迟
                .build();

        // 保存数据
        agentChatHistoryService.save(entity);

        // 同步用户的当月聊天次数计数到数据库
        syncUserChatCount(macAddress);

        log.info("Device {} mapping agent {} report successfully", macAddress, agentId);
    }

    /**
     * 检查并强制执行聊天限制
     * 如果用户当月聊天次数超过限制且不是超级管理员，则禁用账户
     */
    private void checkAndEnforceChatLimit(String macAddress) {
        try {
            log.info("=== CHAT LIMIT CHECK === Checking chat limit for device: {}", macAddress);
            
            // 获取设备关联的用户ID
            xiaozhi.modules.device.entity.DeviceEntity device = deviceService.getDeviceByMacAddress(macAddress);
            if (device == null || device.getUserId() == null) {
                log.debug("=== CHAT LIMIT CHECK === No user found for device: {}", macAddress);
                return;
            }
            
            Long userId = device.getUserId();
            
            log.info("=== CHAT LIMIT CHECK === Device {} belongs to user: {}", macAddress, userId);
            
            // 获取用户信息
            SysUserEntity user = sysUserService.selectById(userId);
            if (user == null) {
                log.warn("=== CHAT LIMIT CHECK === User not found: {}", userId);
                return;
            }
            
            // 检查是否是超级管理员，如果是则跳过限制
            if (Objects.equals(user.getSuperAdmin(), 1)) {
                log.info("=== CHAT LIMIT CHECK === User {} is superadmin, skipping chat limit", userId);
                return;
            }
            
            // 检查是否是高级用户，如果是则跳过聊天限制
            if (premiumSubscriptionService.isUserPremium(userId)) {
                log.info("=== CHAT LIMIT CHECK === User {} is premium, skipping chat limit", userId);
                return;
            }
            
            // 检查是否是高级用户，如果是则跳过聊天限制
            if (premiumSubscriptionService.isUserPremium(userId)) {
                log.info("=== CHAT LIMIT CHECK === User {} is premium, skipping chat limit", userId);
                return;
            }
            
            // 检查账户是否已经被禁用
            if (Objects.equals(user.getStatus(), 0)) {
                log.debug("=== CHAT LIMIT CHECK === User {} is already disabled", userId);
                return;
            }
            
            // 获取最大聊天次数限制
            String maxChatCountStr = sysParamsService.getValue(Constant.MAX_CHAT_COUNT, true);
            if (maxChatCountStr == null || maxChatCountStr.isEmpty()) {
                log.debug("=== CHAT LIMIT CHECK === No max_chat_count configured, skipping limit check");
                return;
            }
            
            int maxChatCount;
            try {
                maxChatCount = Integer.parseInt(maxChatCountStr);
            } catch (NumberFormatException e) {
                log.error("=== CHAT LIMIT CHECK === Invalid max_chat_count value: {}", maxChatCountStr);
                return;
            }
            
            log.info("=== CHAT LIMIT CHECK === Max chat count limit: {}", maxChatCount);
            
            // 获取用户当月聊天次数（直接从数据库chat_count_month字段获取，性能更好）
            Integer currentMonthCount = user.getChatCountMonth() != null ? user.getChatCountMonth() : 0;
            
            log.info("=== CHAT LIMIT CHECK === User {} current month chat count from database: {}", userId, currentMonthCount);
            
            // 检查是否超过限制
            if (currentMonthCount > maxChatCount) {
                log.warn("=== CHAT LIMIT CHECK === User {} exceeded chat limit ({} > {}), disabling account", 
                        userId, currentMonthCount, maxChatCount);
                
                // 禁用账户并设置自动禁用原因
                user.setStatus(0); // 0 = disabled
                user.setAutoDisabledReason("MONTHLY_CHAT_LIMIT_EXCEEDED");
                sysUserService.updateById(user);
                
                log.warn("=== CHAT LIMIT CHECK === User {} account disabled due to chat limit exceeded", userId);
            } else {
                log.debug("=== CHAT LIMIT CHECK === User {} within chat limit ({} <= {})", 
                        userId, currentMonthCount, maxChatCount);
            }
            
        } catch (Exception e) {
            log.error("=== CHAT LIMIT CHECK === Error checking chat limit for device: {}", macAddress, e);
            // Don't throw exception to avoid affecting normal chat reporting
        }
    }

    /**
     * 同步用户聊天次数计数
     * 每次保存聊天记录时调用，从准确的数据源同步最新聊天次数到sys_user表
     */
    private void syncUserChatCount(String macAddress) {
        try {
            log.debug("=== CHAT COUNT SYNC === Syncing chat count for device: {}", macAddress);
            
            // 获取设备关联的用户ID
            xiaozhi.modules.device.entity.DeviceEntity device = deviceService.getDeviceByMacAddress(macAddress);
            if (device == null || device.getUserId() == null) {
                log.debug("=== CHAT COUNT SYNC === No user found for device: {}", macAddress);
                return;
            }
            
            Long userId = device.getUserId();
            String currentMonth = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            
            // 获取用户信息
            xiaozhi.modules.sys.entity.SysUserEntity user = sysUserService.selectById(userId);
            if (user == null) {
                log.warn("=== CHAT COUNT SYNC === User {} not found", userId);
                return;
            }
            
            // 从可靠数据源获取实际聊天次数（与Manager Portal显示的一致）
            List<xiaozhi.modules.sys.vo.UserChatStatsVO> userStats = sysUserService.getUserChatStats();
            xiaozhi.modules.sys.vo.UserChatStatsVO currentUserStats = userStats.stream()
                    .filter(stat -> stat.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
            
            if (currentUserStats != null) {
                Integer actualCurrentMonthCount = currentUserStats.getCurrentMonthCount();
                Integer oldCount = user.getChatCountMonth();
                
                // 直接存储从CHAT_COUNT_FEATURE获取的准确数值
                user.setChatCountMonth(actualCurrentMonthCount);
                user.setLastResetMonth(currentMonth);
                
                // 更新数据库
                sysUserService.updateById(user);
                
                log.debug("=== CHAT COUNT SYNC === Synced chat count for user {}: {} -> {} (from reliable source)", 
                        userId, oldCount, actualCurrentMonthCount);
            } else {
                log.warn("=== CHAT COUNT SYNC === Could not find chat stats for user {}", userId);
            }
            
        } catch (Exception e) {
            log.error("=== CHAT COUNT SYNC === Error syncing chat count for device: {}", macAddress, e);
            // Don't throw exception to avoid affecting normal chat reporting
        }
    }
}
