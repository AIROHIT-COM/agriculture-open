package com.airohit.agriculture.module.system.service.permission;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.airohit.agriculture.framework.common.exception.util.ServiceExceptionUtil;
import com.airohit.agriculture.framework.common.util.collection.CollectionUtils;
import com.airohit.agriculture.module.system.convert.permission.MenuConvert;
import com.airohit.agriculture.module.system.dal.dataobject.permission.MenuDO;
import com.airohit.agriculture.module.system.dal.mysql.permission.MenuMapper;
import com.airohit.agriculture.module.system.entity.admin.permission.vo.menu.MenuCreateReqVO;
import com.airohit.agriculture.module.system.entity.admin.permission.vo.menu.MenuListReqVO;
import com.airohit.agriculture.module.system.entity.admin.permission.vo.menu.MenuUpdateReqVO;
import com.airohit.agriculture.module.system.enums.permission.MenuIdEnum;
import com.airohit.agriculture.module.system.enums.permission.MenuTypeEnum;
import com.airohit.agriculture.module.system.service.tenant.TenantService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.airohit.agriculture.module.system.enums.ErrorCodeConstants.*;

/**
 * 菜单 Service 实现
 *
 * @author shiminghao
 */
@Service
@Slf4j
public class MenuServiceImpl implements MenuService {

    /**
     * 定时执行 {@link #schedulePeriodicRefresh()} 的周期
     * 因为已经通过 Redis Pub/Sub 机制，所以频率不需要高
     */
    private static final long SCHEDULER_PERIOD = 5 * 60 * 1000L;

    /**
     * 菜单缓存
     * key：菜单编号
     * <p>
     * 这里声明 volatile 修饰的原因是，每次刷新时，直接修改指向
     */
    private volatile ImmutableMap<Long, MenuDO> menuCache;
    /**
     * 权限与菜单缓存
     * key：权限 {@link MenuDO#getPermission()}
     * value：MenuDO 数组，因为一个权限可能对应多个 MenuDO 对象
     * <p>
     * 这里声明 volatile 修饰的原因是，每次刷新时，直接修改指向
     */
    private volatile Multimap<String, MenuDO> permissionMenuCache;
    /**
     * 缓存菜单的最大更新时间，用于后续的增量轮询，判断是否有更新
     */
    private volatile LocalDateTime maxUpdateTime;

    @Resource
    private MenuMapper menuMapper;
    @Resource
    private PermissionService permissionService;
    @Resource
    @Lazy // 延迟，避免循环依赖报错
    private TenantService tenantService;


    /**
     * 初始化 {@link #menuCache} 和 {@link #permissionMenuCache} 缓存
     */
    @Override
    @PostConstruct
    public synchronized void initLocalCache() {
        initLocalCacheIfUpdate(null);
    }

    @Scheduled(fixedDelay = SCHEDULER_PERIOD, initialDelay = SCHEDULER_PERIOD)
    public void schedulePeriodicRefresh() {
        initLocalCacheIfUpdate(this.maxUpdateTime);
    }

    /**
     * 刷新本地缓存
     *
     * @param maxUpdateTime 最大更新时间
     *                      1. 如果 maxUpdateTime 为 null，则“强制”刷新缓存
     *                      2. 如果 maxUpdateTime 不为 null，判断自 maxUpdateTime 是否有数据发生变化，有的情况下才刷新缓存
     */
    private void initLocalCacheIfUpdate(LocalDateTime maxUpdateTime) {
        // 第一步：基于 maxUpdateTime 判断缓存是否刷新。
        // 如果没有增量的数据变化，则不进行本地缓存的刷新
        if (maxUpdateTime != null
                && menuMapper.selectCountByUpdateTimeGt(maxUpdateTime) == 0) {
            log.info("[initLocalCacheIfUpdate][数据未发生变化({})，本地缓存不刷新]", maxUpdateTime);
            return;
        }
        List<MenuDO> menuList = menuMapper.selectList();
        log.info("[initLocalCacheIfUpdate][缓存菜单，数量为:{}]", menuList.size());

        // 第二步：构建缓存。
        ImmutableMap.Builder<Long, MenuDO> menuCacheBuilder = ImmutableMap.builder();
        ImmutableMultimap.Builder<String, MenuDO> permMenuCacheBuilder = ImmutableMultimap.builder();
        menuList.forEach(menuDO -> {
            menuCacheBuilder.put(menuDO.getId(), menuDO);
            if (StrUtil.isNotEmpty(menuDO.getPermission())) { // 会存在 permission 为 null 的情况，导致 put 报 NPE 异常
                permMenuCacheBuilder.put(menuDO.getPermission(), menuDO);
            }
        });
        menuCache = menuCacheBuilder.build();
        permissionMenuCache = permMenuCacheBuilder.build();

        // 第三步：设置最新的 maxUpdateTime，用于下次的增量判断。
        this.maxUpdateTime = CollectionUtils.getMaxValue(menuList, MenuDO::getUpdateTime);
    }

    @Override
    public Long createMenu(MenuCreateReqVO reqVO) {
        // 校验父菜单存在
        checkParentResource(reqVO.getParentId(), null);
        // 校验菜单（自己）
        checkResource(reqVO.getParentId(), reqVO.getName(), null);
        // 插入数据库
        MenuDO menu = MenuConvert.INSTANCE.convert(reqVO);
        initMenuProperty(menu);
        menuMapper.insert(menu);
        // 发送刷新消息
        initLocalCache();
        // 返回
        return menu.getId();
    }

    @Override
    public void updateMenu(MenuUpdateReqVO reqVO) {
        // 校验更新的菜单是否存在
        if (menuMapper.selectById(reqVO.getId()) == null) {
            throw ServiceExceptionUtil.exception(MENU_NOT_EXISTS);
        }
        // 校验父菜单存在
        checkParentResource(reqVO.getParentId(), reqVO.getId());
        // 校验菜单（自己）
        checkResource(reqVO.getParentId(), reqVO.getName(), reqVO.getId());
        // 更新到数据库
        MenuDO updateObject = MenuConvert.INSTANCE.convert(reqVO);
        initMenuProperty(updateObject);
        menuMapper.updateById(updateObject);
        // 发送刷新消息
        initLocalCache();
    }

    /**
     * 删除菜单
     *
     * @param menuId 菜单编号
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteMenu(Long menuId) {
        // 校验是否还有子菜单
        if (menuMapper.selectCountByParentId(menuId) > 0) {
            throw ServiceExceptionUtil.exception(MENU_EXISTS_CHILDREN);
        }
        // 校验删除的菜单是否存在
        if (menuMapper.selectById(menuId) == null) {
            throw ServiceExceptionUtil.exception(MENU_NOT_EXISTS);
        }
        // 标记删除
        menuMapper.deleteById(menuId);
        // 删除授予给角色的权限
        permissionService.processMenuDeleted(menuId);
        // 发送刷新消息. 注意，需要事务提交后，在进行发送刷新消息。不然 db 还未提交，结果缓存先刷新了
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                initLocalCache();
            }

        });
    }

    @Override
    public List<MenuDO> getMenus() {
        return menuMapper.selectList();
    }

    @Override
    public List<MenuDO> getTenantMenus(MenuListReqVO reqVO) {
        List<MenuDO> menus = getMenus(reqVO);
        // 开启多租户的情况下，需要过滤掉未开通的菜单
        tenantService.handleTenantMenu(menuIds -> menus.removeIf(menu -> !CollUtil.contains(menuIds, menu.getId())));
        return menus;
    }

    @Override
    public List<MenuDO> getMenus(MenuListReqVO reqVO) {
        return menuMapper.selectList(reqVO);
    }

    @Override
    public List<MenuDO> getMenuListFromCache(Collection<Integer> menuTypes, Collection<Integer> menusStatuses) {
        // 任一一个参数为空，则返回空
        if (CollectionUtils.isAnyEmpty(menuTypes, menusStatuses)) {
            return Collections.emptyList();
        }
        // 创建新数组，避免缓存被修改
        return menuCache.values().stream().filter(menu -> menuTypes.contains(menu.getType())
                        && menusStatuses.contains(menu.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuDO> getMenuListFromCache(Collection<Long> menuIds, Collection<Integer> menuTypes,
                                             Collection<Integer> menusStatuses) {
        // 任一一个参数为空，则返回空
        if (CollectionUtils.isAnyEmpty(menuIds, menuTypes, menusStatuses)) {
            return Collections.emptyList();
        }
        return menuCache.values().stream().filter(menu -> menuIds.contains(menu.getId())
                        && menuTypes.contains(menu.getType())
                        && menusStatuses.contains(menu.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MenuDO> getMenuListByPermissionFromCache(String permission) {
        return new ArrayList<>(permissionMenuCache.get(permission));
    }

    @Override
    public MenuDO getMenu(Long id) {
        return menuMapper.selectById(id);
    }

    /**
     * 校验父菜单是否合法
     * <p>
     * 1. 不能设置自己为父菜单
     * 2. 父菜单不存在
     * 3. 父菜单必须是 {@link MenuTypeEnum#MENU} 菜单类型
     *
     * @param parentId 父菜单编号
     * @param childId  当前菜单编号
     */
    @VisibleForTesting
    public void checkParentResource(Long parentId, Long childId) {
        if (parentId == null || MenuIdEnum.ROOT.getId().equals(parentId)) {
            return;
        }
        // 不能设置自己为父菜单
        if (parentId.equals(childId)) {
            throw ServiceExceptionUtil.exception(MENU_PARENT_ERROR);
        }
        MenuDO menu = menuMapper.selectById(parentId);
        // 父菜单不存在
        if (menu == null) {
            throw ServiceExceptionUtil.exception(MENU_PARENT_NOT_EXISTS);
        }
        // 父菜单必须是目录或者菜单类型
        if (!MenuTypeEnum.DIR.getType().equals(menu.getType())
                && !MenuTypeEnum.MENU.getType().equals(menu.getType())) {
            throw ServiceExceptionUtil.exception(MENU_PARENT_NOT_DIR_OR_MENU);
        }
    }

    /**
     * 校验菜单是否合法
     * <p>
     * 1. 校验相同父菜单编号下，是否存在相同的菜单名
     *
     * @param name     菜单名字
     * @param parentId 父菜单编号
     * @param id       菜单编号
     */
    @VisibleForTesting
    public void checkResource(Long parentId, String name, Long id) {
        MenuDO menu = menuMapper.selectByParentIdAndName(parentId, name);
        if (menu == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的菜单
        if (id == null) {
            throw ServiceExceptionUtil.exception(MENU_NAME_DUPLICATE);
        }
        if (!menu.getId().equals(id)) {
            throw ServiceExceptionUtil.exception(MENU_NAME_DUPLICATE);
        }
    }

    /**
     * 初始化菜单的通用属性。
     * <p>
     * 例如说，只有目录或者菜单类型的菜单，才设置 icon
     *
     * @param menu 菜单
     */
    private void initMenuProperty(MenuDO menu) {
        // 菜单为按钮类型时，无需 component、icon、path 属性，进行置空
        if (MenuTypeEnum.BUTTON.getType().equals(menu.getType())) {
            menu.setComponent("");
            menu.setIcon("");
            menu.setPath("");
        }
    }

}
