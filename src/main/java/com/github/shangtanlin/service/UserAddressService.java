package com.github.shangtanlin.service;

import com.github.shangtanlin.model.dto.address.UserAddressDTO;
import com.github.shangtanlin.model.dto.address.UserAddressUpdateDTO;
import com.github.shangtanlin.model.entity.user.UserAddress;
import com.github.shangtanlin.model.vo.UserAddressVO;
import com.github.shangtanlin.result.Result;
import jakarta.validation.Valid;

import java.util.List;

public interface UserAddressService {
    /**
     * 获取用户地址列表
     * @return
     */
    List<UserAddressVO> getUserAddressList();

    /**
     * 新增用户地址
     * @param userAddressDTO
     * @return
     */
    Result<?> addUserAddress(@Valid UserAddressDTO userAddressDTO);

    /**
     * 删除用户地址
     * @param addressId
     * @return
     */
    Result<?> deleteUserAddress(Long addressId);

    /**
     * 修改用户地址
     * @param userAddressUpdateDTO
     * @return
     */
    Result<?> updateUserAddress(UserAddressUpdateDTO userAddressUpdateDTO);

    /**
     * 查询用户地址详情
     * @param addressId
     * @return
     */
    UserAddress getUserAddressInfo(Long addressId);

    /**
     * 设置用户默认地址
     * @param addressId
     * @return
     */
    Result<?> setDefaultAddress(Long addressId);
}
