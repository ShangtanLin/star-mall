package com.github.shangtanlin.controller;

import com.github.shangtanlin.model.dto.address.UserAddressDTO;
import com.github.shangtanlin.model.dto.address.UserAddressUpdateDTO;
import com.github.shangtanlin.model.entity.user.UserAddress;
import com.github.shangtanlin.model.vo.UserAddressVO;
import com.github.shangtanlin.result.Result;
import com.github.shangtanlin.service.UserAddressService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserAddressController {

    @Autowired
    private UserAddressService userAddressService;

    /**
     * 查询用户地址列表
     * @return
     */
    @GetMapping("/getUserAddressList")
    public Result<?> getUserAddressList() {
        List<UserAddressVO> userAddressList = userAddressService.getUserAddressList();
        return Result.ok(userAddressList);
    }

    /**
     * 查询用户地址详情
     * @return
     */
    @GetMapping("/getUserAddressInfo/{addressId}")
    public Result<?> getUserAddressInfo(@PathVariable("addressId") Long addressId) {
        UserAddress userAddress =  userAddressService.getUserAddressInfo(addressId);
        return Result.ok(userAddress);
    }

    /**
     * 新增用户地址
     * @return
     */
    @PostMapping("/addUserAddress")
    public Result<?> addUserAddress(@Valid @RequestBody UserAddressDTO userAddressDTO) {
        return userAddressService.addUserAddress(userAddressDTO);
    }


    /**
     * 删除用户地址
     * @return
     */
    @DeleteMapping("/deleteUserAddress/{addressId}")
    public Result<?> deleteUserAddress(@PathVariable("addressId") Long addressId) {
        return userAddressService.deleteUserAddress(addressId);
    }

    /**
     * 修改用户地址
     * @return
     */
    @PutMapping("/updateUserAddress")
    public Result<?> updateUserAddress(@RequestBody UserAddressUpdateDTO userAddressUpdateDTO) {
        return userAddressService.updateUserAddress(userAddressUpdateDTO);
    }

    /**
     * 设置默认地址
     * @return
     */
    @PutMapping("/setDefaultAddress/{addressId}")
    public Result<?> updateUserAddress(@PathVariable("addressId") Long addressId) {
        return userAddressService.setDefaultAddress(addressId);
    }



}
