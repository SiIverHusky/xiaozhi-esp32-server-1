package xiaozhi.modules.sys.service;

import java.util.List;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.sys.dto.AdminPageUserDTO;
import xiaozhi.modules.sys.dto.PasswordDTO;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.vo.AdminPageUserVO;
import xiaozhi.modules.sys.vo.ChatCountVO;
import xiaozhi.modules.sys.vo.UserChatStatsVO;

/**
 * 系统用户
 */
public interface SysUserService extends BaseService<SysUserEntity> {

    SysUserDTO getByUsername(String username);

    SysUserDTO getByUserId(Long userId);

    void save(SysUserDTO dto);

    /**
     * 删除指定用户，且有关联的数据设备和智能体
     * 
     * @param ids
     */
    void deleteById(Long ids);

    /**
     * 验证是否允许修改密码更改
     * 
     * @param userId      用户id
     * @param passwordDTO 验证密码的参数
     */
    void changePassword(Long userId, PasswordDTO passwordDTO);

    /**
     * 直接修改密码，不需要验证
     * 
     * @param userId   用户id
     * @param password 密码
     */
    void changePasswordDirectly(Long userId, String password);

    /**
     * 重置密码
     * 
     * @param userId 用户id
     * @return 随机生成符合规范的密码
     */
    String resetPassword(Long userId);

    /**
     * 管理员分页用户信息
     * 
     * @param dto 分页查找参数
     * @return 用户列表分页数据
     */
    PageData<AdminPageUserVO> page(AdminPageUserDTO dto);

    /**
     * 批量修改用户状态
     * 
     * @param status  用户状态
     * @param userIds 用户ID数组
     */
    void changeStatus(Integer status, String[] userIds);

    /**
     * 获取是否允许用户注册
     * 
     * @return 是否允许用户注册
     */
    boolean getAllowUserRegister();

    /**
     * 获取聊天次数统计
     * 
     * @param date 查询日期 (YYYY-MM-DD)
     * @param minCount 最小聊天次数
     * @return 聊天次数统计列表
     */
    List<ChatCountVO> getChatCount(String date, Integer minCount);

    /**
     * 获取所有用户的聊天统计信息（最近3个月和当月）
     * 
     * @return 用户聊天统计列表
     */
    List<UserChatStatsVO> getUserChatStats();

    /**
     * 检查并重新启用因聊天限制而被禁用的账户
     * 当max_chat_count参数增加时调用此方法
     * 
     * @param newMaxChatCount 新的最大聊天次数限制
     */
    void checkAndReEnableAccountsForNewLimit(Integer newMaxChatCount);

    /**
     * 检查并禁用超过新聊天限制的账户
     * 当max_chat_count参数减少时调用此方法
     * 
     * @param newMaxChatCount 新的最大聊天次数限制
     */
    void checkAndDisableAccountsForLowerLimit(Integer newMaxChatCount);

    /**
     * 同步数据库中的chat_count_month字段与Manager Portal显示的准确数据
     * 将从ai_agent_chat_history表计算的准确聊天次数同步到sys_user表
     */
    void syncChatCountToDatabase();
}
