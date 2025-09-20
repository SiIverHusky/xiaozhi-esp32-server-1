package xiaozhi.modules.sys.controller;

import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.common.validator.ValidatorUtils;
import xiaozhi.modules.device.dto.DevicePageUserDTO;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.device.vo.UserShowDeviceListVO;
import xiaozhi.modules.sys.dto.AdminPageUserDTO;
import xiaozhi.modules.sys.service.SysUserService;
import xiaozhi.modules.sys.vo.AdminPageUserVO;
import xiaozhi.modules.sys.vo.ChatCountVO;
import xiaozhi.modules.sys.vo.UserChatStatsVO;

/**
 * 管理员控制层
 *
 * @author zjy
 * @since 2025-3-25
 */
@AllArgsConstructor
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin Management")
@Slf4j
public class AdminController {
    private final SysUserService sysUserService;

    private final DeviceService deviceService;

    @GetMapping("/users")
    @Operation(summary = "paginated user search")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({
            @Parameter(name = "mobile", description = "current mobile number", required = false),
            @Parameter(name = Constant.PAGE, description = "current page, start from 1", required = true),
            @Parameter(name = Constant.LIMIT, description = "records per page", required = true),
    })
    public Result<PageData<AdminPageUserVO>> pageUser(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        AdminPageUserDTO dto = new AdminPageUserDTO();
        dto.setMobile((String) params.get("mobile"));
        dto.setLimit((String) params.get(Constant.LIMIT));
        dto.setPage((String) params.get(Constant.PAGE));
        ValidatorUtils.validateEntity(dto);
        PageData<AdminPageUserVO> page = sysUserService.page(dto);
        return new Result<PageData<AdminPageUserVO>>().ok(page);
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "reset password")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<String> update(
            @PathVariable Long id) {
        String password = sysUserService.resetPassword(id);
        return new Result<String>().ok(password);
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "delete user")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteById(id);
        return new Result<>();
    }

    @PutMapping("/users/changeStatus/{status}")
    @Operation(summary = "batch edit of user status")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameter(name = "status", description = "user status", required = true)
    public Result<Void> changeStatus(@PathVariable Integer status, @RequestBody String[] userIds) {
        sysUserService.changeStatus(status, userIds);
        return new Result<Void>();
    }

    @GetMapping("/device/all")
    @Operation(summary = "paginated finding device")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({
            @Parameter(name = "keywords", description = "device keywords", required = false),
            @Parameter(name = Constant.PAGE, description = "current page, start from 1", required = true),
            @Parameter(name = Constant.LIMIT, description = "records per page", required = true),
    })
    public Result<PageData<UserShowDeviceListVO>> pageDevice(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        DevicePageUserDTO dto = new DevicePageUserDTO();
        dto.setKeywords((String) params.get("keywords"));
        dto.setLimit((String) params.get(Constant.LIMIT));
        dto.setPage((String) params.get(Constant.PAGE));
        ValidatorUtils.validateEntity(dto);
        PageData<UserShowDeviceListVO> page = deviceService.page(dto);
        return new Result<PageData<UserShowDeviceListVO>>().ok(page);
    }

    @GetMapping("/chat-count")
    @Operation(summary = "get chat counts by date")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({
            @Parameter(name = "date", description = "date to query (YYYY-MM-DD)", required = true),
            @Parameter(name = "minCount", description = "minimum chat count threshold", required = false),
    })
    public Result<List<ChatCountVO>> getChatCount(
            @RequestParam String date,
            @RequestParam(defaultValue = "0") Integer minCount) {
        log.info("Getting chat count for date: {} with minCount: {}", date, minCount);
        try {
            List<ChatCountVO> chatCounts = sysUserService.getChatCount(date, minCount);
            log.info("Returning {} chat count records", chatCounts != null ? chatCounts.size() : 0);
            return new Result<List<ChatCountVO>>().ok(chatCounts);
        } catch (Exception e) {
            log.error("Error getting chat count for date: {} with minCount: {}", date, minCount, e);
            return new Result<List<ChatCountVO>>().error("Failed to get chat count: " + e.getMessage());
        }
    }

    @GetMapping("/users/chat-stats")
    @Operation(summary = "get chat statistics for all users")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<UserChatStatsVO>> getUserChatStats() {
        log.info("Getting chat statistics for all users");
        try {
            List<UserChatStatsVO> chatStats = sysUserService.getUserChatStats();
            log.info("Returning {} user chat stats records", chatStats != null ? chatStats.size() : 0);
            return new Result<List<UserChatStatsVO>>().ok(chatStats);
        } catch (Exception e) {
            log.error("Error getting user chat statistics", e);
            return new Result<List<UserChatStatsVO>>().error("Failed to get user chat statistics: " + e.getMessage());
        }
    }

    @PostMapping("/check-re-enable-accounts/{newLimit}")
    @Operation(summary = "manually check and re-enable accounts for new chat limit (DEBUG)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<String> checkReEnableAccounts(@PathVariable Integer newLimit) {
        log.info("=== ADMIN DEBUG === Manually checking and re-enabling accounts with new limit: {}", newLimit);
        try {
            sysUserService.checkAndReEnableAccountsForNewLimit(newLimit);
            return new Result<String>().ok("Check completed for new limit: " + newLimit);
        } catch (Exception e) {
            log.error("=== ADMIN DEBUG === Error checking re-enable accounts", e);
            return new Result<String>().error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/check-disable-accounts/{newLimit}")
    @Operation(summary = "manually check and disable accounts for lower chat limit (DEBUG)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<String> checkDisableAccounts(@PathVariable Integer newLimit) {
        log.info("=== ADMIN DEBUG === Manually checking and disabling accounts with lower limit: {}", newLimit);
        try {
            sysUserService.checkAndDisableAccountsForLowerLimit(newLimit);
            return new Result<String>().ok("Check completed for lower limit: " + newLimit);
        } catch (Exception e) {
            log.error("=== ADMIN DEBUG === Error checking disable accounts", e);
            return new Result<String>().error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/sync-chat-counts")
    @Operation(summary = "synchronize chat counts from reliable source to database (DEBUG)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<String> syncChatCountsToDatabase() {
        log.info("=== ADMIN DEBUG === Manually synchronizing chat counts to database");
        try {
            sysUserService.syncChatCountToDatabase();
            return new Result<String>().ok("Chat count synchronization completed successfully");
        } catch (Exception e) {
            log.error("=== ADMIN DEBUG === Error synchronizing chat counts", e);
            return new Result<String>().error("Error: " + e.getMessage());
        }
    }
}
