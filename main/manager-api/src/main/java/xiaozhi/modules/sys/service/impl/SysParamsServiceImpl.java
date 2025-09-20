package xiaozhi.modules.sys.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.sys.dao.SysParamsDao;
import xiaozhi.modules.sys.dto.SysParamsDTO;
import xiaozhi.modules.sys.entity.SysParamsEntity;
import xiaozhi.modules.sys.event.MaxChatCountUpdatedEvent;
import xiaozhi.modules.sys.redis.SysParamsRedis;
import xiaozhi.modules.sys.service.SysParamsService;

/**
 * 参数管理
 */
@AllArgsConstructor
@Service
public class SysParamsServiceImpl extends BaseServiceImpl<SysParamsDao, SysParamsEntity> implements SysParamsService {
    private static final Logger logger = LoggerFactory.getLogger(SysParamsServiceImpl.class);
    
    private final SysParamsRedis sysParamsRedis;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageData<SysParamsDTO> page(Map<String, Object> params) {
        IPage<SysParamsEntity> page = baseDao.selectPage(
                getPage(params, null, false),
                getWrapper(params));

        return getPageData(page, SysParamsDTO.class);
    }

    @Override
    public List<SysParamsDTO> list(Map<String, Object> params) {
        List<SysParamsEntity> entityList = baseDao.selectList(getWrapper(params));

        return ConvertUtils.sourceToTarget(entityList, SysParamsDTO.class);
    }

    private QueryWrapper<SysParamsEntity> getWrapper(Map<String, Object> params) {
        String paramCode = (String) params.get("paramCode");

        QueryWrapper<SysParamsEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("param_type", 1);
        wrapper.nested(StringUtils.isNotBlank(paramCode), i -> i.like("param_code", paramCode)
                .or()
                .like("remark", paramCode));

        return wrapper;
    }

    @Override
    public SysParamsDTO get(Long id) {
        SysParamsEntity entity = baseDao.selectById(id);

        return ConvertUtils.sourceToTarget(entity, SysParamsDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SysParamsDTO dto) {
        validateParamValue(dto);

        SysParamsEntity entity = ConvertUtils.sourceToTarget(dto, SysParamsEntity.class);
        insert(entity);

        sysParamsRedis.set(entity.getParamCode(), entity.getParamValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysParamsDTO dto) {
        validateParamValue(dto);
        detectingSMSParameters(dto.getParamCode(), dto.getParamValue());
        
        // 检查是否是max_chat_count参数更新
        if (Constant.MAX_CHAT_COUNT.equals(dto.getParamCode())) {
            handleMaxChatCountUpdate(dto.getParamValue());
        }
        
        SysParamsEntity entity = ConvertUtils.sourceToTarget(dto, SysParamsEntity.class);
        updateById(entity);

        sysParamsRedis.set(entity.getParamCode(), entity.getParamValue());
    }

    /**
     * 校验参数值类型
     */
    private void validateParamValue(SysParamsDTO dto) {
        if (dto == null) {
            throw new RenException(ErrorCode.PARAM_VALUE_NULL);
        }

        if (StringUtils.isBlank(dto.getParamValue())) {
            throw new RenException(ErrorCode.PARAM_VALUE_NULL);
        }

        if (StringUtils.isBlank(dto.getValueType())) {
            throw new RenException(ErrorCode.PARAM_TYPE_NULL);
        }

        String valueType = dto.getValueType().toLowerCase();
        String paramValue = dto.getParamValue();

        switch (valueType) {
            case "string":
                break;
            case "array":
                break;
            case "number":
                try {
                    Double.parseDouble(paramValue);
                } catch (NumberFormatException e) {
                    throw new RenException(ErrorCode.PARAM_NUMBER_INVALID);
                }
                break;
            case "boolean":
                if (!"true".equalsIgnoreCase(paramValue) && !"false".equalsIgnoreCase(paramValue)) {
                    throw new RenException(ErrorCode.PARAM_BOOLEAN_INVALID);
                }
                break;
            case "json":
                try {
                    // 首先检查是否以 { 开头，以 } 结尾
                    String trimmedValue = paramValue.trim();
                    if (!trimmedValue.startsWith("{") || !trimmedValue.endsWith("}")) {
                        throw new RenException(ErrorCode.PARAM_JSON_INVALID);
                    }
                    // 然后尝试解析JSON
                    JsonUtils.parseObject(paramValue, Object.class);
                } catch (Exception e) {
                    throw new RenException(ErrorCode.PARAM_JSON_INVALID);
                }
                break;
            default:
                throw new RenException(ErrorCode.PARAM_TYPE_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String[] ids) {
        // 删除Redis数据
        List<String> paramCodeList = baseDao.getParamCodeList(ids);
        String[] paramCodes = paramCodeList.toArray(new String[paramCodeList.size()]);
        if (paramCodes.length > 0) {
            sysParamsRedis.delete(paramCodes);
        }

        // 删除
        deleteBatchIds(Arrays.asList(ids));
    }

    @Override
    public String getValue(String paramCode, Boolean fromCache) {
        String paramValue = null;
        if (fromCache) {
            paramValue = sysParamsRedis.get(paramCode);
            if (paramValue == null) {
                paramValue = baseDao.getValueByCode(paramCode);

                sysParamsRedis.set(paramCode, paramValue);
            }
        } else {
            paramValue = baseDao.getValueByCode(paramCode);
        }
        return paramValue;
    }

    @Override
    public <T> T getValueObject(String paramCode, Class<T> clazz) {
        String paramValue = getValue(paramCode, true);
        if (StringUtils.isNotBlank(paramValue)) {
            return JsonUtils.parseObject(paramValue, clazz);
        }

        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateValueByCode(String paramCode, String paramValue) {
        // 检查是否是max_chat_count参数更新
        if (Constant.MAX_CHAT_COUNT.equals(paramCode)) {
            handleMaxChatCountUpdate(paramValue);
        }
        
        int count = baseDao.updateValueByCode(paramCode, paramValue);
        sysParamsRedis.set(paramCode, paramValue);
        return count;
    }

    @Override
    public void initServerSecret() {
        // 获取服务器密钥
        String secretParam = getValue(Constant.SERVER_SECRET, false);
        if (StringUtils.isBlank(secretParam) || "null".equals(secretParam)) {
            String newSecret = UUID.randomUUID().toString();
            updateValueByCode(Constant.SERVER_SECRET, newSecret);
        }
    }

    /**
     * 检测短信参数是否符合要求
     * 
     * @param paramCode  参数编码
     * @param paramValue 参数值
     * @return 是否通过
     */
    private boolean detectingSMSParameters(String paramCode, String paramValue) {
        // 判断是否是开启手机注册的参数编码，如果不是参数编码，着不需要检测其他短信参数，直接返回true
        if (!Constant.SysMSMParam.SERVER_ENABLE_MOBILE_REGISTER.getValue().equals(paramCode)) {
            return true;
        }
        // 判断是否为关闭，如果是关闭短信注册，着不需要检测其他短信参数，直接返回true
        if ("false".equalsIgnoreCase(paramValue)) {
            return true;
        }
        // 检测短信关联参数是否为空
        ArrayList<String> list = new ArrayList<String>();
        list.add(Constant.SysMSMParam.SERVER_SMS_MAX_SEND_COUNT.getValue());
        list.add(Constant.SysMSMParam.ALIYUN_SMS_ACCESS_KEY_ID.getValue());
        list.add(Constant.SysMSMParam.ALIYUN_SMS_ACCESS_KEY_SECRET.getValue());
        list.add(Constant.SysMSMParam.ALIYUN_SMS_SIGN_NAME.getValue());
        list.add(Constant.SysMSMParam.ALIYUN_SMS_SMS_CODE_TEMPLATE_CODE.getValue());
        StringBuilder str = new StringBuilder();
        list.forEach(item -> {
            if (!StringUtils.isNoneBlank(item)) {
                str.append(",").append(item);
            }
        });
        if (!str.isEmpty()) {
            String promptStr = "%s these parameters cannot be null";
            String substring = str.substring(1, str.length());
            throw new RenException(promptStr.formatted(substring));
        }
        return true;
    }

    /**
     * 处理max_chat_count参数更新
     * 当参数值增加时，重新启用符合新限制的被禁用账户
     */
    private void handleMaxChatCountUpdate(String newParamValue) {
        try {
            if (StringUtils.isBlank(newParamValue)) {
                return;
            }
            
            logger.info("=== CHAT LIMIT UPDATE === Processing max_chat_count parameter update to: {}", newParamValue);
            
            // Get old value from Redis
            String oldParamValue = sysParamsRedis.get(Constant.MAX_CHAT_COUNT);
            Integer oldMaxChatCount = null;
            Integer newMaxChatCount = null;
            
            try {
                if (StringUtils.isNotBlank(oldParamValue)) {
                    oldMaxChatCount = Integer.parseInt(oldParamValue);
                }
                newMaxChatCount = Integer.parseInt(newParamValue);
            } catch (NumberFormatException e) {
                logger.warn("=== CHAT LIMIT UPDATE === Invalid max_chat_count value - old: {}, new: {}", oldParamValue, newParamValue);
                return;
            }
            
            // Publish event for other services to handle
            MaxChatCountUpdatedEvent event = new MaxChatCountUpdatedEvent(this, oldMaxChatCount, newMaxChatCount);
            eventPublisher.publishEvent(event);
            logger.info("=== CHAT LIMIT UPDATE === Published MAX_CHAT_COUNT update event: {} -> {}", oldMaxChatCount, newMaxChatCount);
            
        } catch (Exception e) {
            logger.error("=== CHAT LIMIT UPDATE === Error processing max_chat_count update", e);
        }
    }
}