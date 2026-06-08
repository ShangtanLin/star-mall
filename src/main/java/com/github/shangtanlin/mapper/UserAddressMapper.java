package com.github.shangtanlin.mapper;

import com.github.shangtanlin.model.dto.address.UserAddressUpdateDTO;
import com.github.shangtanlin.model.entity.user.UserAddress;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserAddressMapper {
    /**
     * 查询用户地址列表
     * @param userId
     * @return
     */
    @Select("select * from user_address where user_id = #{userId}")
    List<UserAddress> selectAddressList(Long userId);

    /**
     * 查询用户地址详情
     * @param userId
     * @param addressId
     */
    @Select("select * from user_address where user_id = #{userId} and id = #{addressId}")
    UserAddress selectAddressInfo(@Param("userId") Long userId, @Param("addressId") Long addressId);

    /**
     * 新增用户地址
     * @param userAddress
     */
    @Insert("insert into user_address (user_id, receiver_name, " +
            "receiver_phone, province, " +
            "city, district, detail_address) " +
            "values (#{userId},#{receiverName},#{receiverPhone}," +
            "#{province},#{city},#{district},#{detailAddress})")
    void insertAddress(UserAddress userAddress);

    /**
     * 删除用户地址
     * @param userId
     * @param addressId
     */
    @Delete("delete from user_address where id = #{addressId} and user_id = #{userId}")
    void delete(@Param("userId") Long userId, @Param("addressId") Long addressId);

    /**
     * 修改用户地址
     * @param userAddressUpdateDTO
     */
    void update(@Param("userAddressUpdateDTO") UserAddressUpdateDTO userAddressUpdateDTO, @Param("userId") Long userId);

    /**
     * 将所有地址设置为非默认
     * @param userId
     */
    @Update("update user_address set is_default = 0 where user_id = #{userId}")
    int resetDefaultAddress(Long userId);

    /**
     * 设置默认地址
     * @param userId,addressId
     */
    @Update("update user_address set is_default = 1 " +
            "where user_id = #{userId} " +
            "and id = #{addressId}")
    void setDefaultAddress(@Param("userId") Long userId,@Param("addressId") Long addressId);

    /**
     * 根据id查询地址
     * @param addressId
     * @return
     */
    @Select("select * from `taobao-mall`.user_address where id = #{addressId}")
    UserAddress selectAddressById(Long addressId);

    /**
     * 查询默认地址
     * @param userId
     * @return
     */
    @Select("select * from `taobao-mall`.user_address where user_id = #{userId} and is_default = 1")
    UserAddress selectDefaultAddress(Long userId);
}
