package com.ruoyi.system.service.impl;

import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.BusinessException;
import com.ruoyi.system.domain.SysDept;
import com.ruoyi.system.domain.SysRole;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.service.ISysDeptService;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门管理 服务实现
 *
 * @author ruoyi
 */
@Service
public class SysDeptServiceImpl implements ISysDeptService {

    private final SysDeptMapper deptMapper;

    @Autowired
    public SysDeptServiceImpl(SysDeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    /**
     * 查询部门管理数据
     *
     * @return 部门信息集合
     */
    @Override
    @DataScope(tableAlias = "d")
    public List<SysDept> selectDeptList(SysDept dept) {
        return deptMapper.selectDeptList(dept);
    }

    /**
     * 查询部门管理树
     * @param dept 部门信息
     * @return 所有部门信息
     */
    @Override
    @DataScope(tableAlias = "d")
    public List<Map<String, Object>> selectDeptTree(SysDept dept) {
        List<SysDept> deptList = selectDeptList(dept);
        return getTrees(deptList, false, null);
    }

    /**
     * 根据角色ID查询部门（数据权限）
     *
     * @param role 角色对象
     * @return 部门列表（数据权限）
     */
    @Override
    public List<Map<String, Object>> roleDeptTreeData(SysRole role) {
        Long roleId = role.getRoleId();
        List<Map<String, Object>> trees;
        List<SysDept> deptList = selectDeptList(new SysDept());
        if (ObjectUtils.allNotNull(roleId)) {
            List<String> roleDeptList = deptMapper.selectRoleDeptTree(roleId);
            trees = getTrees(deptList, true, roleDeptList);
        } else {
            trees = getTrees(deptList, false, null);
        }
        return trees;
    }

    /**
     * 对象转部门树
     *
     * @param deptList     部门列表
     * @param isCheck      是否需要选中
     * @param roleDeptList 角色已存在菜单列表
     * @return 部门树
     */
    private List<Map<String, Object>> getTrees(List<SysDept> deptList, boolean isCheck, List<String> roleDeptList) {

        List<Map<String, Object>> trees = new ArrayList<>();
        deptList.stream().filter(sysDept -> UserConstants.DEPT_NORMAL.equals(sysDept.getStatus())).forEach(dept -> {
            Map<String, Object> deptMap = new HashMap<>();
            deptMap.put("id", dept.getDeptId());
            deptMap.put("pId", dept.getParentId());
            deptMap.put("name", dept.getDeptName());
            deptMap.put("title", dept.getDeptName());
            if (isCheck) {
                deptMap.put("checked", roleDeptList.contains(dept.getDeptId() + dept.getDeptName()));
            } else {
                deptMap.put("checked", false);
            }
            trees.add(deptMap);
        });
        return trees;
    }

    /**
     * 查询部门人数
     *
     * @param parentId 部门ID
     * @return 结果
     */
    @Override
    public int selectDeptCount(Long parentId) {
        SysDept dept = new SysDept();
        dept.setParentId(parentId);
        return deptMapper.selectDeptCount(dept);
    }

    /**
     * 查询部门是否存在用户
     *
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    @Override
    public boolean checkDeptExistUser(Long deptId) {
        int result = deptMapper.checkDeptExistUser(deptId);
        return result > 0;
    }

    /**
     * 删除部门管理信息
     *
     * @param deptId 部门ID
     * @return 结果
     */
    @Override
    public int deleteDeptById(Long deptId) {
        return deptMapper.deleteDeptById(deptId);
    }

    /**
     * 新增保存部门信息
     *
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public int insertDept(SysDept dept) {
        SysDept info = deptMapper.selectDeptById(dept.getParentId());
        //如果父节点不为"正常"状态,则不允许新增子节点
        if(!UserConstants.DEPT_NORMAL.equals(info.getStatus())){
            throw new BusinessException("上级部门不为正常状态,新增失败!");
        }
        dept.setAncestors(info.getAncestors() + "," + dept.getParentId());
        return deptMapper.insertDept(dept);
    }

    /**
     * 修改保存部门信息
     *
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public int updateDept(SysDept dept) {
        SysDept info = deptMapper.selectDeptById(dept.getParentId());
        if (ObjectUtils.allNotNull(info)) {
            String ancestors = info.getAncestors() + "," + info.getDeptId();
            dept.setAncestors(ancestors);
            updateDeptChildren(dept, ancestors);
        }
        int result = deptMapper.updateDept(dept);
        if(UserConstants.DEPT_NORMAL.equals(dept.getStatus())){
            //如果该部门是启用状态,这启用该部门的所有上级部门
            updateParentDeptStatus(dept);
        }
        return result;
    }

    /**
     * 修改该部门的父级部门状态
     * @param dept 当前部门
     */
    private void updateParentDeptStatus(SysDept dept) {
        String updateBy = dept.getUpdateBy();
        dept = deptMapper.selectDeptById(dept.getDeptId());
        dept.setUpdateBy(updateBy);
        deptMapper.updateDeptStatus(dept);
    }

    /**
     * 修改子元素关系
     *
     * @param sysDept   部门
     * @param ancestors 元素列表
     */
    private void updateDeptChildren(SysDept sysDept, String ancestors) {
        SysDept dept = new SysDept();
        dept.setParentId(sysDept.getDeptId());
        List<SysDept> childrens = deptMapper.selectDeptList(dept);
        if (!CollectionUtils.isEmpty(childrens)) {
            childrens.forEach(children -> {
                children.setAncestors(ancestors + "," + dept.getParentId());
                if (!UserConstants.DEPT_NORMAL.equals(sysDept.getStatus())) {
                    children.setStatus(sysDept.getStatus());
                }
            });
            deptMapper.updateDeptChildren(childrens);
            childrens.stream().filter(children -> !UserConstants.DEPT_NORMAL.equals(children.getStatus()))
                    .forEach(children ->
                            updateDeptChildren(children, children.getAncestors())
                    );
        }
    }

    /**
     * 根据部门ID查询信息
     *
     * @param deptId 部门ID
     * @return 部门信息
     */
    @Override
    public SysDept selectDeptById(Long deptId) {
        return deptMapper.selectDeptById(deptId);
    }

    /**
     * 校验部门名称是否唯一
     *
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public String checkDeptNameUnique(SysDept dept) {
        SysDept info = deptMapper.checkDeptNameUnique(dept.getDeptName(), dept.getParentId());
        if (ObjectUtils.allNotNull(info) && !info.getDeptId().equals(dept.getDeptId())) {
            return UserConstants.DEPT_NAME_NOT_UNIQUE;
        }
        return UserConstants.DEPT_NAME_UNIQUE;
    }
}
