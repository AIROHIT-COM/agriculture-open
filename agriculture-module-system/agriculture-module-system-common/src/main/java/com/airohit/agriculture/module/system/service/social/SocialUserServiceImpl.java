package com.airohit.agriculture.module.system.service.social;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import com.airohit.agriculture.framework.common.util.http.HttpUtils;
import com.airohit.agriculture.framework.tenant.core.aop.TenantIgnore;
import com.airohit.agriculture.framework.tenant.core.util.TenantUtils;
import com.airohit.agriculture.module.system.api.social.dto.SocialUserBindReqDTO;
import com.airohit.agriculture.module.system.dal.dataobject.social.SocialUserBindDO;
import com.airohit.agriculture.module.system.dal.dataobject.social.SocialUserDO;
import com.airohit.agriculture.module.system.dal.mysql.social.SocialUserBindMapper;
import com.airohit.agriculture.module.system.dal.mysql.social.SocialUserMapper;
import com.airohit.agriculture.module.system.enums.social.SocialTypeEnum;
import com.xkcoding.justauth.AuthRequestFactory;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.airohit.agriculture.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.airohit.agriculture.framework.common.util.collection.CollectionUtils.convertSet;
import static com.airohit.agriculture.framework.common.util.json.JsonUtils.toJsonString;
import static com.airohit.agriculture.module.system.enums.ErrorCodeConstants.*;

/**
 * 社交用户 Service 实现类
 *
 * @author shiminghao
 */
@Service
@Validated
@Slf4j
public class SocialUserServiceImpl implements SocialUserService {

    @Resource
    private AuthRequestFactory authRequestFactory;

    @Resource
    private SocialUserBindMapper socialUserBindMapper;
    @Resource
    private SocialUserMapper socialUserMapper;

    @Override
    public String getAuthorizeUrl(Integer type, String redirectUri) {
        // 获得对应的 AuthRequest 实现
        AuthRequest authRequest = authRequestFactory.get(SocialTypeEnum.valueOfType(type).getSource());
        // 生成跳转地址
        String authorizeUri = authRequest.authorize(AuthStateUtils.createState());
        return HttpUtils.replaceUrlQuery(authorizeUri, "redirect_uri", redirectUri);
    }

    @Override
    public SocialUserDO authSocialUser(Integer type, String code, String state) {
        // 优先从 DB 中获取，因为 code 有且可以使用一次。
        // 在社交登录时，当未绑定 User 时，需要绑定登录，此时需要 code 使用两次
        AtomicReference<SocialUserDO> socialUser = new AtomicReference<>(socialUserMapper.selectByTypeAndCodeAnState(type, code, state));
        if (socialUser.get() != null) {
            return socialUser.get();
        }

        // 请求获取
        AuthUser authUser = getAuthUser(type, code, state);
        Assert.notNull(authUser, "三方用户不能为空");
        TenantUtils.execute(0L, () -> socialUser.set(socialUserMapper.selectByTypeAndOpenid(type, authUser.getUuid())));
        // 保存到 DB 中

        if (socialUser.get() == null) {
            socialUser.set(new SocialUserDO());
        }
        socialUser.get().setType(type).setCode(code).setState(state) // 需要保存 code + state 字段，保证后续可查询
                .setOpenid(authUser.getUuid()).setToken(authUser.getToken().getAccessToken()).setRawTokenInfo((toJsonString(authUser.getToken())))
                .setNickname(authUser.getNickname()).setAvatar(authUser.getAvatar()).setRawUserInfo(toJsonString(authUser.getRawUserInfo()));
        TenantUtils.execute(0L, () -> {
            if (socialUser.get().getId() == null) {
                socialUserMapper.insert(socialUser.get());
            } else {
                socialUserMapper.updateById(socialUser.get());
            }
        });
        return socialUser.get();
    }

    @Override
    @TenantIgnore
    public List<SocialUserDO> getSocialUserList(Long userId, Integer userType) {
        // 获得绑定
        List<SocialUserBindDO> socialUserBinds = socialUserBindMapper.selectListByUserIdAndUserType(userId, userType);
        if (CollUtil.isEmpty(socialUserBinds)) {
            return Collections.emptyList();
        }
        // 获得社交用户
        return socialUserMapper.selectBatchIds(convertSet(socialUserBinds, SocialUserBindDO::getSocialUserId));
    }

    @Override
    @Transactional
    public void bindSocialUser(SocialUserBindReqDTO reqDTO) {
        // 获得社交用户
        SocialUserDO socialUser = authSocialUser(reqDTO.getType(), reqDTO.getCode(), reqDTO.getState());
        Assert.notNull(socialUser, "社交用户不能为空");

        // 社交用户可能之前绑定过别的用户，需要进行解绑
        socialUserBindMapper.deleteByUserTypeAndSocialUserId(reqDTO.getUserType(), socialUser.getId());

        // 用户可能之前已经绑定过该社交类型，需要进行解绑
        socialUserBindMapper.deleteByUserTypeAndUserIdAndSocialType(reqDTO.getUserType(), reqDTO.getUserId(),
                socialUser.getType());

        // 绑定当前登录的社交用户
        SocialUserBindDO socialUserBind = SocialUserBindDO.builder()
                .userId(reqDTO.getUserId()).userType(reqDTO.getUserType())
                .socialUserId(socialUser.getId()).socialType(socialUser.getType()).build();
        socialUserBindMapper.insert(socialUserBind);
    }

    @Override
    public void unbindSocialUser(Long userId, Integer userType, Integer type, String openid) {
        // 获得 openid 对应的 SocialUserDO 社交用户
        SocialUserDO socialUser = socialUserMapper.selectByTypeAndOpenid(type, openid);
        if (socialUser == null) {
            throw exception(SOCIAL_USER_NOT_FOUND);
        }

        // 获得对应的社交绑定关系
        socialUserBindMapper.deleteByUserTypeAndUserIdAndSocialType(userType, userId, socialUser.getType());
    }

    @Override
    public Long getBindUserId(Integer userType, Integer type, String code, String state) {
        // 获得社交用户
        SocialUserDO socialUser = authSocialUser(type, code, state);
        Assert.notNull(socialUser, "社交用户不能为空");

        // 如果未绑定的社交用户，则无法自动登录，进行报错
        SocialUserBindDO socialUserBind = socialUserBindMapper.selectByUserTypeAndSocialUserId(userType,
                socialUser.getId());
        if (socialUserBind == null) {
            throw exception(AUTH_THIRD_LOGIN_NOT_BIND);
        }
        return socialUserBind.getUserId();
    }

    @Override
    public SocialUserBindDO getBindUser(Integer userType, Integer type, String code, String state) {
        // 获得社交用户
        SocialUserDO socialUser = authSocialUser(type, code, state);
        Assert.notNull(socialUser, "社交用户不能为空");
        // 如果未绑定的社交用户，则无法自动登录，进行报错
        return socialUserBindMapper.selectByUserTypeAndSocialUserId(userType,
                socialUser.getId());
    }

    /**
     * 请求社交平台，获得授权的用户
     *
     * @param type  社交平台的类型
     * @param code  授权码
     * @param state 授权 state
     * @return 授权的用户
     */
    private AuthUser getAuthUser(Integer type, String code, String state) {
        AuthRequest authRequest = authRequestFactory.get(SocialTypeEnum.valueOfType(type).getSource());
        AuthCallback authCallback = AuthCallback.builder().code(code).state(state).build();
        AuthResponse<?> authResponse = authRequest.login(authCallback);
        log.info("[getAuthUser][请求社交平台 type({}) request({}) response({})]", type,
                toJsonString(authCallback), toJsonString(authResponse));
        if (!authResponse.ok()) {
            throw exception(SOCIAL_USER_AUTH_FAILURE_MSG);
        }
        return (AuthUser) authResponse.getData();
    }

}
