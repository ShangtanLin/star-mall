package com.github.shangtanlin.service.impl;

import com.github.shangtanlin.common.utils.UserHolder;
import com.github.shangtanlin.mapper.UserAddressMapper;
import com.github.shangtanlin.model.dto.address.UserAddressDTO;
import com.github.shangtanlin.model.dto.address.UserAddressUpdateDTO;
import com.github.shangtanlin.model.entity.user.UserAddress;
import com.github.shangtanlin.model.vo.UserAddressVO;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.UserAddressService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserAddressServiceImpl implements UserAddressService {
    @Autowired
    private UserAddressMapper userAddressMapper;

    /**
     * 获取用户地址列表
     * @return
     */
    @Override
    public List<UserAddressVO> getUserAddressList() {
        Long userId = UserHolder.getUser().getId();
        List<UserAddress> userAddresses = userAddressMapper.selectAddressList(userId);
        List<UserAddressVO> voList = userAddresses.stream().map(userAddress -> {
            UserAddressVO vo = new UserAddressVO();
            BeanUtils.copyProperties(userAddress, vo);
            String fullAddress = userAddress.getProvince() + userAddress.getCity()
                    + userAddress.getDistrict() + userAddress.getDetailAddress();
            vo.setFullAddress(fullAddress);
            return vo;
        }).collect(Collectors.toList());
        return voList;
    }


    /**
     * 新增用户地址
     * @return
     */
    @Override
    public Result<?> addUserAddress(UserAddressDTO userAddressDTO) {
        UserAddress userAddress = new UserAddress();
        BeanUtils.copyProperties(userAddressDTO, userAddress);
        userAddress.setUserId(UserHolder.getUser().getId());

        userAddressMapper.insertAddress(userAddress);
        return Result.ok();
    }

    /**
     * 删除用户地址
     * @return
     */
    @Override
    public Result<?> deleteUserAddress(Long addressId) {
        Long userId = UserHolder.getUser().getId();
        userAddressMapper.delete(userId,addressId);
        return Result.ok();
    }

    /**
     * 修改用户地址
     * @param userAddressUpdateDTO
     * @return
     */
    @Override
    public Result<?> updateUserAddress(UserAddressUpdateDTO userAddressUpdateDTO) {
        Long userId = UserHolder.getUser().getId();
        userAddressMapper.update(userAddressUpdateDTO,userId);
        return Result.ok();
    }

    /**
     * 查询用户地址详情
     * @param addressId
     * @return
     */
    @Override
    public UserAddress getUserAddressInfo(Long addressId) {
        Long userId = UserHolder.getUser().getId();
        UserAddress userAddress =  userAddressMapper.selectAddressInfo(userId,addressId);
        return userAddress;
    }


    /**
     * 设置用户默认地址
     * @param addressId
     * @return
     */
    @Override
    @Transactional
    public Result<?> setDefaultAddress(Long addressId) {
        Long userId = UserHolder.getUser().getId();
        //1.将其他地址设置为非默认
        int rows = userAddressMapper.resetDefaultAddress(userId);
        if (rows == 0) {
            return Result.fail("地址不存在或无权操作");
        }
        //2.将传入地址设置为默认(这两部操作必须同时成功)
        userAddressMapper.setDefaultAddress(userId,addressId);
        return Result.ok();
    }


}
