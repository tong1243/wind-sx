package com.wut.screenwebsx.Service.Impl;

import com.wut.screencommonsx.Model.UserAccount;
import com.wut.screencommonsx.Service.UserStatusProvider;
import com.wut.screenwebsx.Mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserStatusProviderImpl implements UserStatusProvider {
    private final UserAccountMapper userAccountMapper;

    @Override
    public boolean isActive(String phone) {
        UserAccount user = userAccountMapper.selectById(phone);
        return user != null && Integer.valueOf(1).equals(user.getStatus());
    }
}
