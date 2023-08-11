package com.api.gateway.service.impl;

import com.api.gateway.constants.EnabledEnum;
import com.api.gateway.constants.MatchMethodEnum;
import com.api.gateway.constants.MatchObjectEnum;
import com.api.gateway.constants.GatewayCodeEnum;
import com.api.gateway.event.RuleAddEvent;
import com.api.gateway.event.RuleDeleteEvent;
import com.api.gateway.exception.GatewayException;
import com.api.gateway.mapper.AppInstanceMapper;
import com.api.gateway.mapper.AppMapper;
import com.api.gateway.mapper.RouteRuleMapper;
import com.api.gateway.model.dto.AppRuleDTO;
import com.api.gateway.model.dto.ChangeStatusDTO;
import com.api.gateway.model.dto.RuleDTO;
import com.api.gateway.model.entity.App;
import com.api.gateway.model.entity.AppInstance;
import com.api.gateway.model.entity.RouteRule;
import com.api.gateway.model.vo.RuleVO;
import com.api.gateway.service.RuleService;
import com.api.gateway.transfer.AppRuleVOTransfer;
import com.api.gateway.transfer.RuleVOTransfer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RuleServiceImpl implements RuleService {

    @Resource
    private RouteRuleMapper ruleMapper;

    @Resource
    private AppMapper appMapper;

    @Resource
    private AppInstanceMapper instanceMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public List<AppRuleDTO> getEnabledRule() {
        QueryWrapper<App> wrapper = Wrappers.query();
        wrapper.lambda().eq(App::getEnabled, EnabledEnum.ENABLE.getCode());
        List<App> apps = appMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(apps)) {
            return new ArrayList<>();
        }
        List<Integer> appIds = apps.stream().map(App::getId).collect(Collectors.toList());
        Map<Integer, String> nameMap = apps.stream().collect(Collectors.toMap(App::getId, App::getAppName));
        QueryWrapper<RouteRule> query = Wrappers.query();
        query.lambda().in(RouteRule::getAppId, appIds)
                .eq(RouteRule::getEnabled, EnabledEnum.ENABLE.getCode());
        List<RouteRule> routeRules = ruleMapper.selectList(query);
        List<AppRuleDTO> appRuleDTOS = AppRuleVOTransfer.INSTANCE.mapToVOList(routeRules);
        appRuleDTOS.forEach(r -> r.setAppName(nameMap.get(r.getAppId())));
        return appRuleDTOS;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void add(RuleDTO ruleDTO) {
        checkRule(ruleDTO);

        App app = appMapper.selectById(ruleDTO.getAppId());
        RouteRule routeRule = new RouteRule();
        BeanUtils.copyProperties(ruleDTO, routeRule);
        routeRule.setCreatedTime(LocalDateTime.now());
        ruleMapper.insert(routeRule);

        if (EnabledEnum.ENABLE.getCode().equals(ruleDTO.getEnabled())) {
            AppRuleDTO appRuleDTO = new AppRuleDTO();
            BeanUtils.copyProperties(routeRule, appRuleDTO);
            appRuleDTO.setAppName(app.getAppName());
            eventPublisher.publishEvent(new RuleAddEvent(this, appRuleDTO));
        }
    }

    private void checkRule(RuleDTO ruleDTO) {
        QueryWrapper<RouteRule> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(RouteRule::getName, ruleDTO.getName());
        if (!CollectionUtils.isEmpty(ruleMapper.selectList(wrapper))) {
            throw new GatewayException("规则名称不能重复");
        }
        if (MatchObjectEnum.DEFAULT.getCode().equals(ruleDTO.getMatchObject())) {
            ruleDTO.setMatchKey(null);
            ruleDTO.setMatchMethod(null);
            ruleDTO.setMatchRule(null);
        } else {
            if (StringUtils.isEmpty(ruleDTO.getMatchKey()) || ruleDTO.getMatchMethod() == null
                    || StringUtils.isEmpty(ruleDTO.getMatchRule())) {
                throw new GatewayException(GatewayCodeEnum.PARAM_ERROR);
            }
        }
        // check version
        QueryWrapper<AppInstance> query = Wrappers.query();
        query.lambda().eq(AppInstance::getAppId,ruleDTO.getAppId())
            .eq(AppInstance::getVersion,ruleDTO.getVersion());
        List<AppInstance> list = instanceMapper.selectList(query);
        if (CollectionUtils.isEmpty(list)){
            throw new GatewayException("实例版本不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Integer id) {
        RouteRule routeRule = ruleMapper.selectById(id);
        if (routeRule == null) {
            throw new GatewayException(GatewayCodeEnum.PARAM_ERROR);
        }
        AppRuleDTO appRuleDTO = new AppRuleDTO();
        BeanUtils.copyProperties(routeRule, appRuleDTO);
        App app = appMapper.selectById(appRuleDTO.getAppId());
        appRuleDTO.setAppName(app.getAppName());

        ruleMapper.deleteById(id);
        eventPublisher.publishEvent(new RuleDeleteEvent(this, appRuleDTO));
    }

    @Override
    public List<RuleVO> queryList(String appName) {
        Integer appId = null;
        if (!StringUtils.isEmpty(appName)) {
            App app = queryByAppName(appName);
            if (app == null) {
                return Lists.newArrayList();
            }
            appId = app.getId();
        }
        QueryWrapper<RouteRule> query = Wrappers.query();
        query.lambda().eq(Objects.nonNull(appId), RouteRule::getAppId, appId)
        .orderByDesc(RouteRule::getCreatedTime);
        List<RouteRule> rules = ruleMapper.selectList(query);
        if (CollectionUtils.isEmpty(rules)) {
            return Lists.newArrayList();
        }
        List<RuleVO> ruleVOS = RuleVOTransfer.INSTANCE.mapToVOList(rules);
        Map<Integer, String> nameMap = getAppNameMap(ruleVOS.stream().map(r -> r.getAppId()).collect(Collectors.toList()));
        ruleVOS.forEach(ruleVO -> {
            ruleVO.setAppName(nameMap.get(ruleVO.getAppId()));
            ruleVO.setMatchStr(buildMatchStr(ruleVO));
        });
        return ruleVOS;
    }

    private String buildMatchStr(RuleVO ruleVO) {
        if (MatchObjectEnum.DEFAULT.getCode().equals(ruleVO.getMatchObject())){
            return ruleVO.getMatchObject();
        }else {
            StringBuilder sb = new StringBuilder();
            sb.append("["+ruleVO.getMatchKey()+"] ");
            sb.append(MatchMethodEnum.getByCode(ruleVO.getMatchMethod()).getDesc());
            sb.append(" ["+ruleVO.getMatchRule()+"]");
            return sb.toString();
        }
    }

    private Map<Integer, String> getAppNameMap(List<Integer> appIdList) {
        QueryWrapper<App> query = Wrappers.query();
        query.lambda().in(App::getId, appIdList);
        List<App> apps = appMapper.selectList(query);
        return apps.stream().collect(Collectors.toMap(App::getId, App::getAppName));
    }

    private App queryByAppName(String appName) {
        QueryWrapper<App> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(App::getAppName, appName);
        App app = appMapper.selectOne(wrapper);
        return app;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changeStatus(ChangeStatusDTO statusDTO) {
        RouteRule routeRule = new RouteRule();
        routeRule.setId(statusDTO.getId());
        routeRule.setEnabled(statusDTO.getEnabled());
        ruleMapper.updateById(routeRule);
        AppRuleDTO appRuleDTO = new AppRuleDTO();
        BeanUtils.copyProperties(routeRule, appRuleDTO);
        appRuleDTO.setAppName(statusDTO.getAppName());
        if (EnabledEnum.ENABLE.getCode().equals(statusDTO.getEnabled())) {
            eventPublisher.publishEvent(new RuleAddEvent(this, appRuleDTO));
        } else {
            eventPublisher.publishEvent(new RuleDeleteEvent(this, appRuleDTO));
        }
    }
}
