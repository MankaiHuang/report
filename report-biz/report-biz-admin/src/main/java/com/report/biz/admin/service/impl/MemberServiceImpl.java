package com.report.biz.admin.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SQLQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.report.biz.admin.service.MemberService;
import com.report.common.dal.admin.constant.Constants;
import com.report.common.dal.admin.entity.dto.Member;
import com.report.common.dal.admin.entity.dto.MemberGroup;
import com.report.common.dal.admin.entity.vo.MemberCriteriaModel;
import com.report.common.dal.admin.util.SessionUtil;
import com.report.common.dal.common.BaseDao;
import com.report.common.model.UserModel;
import com.report.common.repository.GroupRepository;
import com.report.common.repository.MemberRepository;
import com.report.common.repository.ResourceRepository;
import com.report.common.repository.RoleRepository;
import com.report.common.util.MD5;
import com.report.facade.entity.DataGrid;
import com.report.facade.entity.PageHelper;

@Service("memberService")
@Transactional
public class MemberServiceImpl implements MemberService {

    @Resource
    private BaseDao baseDao;
    
    @Resource
    private MemberRepository memberRepository;
    
    @Resource
    private GroupRepository groupRepository;
    
    @Resource
    private RoleRepository roleRepository;
    
    @Resource
    private ResourceRepository resourceRepository;

    @Override
	public Set<String> findRoles(String accNo) {
    	return roleRepository.findRoles(accNo);
	}

	@Override
	public Set<String> findPermissions(String accNo) {
		return resourceRepository.findPermissions(accNo);
	}
	
    @Override
	public UserModel findUserModelByAccNo(String username) {
		Member member = memberRepository.findMemberByAccNo(username);
		if (null != member) {
			UserModel userModel = new UserModel();
			userModel.setPassword(member.getPassword());
			userModel.setAccNo(member.getAccNo());
			userModel.setUsername(member.getName());
			return userModel;
		}
		return null;
	}
    
    @Override
	public Member getMemberByLoginName(String loginName) {
	    Map<String, Object> params = new HashMap<String, Object>();
	    params.put("loginName", loginName);
        Object result = baseDao.get("from Member t where t.accNo=:loginName and t.status=1", params);
	    return result == null ? null:(Member)result;
	}

    @Override
    public boolean updateMember(Member member, String groupCode, String currentMemberIp, Long currentMemberId) {
        Member target = (Member) baseDao.get(Member.class, member.getId());
        Date now = new Date();
        target.setUpdateTime(now);
        target.setAccNo(member.getAccNo());
        target.setName(member.getName());
        target.setStatus(member.getStatus());

        // 判断该会员有没有关联groupCode，如果没有的话，就insert；如果有的话，就update
        if (groupRepository.isAssociatedWithGroup(member.getId())) {
            // 有关联
            groupRepository.updateGroupCodeByMemberId(member.getId(), groupCode, currentMemberIp);
        } else {
            // 没有关联
            groupRepository.associatedWithGroup(member.getId(), groupCode, currentMemberIp);
        }
        baseDao.update(target);
        return true;
    }

    @Override
    public void saveMember(Member member, String groupCode, String currentMemberIp, Long currentMemberId) {
        Date now = new Date();
        member.setName(member.getName().trim());
        member.setAccNo(member.getAccNo().trim());
        member.setCreateTime(now);
        member.setUpdateTime(now);
       // member.setPassword(MD5.getMD5String(member.getPassword().trim()));

        Long memberId = (Long) baseDao.save(member);

        if (StringUtils.isNotBlank(groupCode)) {
        	MemberGroup memberGroup = new MemberGroup();
            memberGroup.setCreateTime(now);
            memberGroup.setUpdateTime(now);
            memberGroup.setGroupCode(groupCode);
            memberGroup.setMemberId(memberId);
            memberGroup.setStatus(member.getStatus());
            baseDao.save(memberGroup);
        }
    }

    @Override
    public boolean deleteMemberById(Long id, String currentMemberIp) {
        boolean flag = false;
        Object obj = baseDao.get(Member.class, id);
        if (obj != null) {
            baseDao.delete(obj);

            // 删除人员和组别的关联关系
            String sql = "delete from uc_member_group mg where mg.member_id = :memberId";
            SQLQuery query = baseDao.getSqlQuery(sql);
            query.setLong("memberId", id);
            flag = query.executeUpdate() > 0;
        }
        return flag;
    }

    @Override
    public boolean isPasswordRight(Long currentMemberId, String password) {
        return memberRepository.isPasswordRight(currentMemberId, (MD5.getMD5String(password)));
    }

    @Override
    public boolean resetPassword(Long memberId, String memberIp) {
        boolean flag = memberRepository.resetPassword(memberId, MD5.getMD5String(Constants.DEFAULT_PASSWORD_FOR_MEMBER));
        return flag;
    }

    @Override
    public boolean isAccNoExists(String accNo) {
        return memberRepository.isAccNoExists(accNo);
    }

    @Override
    public boolean changePassword(String password, Long currentMemberId, String memberIp) {
        boolean flag = memberRepository.changePassword(MD5.getMD5String(password), currentMemberId);

        return flag;
    }

    @Override
    public DataGrid findMemberListByCriteria(MemberCriteriaModel memberCriteria, PageHelper pageHelper) {
        
        DataGrid dataGrid = new DataGrid();
        if(SessionUtil.isPerAdmin()) {
            memberCriteria.setMemberId(null);
        }
        
        dataGrid.setTotal(memberRepository.countByCriteria(memberCriteria));
        dataGrid.setRows(memberRepository.findMemberListByCriteria(memberCriteria, pageHelper));
        return dataGrid;
    }
}
